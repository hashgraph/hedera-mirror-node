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

import static jakarta.transaction.Transactional.TxType.REQUIRES_NEW;

import jakarta.inject.Named;
import jakarta.transaction.Transactional;
import java.time.*;
import java.util.List;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.retry.annotation.Retryable;

@Named
@RequiredArgsConstructor
@CustomLog
public class PartitionMaintenanceService {
    private static final String TIMESTAMP_PARTITION_COLUMN_NAME_PATTERN = "^(.*_timestamp|consensus_end)$";
    private static final ZoneId PARTITION_BOUND_TIMEZONE = ZoneId.of("UTC");

    private static final String TABLE_INFO_QUERY = "SELECT "
            + "                   tp.parent_table, "
            + "                   tp.partition_column, "
            + "                   max(tp.to_value::bigint) as next_from, "
            + "                   case when tp.partition_column::varchar ~ '"
            + TIMESTAMP_PARTITION_COLUMN_NAME_PATTERN + "'"
            + "                   then extract(epoch from (to_timestamp(max(tp.to_value::bigint / 1000000000.0)) + (?::interval))) * 1000000000 "
            + "                   else extract(epoch from (to_timestamp(max(tp.to_value::bigint / 1000000000.0)) + (?::interval))) * 1000000000 "
            + "                   end as next_to, "
            + "                   (tp.partition_column::varchar ~ '" + TIMESTAMP_PARTITION_COLUMN_NAME_PATTERN
            + "') as time_partition,"
            + "                   (select COALESCE(max(id), 1) from entity) as max_entity_id"
            + "               from time_partitions tp group by tp.parent_table, tp.partition_column";

    private static final String CREATE_TIME_PARTITIONS_SQL = "select create_time_partitions(table_name := ?,"
            + "                              partition_interval := ?::interval,"
            + "                              start_from := ?,"
            + "                              end_at := ?)";

    private static final String UPDATE_PARTITION_NAME_SQL =
            "select updatePartitionTableName(?::regclass, ?::interval, ?::timestamptz, ?)";

    private final JdbcTemplate jdbcTemplate;
    private final PartitionMaintenanceConfiguration maintenanceConfig;

    protected List<PartitionInfo> getNextPartitions() {
        return jdbcTemplate.query(
                TABLE_INFO_QUERY,
                new DataClassRowMapper<>(PartitionInfo.class),
                maintenanceConfig.getTimePartitionInterval(),
                maintenanceConfig.getIdPartitionInterval());
    }

    @Transactional(REQUIRES_NEW)
    @Retryable(retryFor = DataAccessException.class)
    protected boolean createPartition(PartitionInfo partitionInfo) {
        LocalDateTime start = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(0L, partitionInfo.getNextFrom()), PARTITION_BOUND_TIMEZONE);
        LocalDateTime end =
                LocalDateTime.ofInstant(Instant.ofEpochSecond(0L, partitionInfo.getNextTo()), PARTITION_BOUND_TIMEZONE);
        Duration partitionDuration = Duration.between(start, end);

        // will always premake one partition
        boolean makeTimePartition = partitionInfo.isTimePartition()
                && LocalDateTime.now(PARTITION_BOUND_TIMEZONE)
                        .plus(partitionDuration)
                        .isAfter(start);
        boolean makeIdPartition = !partitionInfo.isTimePartition()
                && partitionInfo.getMaxEntityId() * maintenanceConfig.getMaxEntityIdRatio()
                        >= partitionInfo.getNextFrom();
        boolean skipPartition = !(makeIdPartition || makeTimePartition);
        if (skipPartition) {
            log.info("No new partition needed. Skipping creation for {}", partitionInfo);
            return false;
        }
        String interval = partitionInfo.isTimePartition()
                ? maintenanceConfig.getTimePartitionInterval()
                : maintenanceConfig.getIdPartitionInterval();
        boolean created = BooleanUtils.isTrue(jdbcTemplate.queryForObject(
                CREATE_TIME_PARTITIONS_SQL, Boolean.class, partitionInfo.getParentTable(), interval, start, end));

        if (created && !partitionInfo.isTimePartition()) {
            // Work around timestamp granularity issue of citus partition table names
            long partitionCount = partitionInfo.getNextFrom() / partitionDuration.toNanos();
            jdbcTemplate.execute(UPDATE_PARTITION_NAME_SQL, (PreparedStatementCallback<Boolean>) ps -> {
                ps.setString(1, partitionInfo.getParentTable());
                ps.setString(2, maintenanceConfig.getIdPartitionInterval());
                ps.setObject(3, start);
                ps.setLong(4, partitionCount);
                return ps.execute();
            });
            log.info("Renamed partition: {}", partitionInfo);
        }
        log.info("Partition {} created {}", partitionInfo, created);
        return created;
    }
}
