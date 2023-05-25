/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.importer.config.CacheConfiguration.CACHE_MANAGER_TABLE_TIME_PARTITION;

import com.google.common.collect.Range;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@CacheConfig(cacheManager = CACHE_MANAGER_TABLE_TIME_PARTITION)
@CustomLog
@Named
@RequiredArgsConstructor
public class TimePartitionServiceImpl implements TimePartitionService {

    public static final String CACHE_NAME_TABLES = "tables";
    public static final String CACHE_NAME_TABLE_OVERLAPPING_PARTITIONS = "tablesOverlappingPartitions";

    private static final String GET_TIME_PARTITIONS_SQL =
            """
            select
              parent.relname as parent,
              child.relname as name,
              pg_get_expr(child.relpartbound, child.oid) as range
            from pg_inherits
            join pg_class parent on pg_inherits.inhparent = parent.oid
            join pg_class child  on pg_inherits.inhrelid = child.oid
            join pg_namespace nmsp_parent on nmsp_parent.oid = parent.relnamespace
            join pg_namespace nmsp_child  on nmsp_child.oid = child.relnamespace
            where parent.relname = ? and pg_get_expr(child.relpartbound, child.oid) like 'FOR VALUES FROM%';
            """;
    private static final Pattern RANGE_PATTERN = Pattern.compile("^FOR VALUES FROM \\('(\\d+)'\\) TO \\('(\\d+)'\\)$");
    private static final RowMapper<TimePartition> ROW_MAPPER = (rs, rowNum) -> TimePartition.builder()
            .name(rs.getString("name"))
            .parent(rs.getString("parent"))
            .range(rs.getString("range"))
            .build();

    private final JdbcTemplate jdbcTemplate;

    @Cacheable(cacheNames = CACHE_NAME_TABLE_OVERLAPPING_PARTITIONS)
    @Override
    public List<TimePartition> getOverlappingTimePartitions(String tableName, long fromTimestamp, long toTimestamp) {
        if (toTimestamp < fromTimestamp) {
            return Collections.emptyList();
        }

        var partitions = getTimePartitions(tableName);
        if (partitions.isEmpty()) {
            return Collections.emptyList();
        }

        int index = Collections.binarySearch(partitions, null, (current, key) -> {
            if (current.getTimestampRange().contains(fromTimestamp)) {
                return 0;
            }

            return current.getTimestampRange().lowerEndpoint() < fromTimestamp ? -1 : 1;
        });

        var overlappingPartitions = new ArrayList<TimePartition>();
        if (index >= 0) {
            overlappingPartitions.add(partitions.get(index));
            index++;
        } else {
            int insertIndex = -1 - index;
            if (insertIndex == partitions.size()) {
                // all partitions are before fromTimestamp
                return Collections.emptyList();
            }

            // otherwise fromTimestamp is before the first partition
            index = 0;
        }

        for (; index < partitions.size(); index++) {
            var partition = partitions.get(index);
            if (toTimestamp >= partition.getTimestampRange().lowerEndpoint()) {
                overlappingPartitions.add(partition);
            } else {
                break;
            }
        }

        return Collections.unmodifiableList(overlappingPartitions);
    }

    @Cacheable(cacheNames = CACHE_NAME_TABLES)
    @Override
    public List<TimePartition> getTimePartitions(String tableName) {
        try {
            var partitions = jdbcTemplate.query(GET_TIME_PARTITIONS_SQL, ROW_MAPPER, tableName);
            if (partitions.isEmpty()) {
                return Collections.emptyList();
            }

            var timePartitions = new ArrayList<TimePartition>();
            for (var partition : partitions) {
                var matcher = RANGE_PATTERN.matcher(partition.getRange());
                if (!matcher.matches()) {
                    log.warn(
                            "Unable to parse time partition range for partition {}: {}",
                            partition.getName(),
                            partition.getRange());
                    return Collections.emptyList();
                }

                var lowerBound = Long.parseLong(matcher.group(1));
                var upperBound = Long.parseLong(matcher.group(2));
                partition.setTimestampRange(Range.closedOpen(lowerBound, upperBound));
                timePartitions.add(partition);
            }

            Collections.sort(timePartitions);
            return Collections.unmodifiableList(timePartitions);
        } catch (Exception e) {
            log.warn("Unable to query time partitions for table {}: {}", tableName, e);
            return Collections.emptyList();
        }
    }
}
