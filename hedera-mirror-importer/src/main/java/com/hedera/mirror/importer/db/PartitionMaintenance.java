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
import java.util.List;
import lombok.*;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@CustomLog
@Component
@RequiredArgsConstructor
@Profile("v2")
public class PartitionMaintenance {
    private static final String TIMESTAMP_PARTITION_COLUMN_NAME_PATTERN = "^(.*_timestamp|consensus_end)$";
    private static final DateTimeFormatter CITUS_TIME_PARTITION_NAME_FORMATTER =
            DateTimeFormatter.ofPattern("'_p'yyyy_MM_dd_HHmmss").withZone(ZoneId.of("UTC"));

    // todo can probably drop these cases statements. One becomes boolean comparrison and the other can be changed to
    // use same calculation
    private static final String TABLE_INFO_QUERY = "SELECT "
            + "                   tp.parent_table, "
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
    private final TransactionTemplate transactionTemplate;
    private final PartitionMaintenanceConfiguration maintenanceConfig;

    @EventListener(ApplicationPreparedEvent.class)
    public void test(ApplicationPreparedEvent event) throws Exception {
        Thread.sleep(90L * 1000);
    }

    // TODO:// configure ...
    //    @Scheduled(cron = "${hedera.mirror.importer.db.maintenance.cron:0 0 0 1 * *}")
    @Scheduled(initialDelay = 0, fixedRate = 100000000000L)
    // TODO:// retry logic
    @Retryable(retryFor = DataAccessException.class)
    public void runMaintenance() {

        List<PartitionInfo> newPartitions = jdbcTemplate.query(
                TABLE_INFO_QUERY,
                new DataClassRowMapper<>(PartitionInfo.class),
                maintenanceConfig.getTimePartitionInterval());

        newPartitions.forEach(table -> transactionTemplate.executeWithoutResult(status -> {
            LocalDateTime start = Instant.ofEpochSecond(0L, table.getNextFrom())
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();
            LocalDateTime end = Instant.ofEpochSecond(0L, table.getNextTo())
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();

            if (!table.isTimePartition()
                    && table.getMaxEntityId() * maintenanceConfig.maxEntityIdRatio < table.getNextFrom()) {
                log.info("No new partition needed. Skipping creation for partition {}", table);
                return;
            }
            String interval = table.isTimePartition()
                    ? maintenanceConfig.timePartitionInterval
                    : maintenanceConfig.idPartitionInterval;
            Boolean created = jdbcTemplate.queryForObject(
                    CREATE_TIME_PARTITIONS_SQL, Boolean.class, table.parentTable, interval, start, end);

            if (BooleanUtils.isTrue(created) && !table.isTimePartition()) {
                // Work around granularity issue of citus table names
                String createdPartitionName =
                        table.getParentTable() + CITUS_TIME_PARTITION_NAME_FORMATTER.format(start);
                long partitionDuration = table.nextTo - table.nextFrom;
                long partitionCount = table.nextFrom / partitionDuration;
                String newPartitionName = String.format("%s_p%d", table.parentTable, partitionCount);
                jdbcTemplate.execute("ALTER table " + createdPartitionName + " rename to " + newPartitionName);
                log.info("Renamed {} to {}", createdPartitionName, newPartitionName);
            }
            log.info("Partition {} created {}", table, created);
        }));
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
        private double maxEntityIdRatio = 2.0;
    }
}
