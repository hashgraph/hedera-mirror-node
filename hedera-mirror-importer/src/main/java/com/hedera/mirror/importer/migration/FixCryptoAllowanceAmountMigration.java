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

import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.config.Owner;
import com.hedera.mirror.importer.db.DBProperties;
import jakarta.annotation.Nonnull;
import jakarta.inject.Named;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.CustomLog;
import lombok.Getter;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

@CustomLog
@Named
@Profile("!v2")
public class FixCryptoAllowanceAmountMigration extends AsyncJavaMigration<Long> {

    // Process at most 7 days worth approved crypto transfer in each iteration
    static final long INTERVAL = Duration.ofDays(7).toNanos();

    private static final String DROP_MIGRATION_TABLE_SQL = "drop table if exists crypto_allowance_migration";

    private static final String GET_END_TIMESTAMP_SQL =
            """
            select (select lower(timestamp_range) from crypto_allowance_migration where owner = 0 and spender = 0)
            """;

    private static final String MERGE_AMOUNT_SQL =
            """
            lock table crypto_allowance in access exclusive mode;
            lock table crypto_allowance_history in access exclusive mode;

            update crypto_allowance ca
            set amount = greatest(ca.amount + m.amount, 0)
            from crypto_allowance_migration m
            where m.owner = ca.owner and m.spender = ca.spender and lower(m.timestamp_range) = lower(ca.timestamp_range);

            update crypto_allowance_history h
            set amount = greatest(h.amount + m.amount, 0)
            from crypto_allowance_migration m
            where m.owner = h.owner and m.spender = h.spender and lower(m.timestamp_range) = lower(h.timestamp_range);
            """;

    private static final String UPDATE_MIGRATION_AMOUNT_SQL =
            """
            with approved_debit as (
              select sum(amount) as amount_spent, entity_id as owner, payer_account_id as spender
              from crypto_transfer
              where amount < 0 and consensus_timestamp > :fromTimestamp and consensus_timestamp <= :toTimestamp
                and is_approval is true
              group by entity_id, payer_account_id
            )
            update crypto_allowance_migration m
            set amount = amount + amount_spent
            from approved_debit a
            where m.owner = a.owner and m.spender = a.spender
            """;

    private static final String UPDATE_SENTINEL_TIMESTAMP_SQL =
            """
            update crypto_allowance_migration
            set timestamp_range = int8range(:timestamp, null)
            where owner = 0 and spender = 0;
            """;

    @Getter(lazy = true)
    private final long earliestTimestamp = earliestTimestamp();

    private final JdbcTemplate jdbcTemplate;

    @Getter(lazy = true)
    private final boolean migrationTableExists = migrationTableExists();

    @Getter(lazy = true)
    private final TransactionOperations transactionOperations = transactionOperations();

    @Lazy
    public FixCryptoAllowanceAmountMigration(
            DBProperties dbProperties, MirrorProperties mirrorProperties, @Owner JdbcTemplate jdbcTemplate) {
        super(mirrorProperties.getMigration(), new NamedParameterJdbcTemplate(jdbcTemplate), dbProperties.getSchema());
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    protected Long getInitial() {
        if (!isMigrationTableExists()) {
            return 0L;
        }

        var endTimestamp = jdbcTemplate.queryForObject(GET_END_TIMESTAMP_SQL, Long.class);
        return endTimestamp != null ? endTimestamp : 0L;
    }

    @Nonnull
    @Override
    protected Optional<Long> migratePartial(Long last) {
        long minTimestamp = getEarliestTimestamp();
        if (last == 0 || last < minTimestamp) {
            log.info("Nothing to backfill with last timestamp {} and earliest timestamp - {}", last, minTimestamp);

            if (isMigrationTableExists()) {
                jdbcTemplate.execute(DROP_MIGRATION_TABLE_SQL);
            }
            return Optional.empty();
        }

        long fromExclusive = Math.max(minTimestamp, last - INTERVAL);
        var parameters = new MapSqlParameterSource("fromTimestamp", fromExclusive).addValue("toTimestamp", last);
        namedParameterJdbcTemplate.update(UPDATE_MIGRATION_AMOUNT_SQL, parameters);

        if (fromExclusive == minTimestamp) {
            log.info("Aggregated all crypto transfers using allowance, now persist the changes");
            jdbcTemplate.update(MERGE_AMOUNT_SQL);
            jdbcTemplate.execute(DROP_MIGRATION_TABLE_SQL);
            return Optional.empty();
        }

        // Update the sentinel timestamp so the migration can resume if it's cancelled before completion
        namedParameterJdbcTemplate.update(UPDATE_SENTINEL_TIMESTAMP_SQL, Map.of("timestamp", fromExclusive));
        return Optional.of(fromExclusive);
    }

    @Override
    public String getDescription() {
        return "Backfill crypto allowance amount";
    }

    @Override
    protected MigrationVersion getMinimumVersion() {
        return MigrationVersion.fromVersion("1.84.2"); // The version crypto_allowance_migration is created
    }

    private long earliestTimestamp() {
        try {
            // It's safe to use the earliest timestamp as the exclusive min timestamp, because it's impossible to grant
            // and spend the allowance at the same time
            Long timestamp = jdbcTemplate.queryForObject(
                    "select min(lower(timestamp_range)) from crypto_allowance_migration", Long.class);
            if (timestamp == null) {
                log.warn("The table crypto_allowance_migration is empty, use Long.MAX_VALUE as the earliest timestamp");
                timestamp = Long.MAX_VALUE;
            }

            log.info("The earliest timestamp to back fill crypto allowance amount is {}", timestamp);
            return timestamp;
        } catch (DataAccessException ex) {
            log.warn("Unable to get earliest timestamp from table crypto_allowance_migration, use Long.MAX_VALUE", ex);
            return Long.MAX_VALUE;
        }
    }

    private boolean migrationTableExists() {
        try {
            jdbcTemplate.execute("select 'crypto_allowance_migration'::regclass");
            return true;
        } catch (DataAccessException ex) {
            log.warn("Table crypto_allowance_migration doesn't exist");
            return false;
        }
    }

    private TransactionOperations transactionOperations() {
        var transactionManager = new DataSourceTransactionManager(Objects.requireNonNull(jdbcTemplate.getDataSource()));
        return new TransactionTemplate(transactionManager);
    }
}
