/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

import com.hedera.mirror.importer.repository.AccountBalanceFileRepository;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

abstract class AbstractTimestampInfoMigration extends TimeSensitiveBalanceMigration {

    private static final String GET_TIMESTAMP_INFO_SQL =
            """
            with last_record_file as (
              select consensus_end
              from record_file
              order by consensus_end desc
              limit 1
            )
            select
              consensus_timestamp as snapshot_timestamp,
              consensus_timestamp + time_offset as from_timestamp,
              consensus_end as to_timestamp
            from account_balance_file, last_record_file
            where synthetic is false and consensus_timestamp + time_offset <= consensus_end
            order by consensus_timestamp desc
            limit 1
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    protected AbstractTimestampInfoMigration(
            AccountBalanceFileRepository accountBalanceFileRepository,
            Map<String, MigrationProperties> migrationPropertiesMap,
            NamedParameterJdbcTemplate jdbcTemplate,
            RecordFileRepository recordFileRepository,
            TransactionTemplate transactionTemplate) {
        super(migrationPropertiesMap, accountBalanceFileRepository, recordFileRepository);
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    protected AtomicInteger doMigrate(String sql) {
        var count = new AtomicInteger();
        transactionTemplate.executeWithoutResult(s -> {
            try {
                var timestampInfo = jdbcTemplate.queryForObject(
                        GET_TIMESTAMP_INFO_SQL, Collections.emptyMap(), new DataClassRowMapper<>(TimestampInfo.class));
                var params = new MapSqlParameterSource()
                        .addValue("snapshotTimestamp", timestampInfo.snapshotTimestamp())
                        .addValue("fromTimestamp", timestampInfo.fromTimestamp())
                        .addValue("toTimestamp", timestampInfo.toTimestamp());
                count.set(jdbcTemplate.update(sql, params));
            } catch (EmptyResultDataAccessException e) {
                // GET_TIMESTAMP_INFO_SQL returns empty result
            }
        });

        return count;
    }

    private record TimestampInfo(long snapshotTimestamp, long fromTimestamp, long toTimestamp) {}
}
