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

import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RequiredArgsConstructor
abstract class AsyncJavaMigration extends MirrorBaseJavaMigration {

    private static final String SELECT_LAST_CHECKSUM_SQL = "select checksum from flyway_schema_history " +
            "where script = :className order by installed_rank desc limit 1";

    private static final String SELECT_SMALLEST_CHECKSUM_SQL = "select min(checksum) from flyway_schema_history " +
            "where script = :className";

    private static final String UPDATE_CHECKSUM_SQL = "with last as (" +
            "  select installed_rank from flyway_schema_history" +
            "  where script = :className order by installed_rank desc limit 1" +
            ") " +
            "update flyway_schema_history f " +
            "set checksum = :checksum " +
            "from last " +
            "where f.installed_rank = last.installed_rank";

    protected final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public Integer getChecksum() {
        var paramSource = getSqlParamSource();
        Integer lastChecksum = queryForObjectOrNull(SELECT_LAST_CHECKSUM_SQL, paramSource, Integer.class);

        if (lastChecksum == null) {
            return -1;
        } else if (lastChecksum < 0) {
            return lastChecksum - 1;
        } else if (lastChecksum != getSuccessChecksum()) {
            // success checksum has changed, return the smallest - 1 to guarantee it's different and flyway will rerun
            // this migration
            Integer smallest = queryForObjectOrNull(SELECT_SMALLEST_CHECKSUM_SQL, paramSource, Integer.class);
            return smallest != null ? smallest - 1 : -1;
        }
        return lastChecksum;
    }

    public <T> T queryForObjectOrNull(String sql, SqlParameterSource paramSource, Class<T> requiredType) {
        try {
            return jdbcTemplate.queryForObject(sql, paramSource, requiredType);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    @Override
    protected void doMigrate() throws IOException {
        Mono.fromRunnable(this::migrateAsync).subscribeOn(Schedulers.single()).doOnSuccess(t -> onSuccess()).subscribe();
    }

    protected abstract void migrateAsync();

    protected abstract int getSuccessChecksum();

    private MapSqlParameterSource getSqlParamSource() {
        return new MapSqlParameterSource().addValue("className", getClass().getName());
    }

    private void onSuccess() {
        var paramSource = getSqlParamSource().addValue("checksum", getSuccessChecksum());
        jdbcTemplate.update(UPDATE_CHECKSUM_SQL, paramSource);
    }
}
