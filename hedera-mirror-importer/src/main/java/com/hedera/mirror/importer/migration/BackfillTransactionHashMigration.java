/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.common.domain.transaction.TransactionType.ETHEREUMTRANSACTION;
import static java.util.stream.Collectors.joining;

import com.google.common.base.Stopwatch;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.ImporterProperties;
import com.hedera.mirror.importer.config.Owner;
import com.hedera.mirror.importer.db.TimePartitionService;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import jakarta.inject.Named;
import java.io.IOException;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Named
public class BackfillTransactionHashMigration extends RepeatableMigration {

    private static final String BACKFILL_TRANSACTION_HASH_SQL =
            """
            insert into %s (consensus_timestamp, distribution_id, hash, payer_account_id)
            select
              consensus_timestamp,
              ('x' || encode(substring(transaction_hash from 1 for 2), 'hex'))::bit(32)::int >> 16,
              transaction_hash,
              payer_account_id
            from %%s
            where consensus_timestamp >= :startTimestamp and consensus_timestamp < :endTimestamp %s;
            """;
    private static final String BACKFILL_ETHERUM_TRANSACTION_HASH_SQL =
            """
            insert into %s (consensus_timestamp, distribution_id, hash, payer_account_id)
            select
              consensus_timestamp,
              ('x' || encode(substring(hash from 1 for 2), 'hex'))::bit(32)::int >> 16,
              hash,
              payer_account_id
            from %%s
            where length(hash) > 0 and consensus_timestamp >= :startTimestamp and consensus_timestamp < :endTimestamp;
            """;
    private static final String CREATE_TEMP_TABLE_SQL =
            """
            create temp table transaction_hash_backfill_temp on commit drop as table transaction_hash limit 0;
            """;
    private static final String END_TIMESTAMP_KEY = "endTimestamp";
    private static final String ETHEREUM_TRANSACTION_TABLE_NAME = "ethereum_transaction";
    private static final String GET_END_CONSENSUS_TIMESTAMP_SQL =
            """
            select coalesce(max(consensus_timestamp) + 1, 0) from transaction;
            """;
    private static final String INSERT_INTO_FINAL_TABLE_SQL =
            """
            insert into transaction_hash
            select * from transaction_hash_backfill_temp;
            """;
    private static final String START_TIMESTAMP_KEY = "startTimestamp";
    private static final String STRATEGY_KEY = "strategy";
    private static final String TABLE_HAS_DATA_SQL = "select exists(select * from %s limit 1)";
    private static final String TEMP_TABLE_NAME = "transaction_hash_backfill_temp";
    private static final String TRANSACTION_HASH_TABLE_NAME = "transaction_hash";
    private static final String TRANSACTION_TABLE_NAME = "transaction";
    private static final String TRUNCATE_SQL = "truncate table transaction_hash;";
    private static final String TRUNCATE_TEMP_TABLE_SQL = "truncate table transaction_hash_backfill_temp;";

    private final EntityProperties entityProperties;
    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final TimePartitionService timePartitionService;
    private final boolean v2;

    @Lazy
    public BackfillTransactionHashMigration(
            EntityProperties entityProperties,
            Environment environment,
            @Owner JdbcTemplate jdbcTemplate,
            ImporterProperties importerProperties,
            TimePartitionService timePartitionService) {
        super(importerProperties.getMigration());
        this.entityProperties = entityProperties;
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
        this.timePartitionService = timePartitionService;
        this.v2 = environment.acceptsProfiles(Profiles.of("v2"));
    }

    @Override
    protected void doMigrate() throws IOException {
        var persist = entityProperties.getPersist();
        if (!persist.isTransactionHash()) {
            log.info("Skipping migration since transaction hash persistence is disabled");
            return;
        }

        long startTimestamp = Long.parseLong(migrationProperties.getParams().getOrDefault(START_TIMESTAMP_KEY, "-1"));
        if (startTimestamp == -1) {
            log.info("Skipping migration since startTimestamp is not set");
            return;
        }

        var stopwatch = Stopwatch.createStarted();
        var context = getMigrationContext(startTimestamp);
        if (context.shouldSkip()) {
            log.info("Skipping migration based on the configured strategy and the existing data in the table");
            return;
        }

        var transactionTemplate = getTransactionTemplate();
        var count = transactionTemplate.execute(s -> {
            if (context.truncate) {
                jdbcTemplate.execute(TRUNCATE_SQL);
            }

            if (context.tempTable) {
                jdbcTemplate.execute(CREATE_TEMP_TABLE_SQL);
            }

            return backfillFromTable(
                            context, context.backfillEthereumTransactionHashSql, ETHEREUM_TRANSACTION_TABLE_NAME)
                    + backfillFromTable(context, context.backfillTransactionHashSql, TRANSACTION_TABLE_NAME);
        });

        log.info(
                "Backfilled transaction hash for {} transactions at or after {} in {}",
                count,
                startTimestamp,
                stopwatch);
    }

    @Override
    public String getDescription() {
        return "Backfill transaction hash to consensus timestamp mapping";
    }

    private int backfillFromTable(MigrationContext context, String sqlTemplate, String tableName) {
        if (StringUtils.isEmpty(sqlTemplate)) {
            return 0;
        }

        var params = new MapSqlParameterSource()
                .addValue(END_TIMESTAMP_KEY, context.endTimestamp)
                .addValue(START_TIMESTAMP_KEY, context.startTimestamp);
        var partitions = timePartitionService.getTimePartitions(tableName);

        if (partitions.isEmpty()) {
            return backfillOneTable(params, sqlTemplate, tableName, context.tempTable);
        }

        int count = 0;
        partitions = partitions.stream()
                .filter(p -> {
                    long fromInclusive = p.getTimestampRange().lowerEndpoint();
                    long toExclusive = p.getTimestampRange().upperEndpoint();
                    return context.startTimestamp < toExclusive && context.endTimestamp > fromInclusive;
                })
                .toList()
                .reversed();
        for (var partition : partitions) {
            count += backfillOneTable(params, sqlTemplate, partition.getName(), context.tempTable);
        }

        return count;
    }

    private int backfillOneTable(SqlParameterSource params, String sqlTemplate, String tableName, boolean tempTable) {
        if (!tableHasData(tableName)) {
            return 0;
        }

        var stopwatch = Stopwatch.createStarted();

        if (tempTable) {
            jdbcTemplate.execute(TRUNCATE_TEMP_TABLE_SQL);
        }

        String sql = String.format(sqlTemplate, tableName);
        int count = namedParameterJdbcTemplate.update(sql, params);

        if (tempTable) {
            jdbcTemplate.update(INSERT_INTO_FINAL_TABLE_SQL);
        }

        log.info("Backfilled transaction hash for {} transactions of {} in {}", count, tableName, stopwatch);
        return count;
    }

    private MigrationContext getMigrationContext(long startTimestamp) {
        String destinationTableName = v2 ? TEMP_TABLE_NAME : TRANSACTION_HASH_TABLE_NAME;
        long endTimestamp =
                Objects.requireNonNull(jdbcTemplate.queryForObject(GET_END_CONSENSUS_TIMESTAMP_SQL, Long.class));
        var transactionHashTypes = entityProperties.getPersist().getTransactionHashTypes();
        boolean ethereumTransactionIncluded = transactionHashTypes.contains(ETHEREUMTRANSACTION);
        String transactionTypesCondition = transactionHashTypes.isEmpty()
                ? StringUtils.EMPTY
                : String.format(
                        "and type in (%s)",
                        transactionHashTypes.stream()
                                .map(TransactionType::getProtoId)
                                .map(Object::toString)
                                .collect(joining(",")));
        String backfillEthereumTransactionHashSql = ethereumTransactionIncluded
                ? String.format(BACKFILL_ETHERUM_TRANSACTION_HASH_SQL, destinationTableName)
                : StringUtils.EMPTY;
        String backfillTransactionHashSql =
                String.format(BACKFILL_TRANSACTION_HASH_SQL, destinationTableName, transactionTypesCondition);
        var backfillBoth = new MigrationContext(
                backfillEthereumTransactionHashSql, backfillTransactionHashSql, endTimestamp, startTimestamp, v2, true);
        var backfillEthereumTransactionHash = new MigrationContext(
                backfillEthereumTransactionHashSql, StringUtils.EMPTY, endTimestamp, startTimestamp, v2, false);
        var backfillTransactionHash = new MigrationContext(
                StringUtils.EMPTY, backfillTransactionHashSql, endTimestamp, startTimestamp, v2, true);

        var strategy = getStrategy();
        return switch (strategy) {
            case AUTO -> tableHasData(TRANSACTION_HASH_TABLE_NAME) ? backfillEthereumTransactionHash : backfillBoth;
            case BOTH -> backfillBoth;
            case ETHEREUM_HASH -> backfillEthereumTransactionHash;
            case TRANSACTION_HASH -> backfillTransactionHash;
        };
    }

    private Strategy getStrategy() {
        String name = migrationProperties
                .getParams()
                .getOrDefault(STRATEGY_KEY, Strategy.AUTO.name())
                .toUpperCase();
        return Strategy.valueOf(name);
    }

    private TransactionTemplate getTransactionTemplate() {
        var transactionManager = new DataSourceTransactionManager(Objects.requireNonNull(jdbcTemplate.getDataSource()));
        return new TransactionTemplate(transactionManager);
    }

    private boolean tableHasData(String tableName) {
        String sql = String.format(TABLE_HAS_DATA_SQL, tableName);
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(sql, Boolean.class));
    }

    private record MigrationContext(
            String backfillEthereumTransactionHashSql,
            String backfillTransactionHashSql,
            long endTimestamp,
            long startTimestamp,
            boolean tempTable,
            boolean truncate) {

        public boolean shouldSkip() {
            return StringUtils.isEmpty(backfillEthereumTransactionHashSql)
                    && StringUtils.isEmpty(backfillTransactionHashSql);
        }
    }

    private enum Strategy {
        AUTO,
        BOTH,
        ETHEREUM_HASH,
        TRANSACTION_HASH
    }
}
