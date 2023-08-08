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

package com.hedera.mirror.importer.db;

import java.util.HashMap;
import java.util.Map;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@CustomLog
@Component
@RequiredArgsConstructor
public class PartitionMaintenance {
    private static final String TIMESTAMP_PARTITION_COLUMN_NAME_PATTERN = "^(.*_timestamp|consensus_end)$";

    // TODO:// configure likely not to be a configurable prop but instead static cron definition
    @Scheduled(initialDelay = 0, fixedRate = 1000000000000L)
    @Transactional
    public void test() {
        // TODO:// We may want to filter the schema here to so we can handle appropriately if there are partitioned
        // tables in others schemas we shouldn't touch
        String tableInfoQuery = "select " + "    par.relname as table_name, "
                + "    col.column_name "
                + "from   "
                + "    (select partrelid, unnest(partattrs) column_index"
                + "     from"
                + "         pg_partitioned_table) pt "
                + "join   "
                + "    pg_class par "
                + "on     "
                + "    par.oid = pt.partrelid "
                + "join"
                + "    information_schema.columns col "
                + "on  "
                + "    col.table_schema = par.relnamespace::regnamespace::text"
                + "    and col.table_name = par.relname"
                + "    and ordinal_position = pt.column_index";

        String sql = "select create_time_partitions(table_name := ?,"
                + "                              partition_interval := INTERVAL '1 month',"
                + "                              start_from := CURRENT_TIMESTAMP + '2 months',"
                + "                              end_at := CURRENT_TIMESTAMP + '24 months')";

        Map<String, String> tableInfo =
                jdbcTemplate.query(tableInfoQuery, (ResultSetExtractor<Map<String, String>>) rs -> {
                    HashMap<String, String> mapRet = new HashMap<>();
                    while (rs.next()) {
                        mapRet.put(rs.getString("table_name"), rs.getString("column_name"));
                    }
                    return mapRet;
                });

        tableInfo.entrySet().stream()
                .filter(entry -> entry.getValue().matches(TIMESTAMP_PARTITION_COLUMN_NAME_PATTERN))
                .map(Map.Entry::getKey)
                .forEach(table -> {
                    Boolean created = jdbcTemplate.queryForObject(sql, Boolean.class, table);
                    log.info("Table {} created partitions {}", table, created);
                });

        log.info("I am a test {}", tableInfo);
    }

    private final JdbcTemplate jdbcTemplate;
}
