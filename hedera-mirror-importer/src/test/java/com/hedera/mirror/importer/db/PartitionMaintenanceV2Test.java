/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
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

import com.hedera.mirror.importer.EnabledIfV2;
import com.hedera.mirror.importer.ImporterIntegrationTest;
import com.hedera.mirror.importer.config.Owner;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.flyway.FlywayProperties;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

@RequiredArgsConstructor
@EnabledIfV2
class PartitionMaintenanceV2Test extends ImporterIntegrationTest {
    private static final String GET_LATEST_PARTITIONS =
            """
                    select distinct on (tp.parent_table) tp.parent_table,
                                   tp.partition_column,
                                   tp.partition,
                                   tp.from_value::bigint as current_from,
                                   tp.to_value::bigint as current_to,
                                   (greatest(0, extract(
                                     epoch from (to_timestamp(tp.from_value::bigint / 1000000000.0) - ?::interval))::bigint * 1000000000)
                                   ) as previous_from
                            from time_partitions tp
                            order by tp.parent_table, tp.from_value::bigint desc
                    """;
    private final @Owner JdbcTemplate jdbcTemplate;
    private final PartitionMaintenance partitionMaintenance;
    private final FlywayProperties flywayProperties;

    private List<PartitionInfo> latestPartitions = new ArrayList<>();

    @BeforeEach
    void setup() {
        latestPartitions = getCurrentPartitions();
        assertThat(latestPartitions).isNotEmpty();
    }

    @Test
    void noPartitionsCreated() {
        partitionMaintenance.runMaintenance();
        assertThat(getCurrentPartitions()).isEqualTo(latestPartitions);
    }

    @Test
    void createsTimePartitions() {
        String dropLatestTimePartitions = latestPartitions.stream()
                .map(partitionInfo -> "drop table " + partitionInfo.partition)
                .collect(Collectors.joining(";\n"));
        jdbcTemplate.execute(dropLatestTimePartitions);

        List<PartitionInfo> newLatestPartitions = getCurrentPartitions();

        assertThat(newLatestPartitions).hasSize(latestPartitions.size());

        // Verify partitions were removed
        for (var i = 0; i < latestPartitions.size(); i++) {
            var actual = newLatestPartitions.get(i);
            var expected = latestPartitions.get(i);
            assertThat(actual.currentTo).isEqualTo(expected.currentFrom);
            assertThat(actual.currentFrom).isEqualTo(expected.previousFrom);
        }
        partitionMaintenance.runMaintenance();

        // Verify partitions were re-created
        assertThat(getCurrentPartitions()).isEqualTo(latestPartitions);
    }

    private List<PartitionInfo> getCurrentPartitions() {
        return jdbcTemplate.query(
                GET_LATEST_PARTITIONS,
                new DataClassRowMapper<>(PartitionInfo.class),
                flywayProperties.getPlaceholders().get("partitionTimeInterval"));
    }

    @Data
    private static class PartitionInfo {
        private String parentTable;
        private String partitionColumn;
        private String partition;
        private long currentFrom;
        private long previousFrom;
        private long currentTo;
    }
}
