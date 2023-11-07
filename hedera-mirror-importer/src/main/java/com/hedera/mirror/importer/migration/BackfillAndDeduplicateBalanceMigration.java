/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.mirror.importer.migration;

import com.google.common.base.Stopwatch;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.config.Owner;
import com.hedera.mirror.importer.db.DBProperties;
import com.hedera.mirror.importer.db.TimePartitionService;
import com.hedera.mirror.importer.exception.InvalidDatasetException;
import jakarta.annotation.Nonnull;
import jakarta.inject.Named;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import lombok.CustomLog;
import lombok.Getter;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.EmptySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

@CustomLog
@Named
@Profile("!v2")
public class BackfillAndDeduplicateBalanceMigration extends AsyncJavaMigration<Long> {

    private static final String ACCOUNT_BALANCE_TABLE_NAME = "account_balance";
    private static final long EPOCH = 0L;

    // Can't use spring data repositories because the migration runs with a different role, so it can drop the tables
    // at the end
    // To simplify the test case, the create_(deduped|full)_*_balance_snapshot functions are not dropped at the end
    private static final String CLEANUP_SQL =
            """
            drop table account_balance_old;
            drop table token_balance_old;
            """;

    private static final String CREATE_FULL_ACCOUNT_BALANCE_SNAPSHOT_SQL =
            "select create_full_account_balance_snapshot(:balanceTimestamp, :prevBalanceTimestamp)";

    private static final String CREATE_DEDUPED_ACCOUNT_BALANCE_SNAPSHOT_SQL =
            "select create_deduped_account_balance_snapshot(:balanceTimestamp, :prevBalanceTimestamp)";

    private static final String CREATE_FULL_TOKEN_BALANCE_SNAPSHOT_SQL =
            "select create_full_token_balance_snapshot(:balanceTimestamp, :prevBalanceTimestamp)";

    private static final String CREATE_DEDUPED_TOKEN_BALANCE_SNAPSHOT_SQL =
            "select create_deduped_token_balance_snapshot(:balanceTimestamp, :prevBalanceTimestamp)";

    private static final String IS_ACCOUNT_BALANCE_PARTITION_EMPTY_SQL =
            """
            select not exists(
              select * from account_balance
              where consensus_timestamp >= :lowerBound and consensus_timestamp < :upperBound
            )
            """;

    private static final long NO_BALANCE = -1;

    private static final long ONE_HOUR_IN_NS = Duration.ofHours(1).toNanos();

    private static final String PATCH_ORIGINAL_FIRST_ACCOUNT_BALANCE_SNAPSHOT_SQL =
            """
            with previous as (
              select *
              from account_balance_old
              where consensus_timestamp = :prevBalanceTimestamp
            ), current as (
              select *
              from account_balance_old
              where consensus_timestamp = :balanceTimestamp
            )
            insert into account_balance (account_id, balance, consensus_timestamp)
            select p.account_id, 0, :balanceTimestamp
            from previous as p
            left join current as c using (account_id)
            where c.account_id is null
            """;

    private static final String PATCH_ORIGINAL_FIRST_TOKEN_BALANCE_SNAPSHOT_SQL =
            """
            with previous as (
              select *
              from token_balance_old
              where consensus_timestamp = :prevBalanceTimestamp
            ), current as (
              select *
              from token_balance_old
              where consensus_timestamp = :balanceTimestamp
            )
            insert into token_balance (account_id, balance, consensus_timestamp, token_id)
            select p.account_id, 0, :balanceTimestamp, p.token_id
            from previous as p
            left join current as c using (account_id, token_id)
            where c.account_id is null
            """;

    private static final String SELECT_NEXT_CONSENSUS_TIMESTAMP_SQL =
            """
            select consensus_timestamp
            from account_balance_file
            where consensus_timestamp >= :lowerBound and consensus_timestamp < :upperBound
            order by consensus_timestamp
            limit 1
            """;

    private static final String SELECT_INITIAL_CONSENSUS_TIMESTAMP_SQL =
            """
            select coalesce(max(consensus_timestamp), 0)
            from account_balance
            where account_id = 2 and consensus_timestamp < ?
            """;

    private static final String SELECT_LAST_CONSENSUS_TIMESTAMP_SQL =
            "select balance from account_balance_old where consensus_timestamp = -1 and account_id = -1";

    private final JdbcTemplate jdbcTemplate;

    private final TimePartitionService timePartitionService;

    @Getter(lazy = true)
    private final TransactionOperations transactionOperations = transactionOperations();

    private Long lastConsensusTimestamp;

    @Lazy
    public BackfillAndDeduplicateBalanceMigration(
            DBProperties dbProperties,
            MirrorProperties mirrorProperties,
            @Owner JdbcTemplate jdbcTemplate,
            TimePartitionService timePartitionService) {
        super(mirrorProperties.getMigration(), new NamedParameterJdbcTemplate(jdbcTemplate), dbProperties.getSchema());
        this.jdbcTemplate = jdbcTemplate;
        this.timePartitionService = timePartitionService;
    }

    @Override
    public String getDescription() {
        return "Backfill and deduplicate old balance information";
    }

    @Override
    protected Long getInitial() {
        lastConsensusTimestamp =
                queryForObjectOrNull(SELECT_LAST_CONSENSUS_TIMESTAMP_SQL, EmptySqlParameterSource.INSTANCE, Long.class);
        if (lastConsensusTimestamp == null) {
            return NO_BALANCE;
        }

        return jdbcTemplate.queryForObject(SELECT_INITIAL_CONSENSUS_TIMESTAMP_SQL, Long.class, lastConsensusTimestamp);
    }

    @Override
    protected MigrationVersion getMinimumVersion() {
        return MigrationVersion.fromVersion("1.89.0"); // The version balance table partitions are created
    }

    @Nonnull
    @Override
    protected Optional<Long> migratePartial(Long last) {
        var stopwatch = Stopwatch.createStarted();
        Long timestamp = getBalanceTimestamp(last);
        if (timestamp == null) {
            patchSnapshotAtLastConsensusTimestamp(last);
            jdbcTemplate.execute(CLEANUP_SQL);
            return Optional.empty();
        }

        var partitions =
                timePartitionService.getOverlappingTimePartitions(ACCOUNT_BALANCE_TABLE_NAME, timestamp, timestamp);
        if (partitions.isEmpty()) {
            throw new InvalidDatasetException(
                    String.format("No account_balance table partition found for timestamp %d", timestamp));
        }

        var partitionRange = partitions.get(0).getTimestampRange();
        var params = new MapSqlParameterSource()
                .addValue("lowerBound", partitionRange.lowerEndpoint())
                .addValue("upperBound", Math.min(partitionRange.upperEndpoint(), lastConsensusTimestamp));
        var isPartitionEmpty = namedParameterJdbcTemplate.queryForObject(
                IS_ACCOUNT_BALANCE_PARTITION_EMPTY_SQL, params, Boolean.class);
        var createAccountBalanceSnapshotSql = Boolean.TRUE.equals(isPartitionEmpty)
                ? CREATE_FULL_ACCOUNT_BALANCE_SNAPSHOT_SQL
                : CREATE_DEDUPED_ACCOUNT_BALANCE_SNAPSHOT_SQL;
        var createTokenBalanceSnapshotSql = Boolean.TRUE.equals(isPartitionEmpty)
                ? CREATE_FULL_TOKEN_BALANCE_SNAPSHOT_SQL
                : CREATE_DEDUPED_TOKEN_BALANCE_SNAPSHOT_SQL;

        params = new MapSqlParameterSource()
                .addValue("balanceTimestamp", timestamp)
                .addValue("prevBalanceTimestamp", last);
        var accountBalanceCount =
                namedParameterJdbcTemplate.queryForObject(createAccountBalanceSnapshotSql, params, Integer.class);
        var tokenBalanceCount =
                namedParameterJdbcTemplate.queryForObject(createTokenBalanceSnapshotSql, params, Integer.class);

        log.info(
                "Created a new {} snapshot with {} account balances and {} token balances at timestamp {} in {}",
                Boolean.TRUE.equals(isPartitionEmpty) ? "full" : "deduped",
                accountBalanceCount,
                tokenBalanceCount,
                timestamp,
                stopwatch);

        return Optional.of(timestamp);
    }

    private Long getBalanceTimestamp(Long last) {
        if (last == NO_BALANCE) {
            return null;
        }

        long lowerBound = last + 4 * ONE_HOUR_IN_NS;
        var params = new MapSqlParameterSource()
                .addValue("lowerBound", lowerBound)
                .addValue("upperBound", lastConsensusTimestamp);
        return queryForObjectOrNull(SELECT_NEXT_CONSENSUS_TIMESTAMP_SQL, params, Long.class);
    }

    private void patchSnapshotAtLastConsensusTimestamp(Long lastProcessedTimestamp) {
        if (lastConsensusTimestamp == null || lastProcessedTimestamp == EPOCH) {
            return;
        }

        Stopwatch stopwatch = Stopwatch.createStarted();
        var params = new MapSqlParameterSource()
                .addValue("balanceTimestamp", lastConsensusTimestamp)
                .addValue("prevBalanceTimestamp", lastProcessedTimestamp);
        int accountBalanceCount =
                namedParameterJdbcTemplate.update(PATCH_ORIGINAL_FIRST_ACCOUNT_BALANCE_SNAPSHOT_SQL, params);
        int tokenBalanceCount =
                namedParameterJdbcTemplate.update(PATCH_ORIGINAL_FIRST_TOKEN_BALANCE_SNAPSHOT_SQL, params);
        log.info(
                "Patched the original first balance snapshot with {} account balances and {} token balances in {}",
                accountBalanceCount,
                tokenBalanceCount,
                stopwatch);
    }

    private TransactionOperations transactionOperations() {
        var transactionManager = new DataSourceTransactionManager(Objects.requireNonNull(jdbcTemplate.getDataSource()));
        return new TransactionTemplate(transactionManager);
    }
}
