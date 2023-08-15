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

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.importer.EnabledIfV2;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.config.Owner;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayProperties;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@EnabledIfV2
class PartitionMaintenanceTest extends IntegrationTest {
    private static final String GET_LATEST_PARTITIONS =
            """
            select distinct on (tp.parent_table) tp.parent_table,
                           tp.partition_column,
                           tp.partition,
                           tp.from_value::bigint as current_from,
                           tp.to_value::bigint as current_to,
                           (greatest(0, case when tp.partition_column::varchar ~ '^(.*_timestamp|consensus_end)$' then
                                             extract(epoch from (to_timestamp(tp.from_value::bigint / 1000000000.0) - ?::interval))::bigint * 1000000000
                                             else extract(epoch from (to_timestamp(tp.from_value::bigint / 1000000000.0) - ?::interval))::bigint * 1000000000 end)) as previous_from,
                           tp.partition_column::varchar ~ '^(.*_timestamp|consensus_end)$' as time_partition,
                           (select coalesce(max(id), 1) from entity)             as max_entity_id
                    from time_partitions tp
                    order by tp.parent_table, tp.from_value::bigint desc
            """;
    private final @Owner JdbcTemplate jdbcTemplate;
    private final PartitionMaintenance partitionMaintenance;
    private final DomainBuilder domainBuilder;
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
                .filter(PartitionInfo::isTimePartition)
                .map(partitionInfo -> "drop table " + partitionInfo.partition)
                .collect(Collectors.joining(";\n"));
        jdbcTemplate.execute(dropLatestTimePartitions);

        List<PartitionInfo> newLatestPartitions = getCurrentPartitions();

        assertThat(newLatestPartitions).hasSize(latestPartitions.size());

        // Verify partitions were removed
        for (var i = 0; i < latestPartitions.size(); i++) {
            var actual = newLatestPartitions.get(i);
            var expected = latestPartitions.get(i);
            if (actual.timePartition) {
                assertThat(actual.currentTo).isEqualTo(expected.currentFrom);
                assertThat(actual.currentFrom).isEqualTo(expected.previousFrom);
            }
        }
        partitionMaintenance.runMaintenance();

        // Verify partitions were re-created
        assertThat(getCurrentPartitions()).isEqualTo(latestPartitions);
    }

    @Test
    void createsIdPartitions() {
        var entityIdPartition = latestPartitions.stream()
                .filter(partitionInfo -> !partitionInfo.timePartition)
                .findFirst()
                .orElseThrow();
        var newEntityId = entityIdPartition.getCurrentTo() - 1;
        domainBuilder.entity().customize(builder -> builder.id(newEntityId)).persist();
        partitionMaintenance.runMaintenance();

        var expected = latestPartitions.stream()
                .peek(partitionInfo -> {
                    if (!partitionInfo.isTimePartition()) {
                        partitionInfo.setPartition(partitionInfo.parentTable + "_p1");
                        var newFrom = partitionInfo.currentTo;
                        var newTo = 2 * (newFrom - partitionInfo.currentFrom);
                        partitionInfo.setPreviousFrom(partitionInfo.currentFrom);
                        partitionInfo.setCurrentFrom(newFrom);
                        partitionInfo.setCurrentTo(newTo);
                    }
                    partitionInfo.setMaxEntityId(newEntityId);
                })
                .collect(Collectors.toList());

        assertThat(getCurrentPartitions()).isEqualTo(expected);
    }

    @NotNull
    private List<PartitionInfo> getCurrentPartitions() {
        return jdbcTemplate.query(
                GET_LATEST_PARTITIONS,
                new DataClassRowMapper<>(PartitionInfo.class),
                flywayProperties.getPlaceholders().get("partitionTimeInterval"),
                flywayProperties.getPlaceholders().get("partitionIdInterval"));
    }

    @Data
    private static class PartitionInfo {
        private String parentTable;
        private String partitionColumn;
        private String partition;
        private long currentFrom;
        private long previousFrom;
        private long currentTo;
        private long maxEntityId;
        private boolean timePartition;
    }
}
