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

import static com.hedera.mirror.importer.config.CacheConfiguration.CACHE_NAME;
import static com.hedera.mirror.importer.config.CacheConfiguration.CACHE_TIME_PARTITION;
import static com.hedera.mirror.importer.config.CacheConfiguration.CACHE_TIME_PARTITION_OVERLAP;

import com.google.common.collect.Range;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@CustomLog
@Named
@RequiredArgsConstructor
public class TimePartitionServiceImpl implements TimePartitionService {

    private static final String GET_TIME_PARTITIONS_SQL = "select * from mirror_node_time_partitions where parent = ?";
    private static final RowMapper<TimePartition> ROW_MAPPER = (rs, rowNum) -> TimePartition.builder()
            .name(rs.getString("name"))
            .parent(rs.getString("parent"))
            .timestampRange(Range.closedOpen(rs.getLong("from_timestamp"), rs.getLong("to_timestamp")))
            .build();

    private final JdbcTemplate jdbcTemplate;

    @Cacheable(cacheManager = CACHE_TIME_PARTITION_OVERLAP, cacheNames = CACHE_NAME)
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

    @Cacheable(cacheManager = CACHE_TIME_PARTITION, cacheNames = CACHE_NAME)
    @Override
    public List<TimePartition> getTimePartitions(String tableName) {
        try {
            var partitions = jdbcTemplate.query(GET_TIME_PARTITIONS_SQL, ROW_MAPPER, tableName);
            if (partitions.isEmpty()) {
                return Collections.emptyList();
            }

            return Collections.unmodifiableList(partitions);
        } catch (Exception e) {
            log.warn("Unable to query time partitions for table {}", tableName, e);
            return Collections.emptyList();
        }
    }
}
