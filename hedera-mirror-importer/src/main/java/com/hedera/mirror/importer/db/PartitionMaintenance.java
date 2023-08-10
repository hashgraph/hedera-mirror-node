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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import lombok.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@CustomLog
@Component
@RequiredArgsConstructor

// TODO:// only in V2
public class PartitionMaintenance {
    //    TODO:// use in query
    private static final String TIMESTAMP_PARTITION_COLUMN_NAME_PATTERN = "^(.*_timestamp|consensus_end)$";
    private static final DateTimeFormatter CITUS_TIME_PARTITION_NAME_FORMATTER =
            DateTimeFormatter.ofPattern("'_p'yyyy_MM_dd_HHmmss").withZone(ZoneId.of("UTC"));

    // todo can probably drop these cases statements. One becomes boolean comparrison and the other can be changed to
    // use same calculation
    private static final String TABLE_INFO_QUERY = "SELECT " + "                   tp.parent_table, "
            + "                   tp.partition_column, "
            + "                   max(tp.to_value::bigint) as next_from, "
            + "                   case when tp.partition_column::varchar ~ '"
            + TIMESTAMP_PARTITION_COLUMN_NAME_PATTERN + "'"
            + "                   then extract(epoch from (to_timestamp(max(tp.to_value::bigint / 1000000000)) + (?::interval))) * 1000000000 "
            + "                   else 2 * max(tp.to_value::bigint) - max(tp.from_value::bigint) "
            + "                   end as next_to, "
            + "                   case when tp.partition_column::varchar ~ '"
            + TIMESTAMP_PARTITION_COLUMN_NAME_PATTERN + "'" + "                   then true  "
            + "                   else false  "
            + "                   end as time_partition "
            + "               from time_partitions tp group by tp.parent_table, tp.partition_column";

    private static final String CREATE_TIME_PARTITIONS_SQL = "select create_time_partitions(table_name := ?,"
            + "                              partition_interval := ?::interval,"
            + "                              start_from := ?,"
            + "                              end_at := ?)";

    private final JdbcTemplate jdbcTemplate;
    private final PartitionMaintenanceConfiguration maintenanceConfig;

    // TODO:// configure ... likely not to be a configurable prop but instead static cron definition
    @Scheduled(initialDelay = 0, fixedRate = 1000000000000L)
    @Transactional
    public void test() {
        // TODO PessimisticLockingFailureException when trying to create partitions. Probably execute individual tables
        // in a transaction template
        jdbcTemplate
                .query(
                        TABLE_INFO_QUERY,
                        new DataClassRowMapper<>(PartitionInfo.class),
                        maintenanceConfig.getTimePartitionInterval())
                .forEach(table -> {
                    LocalDateTime start = Instant.ofEpochSecond(0L, table.getNextFrom())
                            .atZone(ZoneId.of("UTC"))
                            .toLocalDateTime();
                    LocalDateTime end = Instant.ofEpochSecond(0L, table.getNextTo())
                            .atZone(ZoneId.of("UTC"))
                            .toLocalDateTime();

                    // TODO configure threshold
                    if (false && !table.isTimePartition() && table.getMaxEntityId() * 2 < table.getNextFrom()) {
                        log.info("No new partition needed. Skipping creation for partition {}", table);
                        return;
                    }
                    String interval = table.isTimePartition()
                            ? maintenanceConfig.timePartitionInterval
                            : maintenanceConfig.idPartitionInterval;
                    Boolean created = jdbcTemplate.queryForObject(
                            CREATE_TIME_PARTITIONS_SQL, Boolean.class, table.parentTable, interval, start, end);

                    if (created && !table.isTimePartition()) {
                        // Work around granularity issue of citus table names
                        String createdPartitionName =
                                table.getParentTable() + CITUS_TIME_PARTITION_NAME_FORMATTER.format(start);
                        long partitionDuration = table.nextTo - table.nextFrom;
                        long partitionCount = table.nextFrom / partitionDuration;
                        String newPartitionName = String.format("%s_p%d", table.parentTable, partitionCount);
                        // TODO:// handle failure
                        jdbcTemplate.execute("ALTER table " + createdPartitionName + " rename to " + newPartitionName);
                        log.info("Renamed {} to {}", createdPartitionName, newPartitionName);
                    }
                    log.info("Partition {} created {}", table, created);
                });
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class PartitionInfo {
        private String parentTable;
        private String partitionColumn;
        private long nextFrom;
        private long nextTo;
        private long maxEntityId;
        private boolean timePartition;
    }

    @ConfigurationProperties("hedera.mirror.importer.db.maintenance")
    @Data
    private static class PartitionMaintenanceConfiguration {
        private String timePartitionInterval = "1 month";
        private String idPartitionInterval = ".001 seconds";
    }
}
