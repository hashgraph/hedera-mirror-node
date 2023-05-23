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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.config.Owner;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@TestInstance(Lifecycle.PER_CLASS)
class TimePartitionServiceTest extends IntegrationTest {

    private static final List<TimePartition> EVENT_TIME_PARTITIONS = List.of(
            TimePartition.builder()
                    .name("event_00")
                    .parent("event")
                    .range("FOR VALUES FROM ('1000000000') TO ('2000000000')")
                    .timestampRange(Range.closedOpen(1000000000L, 2000000000L))
                    .build(),
            TimePartition.builder()
                    .name("event_01")
                    .parent("event")
                    .range("FOR VALUES FROM ('2000000000') TO ('3000000000')")
                    .timestampRange(Range.closedOpen(2000000000L, 3000000000L))
                    .build(),
            TimePartition.builder()
                    .name("event_02")
                    .parent("event")
                    .range("FOR VALUES FROM ('3000000000') TO ('4000000000')")
                    .timestampRange(Range.closedOpen(3000000000L, 4000000000L))
                    .build());

    private static final String CREATE_TABLE_DDL =
            """
            create table event (name text not null, timestamp bigint not null) partition by range (timestamp);
            create table event_default partition of event default;
            create table event_00 partition of event for values from ('1000000000') to ('2000000000');
            create table event_02 partition of event for values from ('3000000000') to ('4000000000');
            create table event_01 partition of event for values from ('2000000000') to ('3000000000');
            create table location (name text not null, code varchar(1) not null) partition by range (code);
            create table location_default partition of location default;
            create table location_00 partition of location for values from ('A') to ('H');
            create table location_01 partition of location for values from ('H') to ('O');
            create table not_partitioned (id integer not null);
            """;
    private static final String DROP_TABLE_DDL =
            """
            drop table event cascade;
            drop table location cascade;
            drop table not_partitioned;
            """;

    private final @Owner JdbcTemplate jdbcTemplate;
    private final TimePartitionService timePartitionService;

    @BeforeAll
    void setup() {
        jdbcTemplate.execute(CREATE_TABLE_DDL);
    }

    @AfterAll
    void teardown() {
        jdbcTemplate.execute(DROP_TABLE_DDL);
    }

    @ParameterizedTest
    @MethodSource("generateOverlappingTimePartitionsTestData")
    void getOverlappingTimePartitions(
            String tableName, long fromTimestamp, long toTimestamp, List<TimePartition> expected) {
        assertThat(timePartitionService.getOverlappingTimePartitions(tableName, fromTimestamp, toTimestamp))
                .containsExactlyElementsOf(expected);
    }

    @Test
    void getTimePartitions() {
        assertThat(timePartitionService.getTimePartitions("event")).containsExactlyElementsOf(EVENT_TIME_PARTITIONS);
    }

    @Test
    void getTimePartitionsEmpty() {
        // Table location is partitioned but not partitioned on timestamp
        assertThat(timePartitionService.getTimePartitions("location")).isEmpty();
        assertThat(timePartitionService.getTimePartitions("non_existent_table")).isEmpty();
        assertThat(timePartitionService.getTimePartitions("not_partitioned")).isEmpty();
    }

    static Stream<Arguments> generateOverlappingTimePartitionsTestData() {
        return Stream.of(
                Arguments.of("event", 1000000000L, 1000000000L, List.of(EVENT_TIME_PARTITIONS.get(0))), // from = to
                Arguments.of("event", 1000000000L, 1000000001L, List.of(EVENT_TIME_PARTITIONS.get(0))),
                Arguments.of(
                        "event",
                        1000000001L,
                        2000000000L,
                        List.of(EVENT_TIME_PARTITIONS.get(0), EVENT_TIME_PARTITIONS.get(1))),
                Arguments.of("event", 1000000000L, 3999999999L, EVENT_TIME_PARTITIONS),
                Arguments.of("event", 1L, 3999999999L, EVENT_TIME_PARTITIONS),
                Arguments.of("event", 1L, 4000000000L, EVENT_TIME_PARTITIONS),
                // empty list
                Arguments.of("event", 1000000001L, 1000000000L, Collections.EMPTY_LIST), // from > to
                Arguments.of("event", 1L, 2L, Collections.EMPTY_LIST), // both before first
                Arguments.of("event", 4000000000L, 4000000001L, Collections.EMPTY_LIST), // both after last
                Arguments.of("location", 1000000000L, 1000000000L, Collections.EMPTY_LIST),
                Arguments.of("non_existent_table", 1000000000L, 1000000000L, Collections.EMPTY_LIST),
                Arguments.of("not_partitioned", 1000000000L, 1000000000L, Collections.EMPTY_LIST));
    }
}
