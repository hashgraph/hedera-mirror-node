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
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import jakarta.inject.Named;
import java.io.IOException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@Named
public class BackfillTransactionHashMigration extends RepeatableMigration {

    private static final String BACKFILL_TRANSACTION_HASH_SQL =
            """
            insert into transaction_hash (consensus_timestamp, hash, payer_account_id)
            select consensus_timestamp, transaction_hash, payer_account_id
            from transaction
            where consensus_timestamp >= :startTimestamp %s;
            """;
    private static final String BACKFILL_ETHERUM_TRANSACTION_HASH_SQL =
            """
            insert into transaction_hash (consensus_timestamp, hash, payer_account_id)
            select consensus_timestamp, hash, payer_account_id
            from ethereum_transaction
            where consensus_timestamp >= :startTimestamp;
            """;
    private static final String START_TIMESTAMP_KEY = "startTimestamp";
    private static final String STRATEGY_KEY = "strategy";
    private static final String TABLE_HAS_DATA_SQL = "select exists(select * from transaction_hash limit 1)";
    private static final String TRUNCATE_SQL = "truncate table transaction_hash;";
    private static final String TRUNCATE_AND_BACKFILL_BOTH_SQL =
            wrap(TRUNCATE_SQL, BACKFILL_TRANSACTION_HASH_SQL, BACKFILL_ETHERUM_TRANSACTION_HASH_SQL);
    private static final String TRUNCATE_AND_BACKFILL_TRANSACTION_HASH_SQL =
            wrap(TRUNCATE_SQL, BACKFILL_TRANSACTION_HASH_SQL);

    private final EntityProperties entityProperties;

    private final JdbcTemplate jdbcTemplate;

    @Lazy
    public BackfillTransactionHashMigration(
            EntityProperties entityProperties,
            @Owner JdbcTemplate jdbcTemplate,
            ImporterProperties importerProperties) {
        super(importerProperties.getMigration());
        this.entityProperties = entityProperties;
        this.jdbcTemplate = jdbcTemplate;
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
        String sql = getMigrationSql();
        if (StringUtils.isEmpty(sql)) {
            log.info("Skipping migration based on the configured strategy and the existing data in the table");
            return;
        }

        var namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
        var params = new MapSqlParameterSource(START_TIMESTAMP_KEY, startTimestamp);
        namedParameterJdbcTemplate.update(sql, params);

        log.info("Backfilled transaction hash for transactions at or after {} in {}", startTimestamp, stopwatch);
    }

    @Override
    public String getDescription() {
        return "Backfill transaction hash to consensus timestamp mapping";
    }

    private static String wrap(String... queries) {
        return String.format(
                """
                begin;
                %s
                commit;
                """,
                StringUtils.join(queries, "\n"));
    }

    private String getMigrationSql() {
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
        String backfillEthereumTransactionHashSql =
                ethereumTransactionIncluded ? BACKFILL_ETHERUM_TRANSACTION_HASH_SQL : StringUtils.EMPTY;
        String backfillTransactionHashSql =
                String.format(TRUNCATE_AND_BACKFILL_TRANSACTION_HASH_SQL, transactionTypesCondition);
        String backfillBothSql = ethereumTransactionIncluded
                ? String.format(TRUNCATE_AND_BACKFILL_BOTH_SQL, transactionTypesCondition)
                : backfillTransactionHashSql;

        var strategy = getStrategy();
        return switch (strategy) {
            case AUTO -> tableHasData() ? backfillEthereumTransactionHashSql : backfillBothSql;
            case BOTH -> backfillBothSql;
            case ETHEREUM_HASH -> backfillEthereumTransactionHashSql;
            case TRANSACTION_HASH -> backfillTransactionHashSql;
        };
    }

    private Strategy getStrategy() {
        String name = migrationProperties
                .getParams()
                .getOrDefault(STRATEGY_KEY, Strategy.AUTO.name())
                .toUpperCase();
        return Strategy.valueOf(name);
    }

    private boolean tableHasData() {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(TABLE_HAS_DATA_SQL, Boolean.class));
    }

    private enum Strategy {
        AUTO,
        BOTH,
        ETHEREUM_HASH,
        TRANSACTION_HASH
    }
}
