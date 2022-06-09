package com.hedera.mirror.importer.migration;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.google.common.base.Stopwatch;
import java.io.IOException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.transaction.support.TransactionOperations;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RequiredArgsConstructor
abstract class AsyncJavaMigration<T> extends MirrorBaseJavaMigration {

    private static final String CHECK_FLYWAY_SCHEMA_HISTORY_EXISTENCE_SQL = "select exists( " +
            "  select * from information_schema.tables " +
            "  where table_schema = :schema and table_name = 'flyway_schema_history')";

    private static final String SELECT_LAST_CHECKSUM_SQL = "select checksum from flyway_schema_history " +
            "where script = :className order by installed_rank desc limit 1";

    private static final String UPDATE_CHECKSUM_SQL = "with last as (" +
            "  select installed_rank from flyway_schema_history" +
            "  where script = :className order by installed_rank desc limit 1" +
            ") " +
            "update flyway_schema_history f " +
            "set checksum = :checksum " +
            "from last " +
            "where f.installed_rank = last.installed_rank";

    protected final NamedParameterJdbcTemplate jdbcTemplate;

    private final String schema;

    private final TransactionOperations transactionOperations;

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

    @Override
    public MigrationVersion getVersion() {
        return null; // repeatable
    }

    public <O> O queryForObjectOrNull(String sql, SqlParameterSource paramSource, Class<O> requiredType) {
        try {
            return jdbcTemplate.queryForObject(sql, paramSource, requiredType);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    @Override
    protected void doMigrate() throws IOException {
        int successChecksum = getSuccessChecksum();
        if (successChecksum < 0) {
            log.error("Migration skipped due to negative success checksum {}, please fix it and rerun", successChecksum);
            return;
        }

        Mono.fromRunnable(this::migrateAsync)
                .subscribeOn(Schedulers.single())
                .doOnSuccess(t -> onSuccess())
                .doOnError(t -> log.error("Asynchronous migration failed:", t))
                .subscribe();
    }

    protected void migrateAsync() {
        long count = 0;
        T last = getInitial();
        var stopwatch = Stopwatch.createStarted();

        try {
            do {
                final T previous = last;
                last = transactionOperations.execute(t -> migratePartial(previous));
                count++;
            } while (last != null);

            log.info("Successfully completed asynchronous migration with {} iterations in {}", count, stopwatch);
        } catch (Exception e) {
            log.error("Error executing asynchronous migration after {} iterations in {}", count, stopwatch);
            throw e;
        }
    }

    protected abstract T getInitial();

    protected abstract int getSuccessChecksum();

    protected abstract T migratePartial(T last);

    private MapSqlParameterSource getSqlParamSource() {
        return new MapSqlParameterSource().addValue("className", getClass().getName());
    }

    private boolean hasFlywaySchemaHistoryTable() {
        var exists = jdbcTemplate.queryForObject(CHECK_FLYWAY_SCHEMA_HISTORY_EXISTENCE_SQL, Map.of("schema", schema),
                Boolean.class);
        return exists != null && exists;
    }

    private void onSuccess() {
        var paramSource = getSqlParamSource().addValue("checksum", getSuccessChecksum());
        jdbcTemplate.update(UPDATE_CHECKSUM_SQL, paramSource);
    }
}
