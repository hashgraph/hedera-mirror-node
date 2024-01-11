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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import jakarta.annotation.Nonnull;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.transaction.support.TransactionOperations;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

abstract class AsyncJavaMigration<T> extends RepeatableMigration {

    private static final String CHECK_FLYWAY_SCHEMA_HISTORY_EXISTENCE_SQL =
            """
            select exists(select * from information_schema.tables
            where table_schema = :schema and table_name = 'flyway_schema_history')
            """;

    private static final String SELECT_LAST_CHECKSUM_SQL =
            """
            select checksum from flyway_schema_history
            where script = :className order by installed_rank desc limit 1
            """;

    private static final String UPDATE_CHECKSUM_SQL =
            """
            with last as (
              select installed_rank from flyway_schema_history
              where script = :className order by installed_rank desc limit 1
            )
            update flyway_schema_history f
            set checksum = :checksum,
            execution_time = least(2147483647, extract(epoch from now() - f.installed_on) * 1000)
            from last
            where f.installed_rank = last.installed_rank
            """;

    protected final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final String schema;
    private final AtomicBoolean complete = new AtomicBoolean(false);

    protected AsyncJavaMigration(
            Map<String, MigrationProperties> migrationPropertiesMap,
            NamedParameterJdbcTemplate namedParameterJdbcTemplate,
            String schema) {
        super(migrationPropertiesMap);
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.schema = schema;
    }

    @Override
    public Integer getChecksum() {
        if (!hasFlywaySchemaHistoryTable()) {
            return -1;
        }

        Integer lastChecksum = queryForObjectOrNull(SELECT_LAST_CHECKSUM_SQL, getSqlParamSource(), Integer.class);
        if (lastChecksum == null) {
            return -1;
        } else if (lastChecksum < 0) {
            return lastChecksum - 1;
        } else if (lastChecksum != getSuccessChecksum()) {
            return -1;
        }
        return lastChecksum;
    }

    protected abstract TransactionOperations getTransactionOperations();

    boolean isComplete() {
        return complete.get();
    }

    public <O> O queryForObjectOrNull(String sql, SqlParameterSource paramSource, Class<O> requiredType) {
        try {
            return namedParameterJdbcTemplate.queryForObject(sql, paramSource, requiredType);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    @Override
    protected void doMigrate() throws IOException {
        int checksum = getSuccessChecksum();
        if (checksum <= 0) {
            throw new IllegalArgumentException(String.format("Invalid non-positive success checksum %d", checksum));
        }

        Mono.fromRunnable(this::migrateAsync)
                .subscribeOn(Schedulers.single())
                .doOnSuccess(t -> onSuccess())
                .doOnError(t -> log.error("Asynchronous migration failed:", t))
                .doFinally(s -> complete.set(true))
                .subscribe();
    }

    protected void migrateAsync() {
        log.info("Starting asynchronous migration");

        long count = 0;
        var stopwatch = Stopwatch.createStarted();
        var last = Optional.of(getInitial());
        long minutes = 1L;

        try {
            do {
                final var previous = last;
                last = Objects.requireNonNullElse(
                        getTransactionOperations().execute(t -> migratePartial(previous.get())), Optional.empty());
                count++;

                long elapsed = stopwatch.elapsed(TimeUnit.MINUTES);
                if (elapsed >= minutes) {
                    log.info("Completed iteration {} with last value: {}", count, last.orElse(null));
                    minutes = elapsed + 1;
                }
            } while (last.isPresent());

            log.info("Successfully completed asynchronous migration with {} iterations in {}", count, stopwatch);
        } catch (Exception e) {
            log.error("Error executing asynchronous migration after {} iterations in {}", count, stopwatch);
            throw e;
        }
    }

    protected abstract T getInitial();

    /**
     * Gets the success checksum to set for the migration in flyway schema history table. Note the checksum is required
     * to be positive.
     *
     * @return The success checksum for the migration
     */
    protected final int getSuccessChecksum() {
        return migrationProperties.getChecksum();
    }

    @Nonnull
    protected abstract Optional<T> migratePartial(T last);

    private MapSqlParameterSource getSqlParamSource() {
        return new MapSqlParameterSource().addValue("className", getClass().getName());
    }

    private boolean hasFlywaySchemaHistoryTable() {
        var exists = namedParameterJdbcTemplate.queryForObject(
                CHECK_FLYWAY_SCHEMA_HISTORY_EXISTENCE_SQL, Map.of("schema", schema), Boolean.class);
        return exists != null && exists;
    }

    private void onSuccess() {
        var paramSource = getSqlParamSource().addValue("checksum", getSuccessChecksum());
        namedParameterJdbcTemplate.update(UPDATE_CHECKSUM_SQL, paramSource);
    }

    @VisibleForTesting
    void setComplete(boolean complete) {
        this.complete.set(complete);
    }
}
