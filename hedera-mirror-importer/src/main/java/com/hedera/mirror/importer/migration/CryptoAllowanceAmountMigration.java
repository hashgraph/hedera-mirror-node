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

import com.hedera.mirror.importer.config.Owner;
import com.hedera.mirror.importer.db.DBProperties;
import jakarta.annotation.Nonnull;
import jakarta.inject.Named;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.CustomLog;
import lombok.Getter;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

@CustomLog
@Named
public class CryptoAllowanceAmountMigration extends AsyncJavaMigration<Long> {

    private static final String DROP_MIGRATION_TABLE_SQL = "drop table if exists crypto_allowance_migration";

    private static final String GET_END_TIMESTAMP_SQL =
            """
            select created_timestamp from crypto_allowance_migration where owner = 0 and spender = 0;
            """;

    // Process at most 30 days worth approved crypto transfer in each iteration
    private static final long INTERVAL = Duration.ofDays(30).toNanos();

    private static final String MERGE_AMOUNT_SQL =
            """
            lock table crypto_allowance in access exclusive mode;
            lock table crypto_allowance_history in access exclusive mode;

            update crypto_allowance ca
            set amount = greatest(ca.amount + m.amount, 0)
            from crypto_allowance_migration m
            where m.owner = ca.owner and m.spender = ca.spender and m.created_timestamp = ca.created_timestamp;

            update crypto_allowance_history h
            set amount = greatest(h.amount + m.amount, 0)
            from crypto_allowance_migration m
            where m.owner = h.owner and m.spender = h.spender and m.created_timestamp = h.created_timestamp;
            """;

    private static final String UPDATE_MIGRATION_AMOUNT_SQL =
            """
            with aggregated_approved_debit as (
              select sum(amount) as amount_spent, entity_id as owner, payer_account_id as spender
              from crypto_transfer
              where amount < 0 and consensus_timestamp > :fromTimestamp and consensus_timestamp <= :toTimestamp
                and is_approval is true
              group by entity_id, payer_account_id
            )
            update crypto_allowance_migration m
            set amount = amount + amount_spent
            from aggregated_approved_debit a
            where m.owner = a.owner and m.spender = a.owner
            """;

    private static final String UPDATE_SENTINEL_TIMESTAMP_SQL =
            """
            update crypto_allowance_migration
            set created_timestamp = :timestamp
            where owner = 0 and spender = 0;
            """;

    @Getter(lazy = true)
    private final long earliestTimestamp = earliestTimestamp();

    @Getter(lazy = true)
    private final boolean migrationTableExists = migrationTableExists();

    @Getter(lazy = true)
    private final TransactionOperations transactionOperations = transactionOperations();

    @Lazy
    public CryptoAllowanceAmountMigration(DBProperties dbProperties, @Owner JdbcTemplate jdbcTemplate) {
        super(new NamedParameterJdbcTemplate(jdbcTemplate), dbProperties.getSchema());
    }

    @Override
    protected Long getInitial() {
        if (!isMigrationTableExists()) {
            return 0L;
        }

        var endTimestamp = jdbcTemplate.getJdbcOperations().queryForObject(GET_END_TIMESTAMP_SQL, Long.class);
        return endTimestamp != null ? endTimestamp : 0L;
    }

    @Override
    protected int getSuccessChecksum() {
        return 1; // Change this if this migration should be rerun
    }

    @Nonnull
    @Override
    protected Optional<Long> migratePartial(Long last) {
        long minTimestamp = getEarliestTimestamp();
        if (last == 0 || last < minTimestamp) {
            return Optional.empty();
        }

        if (last == minTimestamp) {
            jdbcTemplate.update(MERGE_AMOUNT_SQL, Collections.emptyMap());
            jdbcTemplate.getJdbcOperations().execute(DROP_MIGRATION_TABLE_SQL);
            return Optional.empty();
        }

        long fromExclusive = Math.max(minTimestamp, last - INTERVAL);
        var parameters = new MapSqlParameterSource("fromTimestamp", fromExclusive).addValue("toTimestamp", last);
        jdbcTemplate.update(UPDATE_MIGRATION_AMOUNT_SQL, parameters);
        // Update the sentinel timestamp so the migration can resume if it's cancelled before completion
        jdbcTemplate.update(UPDATE_SENTINEL_TIMESTAMP_SQL, Map.of("timestamp", fromExclusive));

        return Optional.of(fromExclusive);
    }

    @Override
    public String getDescription() {
        return "Backfill crypto allowance amount";
    }

    @Override
    protected MigrationVersion getMinimumVersion() {
        return MigrationVersion.fromVersion("1.84.0"); // The version crypto_allowance_migration is created
    }

    private long earliestTimestamp() {
        try {
            // It's safe to use the earliest timestamp as the exclusive min timestamp, because it's impossible to grant
            // and spend the allowance at the same time
            Long timestamp = jdbcTemplate.queryForObject(
                    "select min(created_timestamp) from crypto_allowance_migration",
                    Collections.emptyMap(),
                    Long.class);
            if (timestamp == null) {
                log.warn("The table crypto_allowance_history is empty, use Long.MAX_VALUE as the earliest timestamp");
                timestamp = Long.MAX_VALUE;
            }
            return timestamp;
        } catch (DataAccessException ex) {
            log.error("Unable to get earliest timestamp from table crypto_allowance_migration", ex);
            return Long.MAX_VALUE;
        }
    }

    private boolean migrationTableExists() {
        try {
            jdbcTemplate.getJdbcOperations().execute("select 'crypto_allowance_migration'::regclass");
            return true;
        } catch (DataAccessException ex) {
            log.warn("Table crypto_allowance_migration doesn't exist");
            return false;
        }
    }

    private TransactionOperations transactionOperations() {
        var wrapped = jdbcTemplate.getJdbcTemplate();
        var transactionManager = new DataSourceTransactionManager(Objects.requireNonNull(wrapped.getDataSource()));
        return new TransactionTemplate(transactionManager);
    }
}
