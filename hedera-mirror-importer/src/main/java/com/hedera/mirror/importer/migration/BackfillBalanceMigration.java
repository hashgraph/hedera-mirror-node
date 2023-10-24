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
import jakarta.annotation.Nonnull;
import jakarta.inject.Named;
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
public class BackfillBalanceMigration extends AsyncJavaMigration<Long> {

    private static final String COPY_ACCOUNT_BALANCE_SQL =
            """
            insert into account_balance
            select *
            from account_balance_old
            where consensus_timestamp = ?
            """;

    private static final String COPY_TOKEN_BALANCE_SQL =
            """
            insert into token_balance
            select *
            from token_balance_old
            where consensus_timestamp = ?
            """;

    private static final String DROP_OLD_TABLES_SQL =
            """
            drop table account_balance_old;
            drop table token_balance_old;
            """;

    private static final long NO_BALANCE = 0;

    private static final long ONE_DAY_IN_NS = 24 * 3600 * 1_000_000_000L;

    private static final String SELECT_EARLIEST_BALANCE_TIMESTAMP_SQL =
            """
            select consensus_timestamp from account_balance order by consensus_timestamp limit 1
            """;

    private static final String SELECT_CURRENT_BALANCE_TIMESTAMP_SQL =
            """
            select consensus_timestamp
            from account_balance_file
            where consensus_timestamp >= :lowerBound and consensus_timestamp < :upperBound
            order by consensus_timestamp
            limit 1
            """;

    private final JdbcTemplate jdbcTemplate;

    @Getter(lazy = true)
    private final TransactionOperations transactionOperations = transactionOperations();

    @Lazy
    public BackfillBalanceMigration(
            DBProperties dbProperties, MirrorProperties mirrorProperties, @Owner JdbcTemplate jdbcTemplate) {
        super(mirrorProperties.getMigration(), new NamedParameterJdbcTemplate(jdbcTemplate), dbProperties.getSchema());
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String getDescription() {
        return "Backfill old balance information to keep one balance snapshot per day";
    }

    @Override
    protected Long getInitial() {
        Long timestamp = queryForObjectOrNull(
                SELECT_EARLIEST_BALANCE_TIMESTAMP_SQL, EmptySqlParameterSource.INSTANCE, Long.class);
        return timestamp != null ? timestamp : NO_BALANCE;
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
            jdbcTemplate.execute(DROP_OLD_TABLES_SQL);
            return Optional.empty();
        }

        int accountBalanceCount = jdbcTemplate.update(COPY_ACCOUNT_BALANCE_SQL, timestamp);
        int tokenBalanceCount = jdbcTemplate.update(COPY_TOKEN_BALANCE_SQL, timestamp);
        log.info(
                "Copied {} account balances and {} token balances at timestamp {} in {}",
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

        long lowerBound = last - ONE_DAY_IN_NS;
        var params =
                new MapSqlParameterSource().addValue("lowerBound", lowerBound).addValue("upperBound", last);
        return queryForObjectOrNull(SELECT_CURRENT_BALANCE_TIMESTAMP_SQL, params, Long.class);
    }

    private TransactionOperations transactionOperations() {
        var transactionManager = new DataSourceTransactionManager(Objects.requireNonNull(jdbcTemplate.getDataSource()));
        return new TransactionTemplate(transactionManager);
    }
}
