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

import com.google.common.collect.Range;
import java.util.Comparator;
import javax.annotation.Nullable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@AllArgsConstructor(access = AccessLevel.PRIVATE) // For builder
@Builder
@Data
public class TimePartition implements Comparable<TimePartition> {
    private static final Comparator<TimePartition> COMPARATOR = Comparator.nullsFirst(
            Comparator.comparingLong(t -> t.getTimestampRange().lowerEndpoint()));

    private String name;
    private String parent;
    private String range;
    private Range<Long> timestampRange;

    @Override
    public int compareTo(@Nullable TimePartition other) {
        return COMPARATOR.compare(this, other);
    }

    public long getEnd() {
        return timestampRange.upperEndpoint() - 1;
    }
}
