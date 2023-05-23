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

import java.util.List;

public interface TimePartitionService {
    /**
     * Get the time partitions overlapping the range [fromTimestamp, toTimestamp]
     *
     * @param tableName The table name
     * @param fromTimestamp The from timestamp, inclusive
     * @param toTimestamp The to timestamp, inclusive
     * @return The overlapping time partitions
     */
    List<TimePartition> getOverlappingTimePartitions(String tableName, long fromTimestamp, long toTimestamp);

    /**
     * Get the time partitions for a given table. The returned time partitions are sorted by the timestamp range
     * in ascending order.
     *
     * @param tableName The table name
     * @return The time partitions. If the table is not time partitioned or doesn't have time partitions, returns an empty list
     */
    List<TimePartition> getTimePartitions(String tableName);
}
