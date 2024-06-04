/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.migration;

import static com.hedera.mirror.importer.TestUtils.plus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.topic.TopicMessageLookup;
import com.hedera.mirror.importer.EnabledIfV1;
import com.hedera.mirror.importer.parser.record.entity.topic.AbstractTopicMessageLookupIntegrationTest;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@RequiredArgsConstructor
@Tag("migration")
@ExtendWith({OutputCaptureExtension.class})
class TopicMessageLookupMigrationTest extends AbstractTopicMessageLookupIntegrationTest {

    private static final String RESET_CHECKSUM_SQL = "update flyway_schema_history set checksum = -1 where script = ?";

    private static final String SELECT_LAST_CHECKSUM_SQL =
            """
            select (
              select checksum from flyway_schema_history
              where script = ?
              order by installed_rank desc
              limit 1
            )
            """;

    private final TopicMessageLookupMigration migration;

    @AfterEach
    @BeforeEach
    void resetChecksum() {
        jdbcOperations.update(RESET_CHECKSUM_SQL, migration.getClass().getName());
    }

    @Test
    void disabled() {
        // given
        entityProperties.getPersist().setTopicMessageLookups(false);
        long partition1Start = partitions.get(0).getTimestampRange().lowerEndpoint();
        domainBuilder
                .recordFile()
                .customize(r ->
                        r.consensusStart(partition1Start).consensusEnd(plus(partition1Start, RECORD_FILE_INTERVAL)))
                .persist();
        domainBuilder
                .topicMessage()
                .customize(t -> t.consensusTimestamp(partition1Start))
                .persist();

        // when
        runMigration();

        // then
        assertThat(topicMessageLookupRepository.count()).isZero();
    }

    @Test
    void empty() {
        runMigration();
        assertThat(topicMessageLookupRepository.count()).isZero();
    }

    @Test
    void emptyTopicMessage() {
        // given
        long consensusStart = partitions.get(0).getTimestampRange().lowerEndpoint();
        domainBuilder
                .recordFile()
                .customize(
                        r -> r.consensusStart(consensusStart).consensusEnd(plus(consensusStart, RECORD_FILE_INTERVAL)))
                .persist();

        // when
        runMigration();

        // then
        assertThat(topicMessageLookupRepository.count()).isZero();
    }

    @Test
    void idempotent(CapturedOutput output) {
        var activeMessage = "Migrating topic_message_lookup for partition "
                + partitions.get(1).getName() + " synchronously";
        var expectedPartitions = Arrays.asList(partitions.get(0), partitions.get(1));

        migrate(true);
        assertThat(output).contains(activeMessage);
        for (var partition : expectedPartitions) {
            var expectedMessage = "Partition " + partition.getName() + " already migrated";
            assertThat(output).doesNotContain(expectedMessage);
        }

        migrate(false);
        for (var partition : expectedPartitions) {
            var expectedMessage = "Partition " + partition.getName() + " already migrated";
            assertThat(output).contains(expectedMessage);
        }
    }

    @Test
    void migrateOnce() {
        migrate(true);
    }

    @Test
    void onlyUnprocessedPartitionsAreProcessed(CapturedOutput output) {
        migrate(true);
        var closedPartiton = partitions.get(0);
        var openPartition = partitions.get(1);
        var deleteSql = "delete from topic_message_lookup where partition = ?";
        jdbcTemplate.update(deleteSql, closedPartiton.getName());

        migrate(false);
        assertThat(output)
                .contains("Partition " + openPartition.getName() + " already migrated")
                .doesNotContain("Partition " + closedPartiton.getName() + " already migrated");
    }

    @Test
    void onlyProcessPartitionsPriorToRecordFile() {
        var beforeRecordFilePartition = partitions.getFirst();

        long consensusStart = beforeRecordFilePartition.getTimestampRange().lowerEndpoint();
        long consensusEnd = beforeRecordFilePartition.getTimestampRange().upperEndpoint() - 1;

        var topicId1 = EntityId.of(5000);
        var topicId2 = EntityId.of(5001);

        domainBuilder
                .recordFile()
                .customize(r -> r.consensusStart(consensusStart).consensusEnd(consensusEnd))
                .persist();

        // Before record file
        domainBuilder
                .topicMessage()
                .customize(t -> t.consensusTimestamp(consensusStart + 1)
                        .sequenceNumber(1)
                        .topicId(topicId1))
                .persist();
        domainBuilder
                .topicMessage()
                .customize(t -> t.consensusTimestamp(consensusStart + 2)
                        .sequenceNumber(1)
                        .topicId(topicId2))
                .persist();

        domainBuilder
                .topicMessage()
                .customize(t -> t.consensusTimestamp(consensusStart + 3)
                        .sequenceNumber(2)
                        .topicId(topicId2))
                .persist();

        // Insert data later than latest record file to ensure it is not processed
        domainBuilder
                .topicMessage()
                .customize(t ->
                        t.consensusTimestamp(consensusEnd + 1).sequenceNumber(2).topicId(topicId1))
                .persist();
        domainBuilder
                .topicMessage()
                .customize(t ->
                        t.consensusTimestamp(consensusEnd + 2).sequenceNumber(3).topicId(topicId2))
                .persist();

        var expected = List.of(
                TopicMessageLookup.builder()
                        .partition(beforeRecordFilePartition.getName())
                        .sequenceNumberRange(Range.closedOpen(1L, 2L))
                        .timestampRange(Range.closedOpen(consensusStart + 1, consensusStart + 2))
                        .topicId(topicId1.getId())
                        .build(),
                TopicMessageLookup.builder()
                        .partition(beforeRecordFilePartition.getName())
                        .sequenceNumberRange(Range.closedOpen(1L, 3L))
                        .timestampRange(Range.closedOpen(consensusStart + 2, consensusStart + 4))
                        .topicId(topicId2.getId())
                        .build());

        runMigration();

        assertThat(topicMessageLookupRepository.findAll()).containsExactlyInAnyOrderElementsOf(expected);
    }

    private void migrate(boolean loadData) {
        // given
        // the first partition is closed
        var partition1 = partitions.get(0);
        var partition2 = partitions.get(1);
        long partition1Start = partition1.getTimestampRange().lowerEndpoint();
        long consensusStart = plus(partition2.getTimestampRange().lowerEndpoint(), Duration.ofSeconds(10));
        var topicId1 = EntityId.of(5000);
        var topicId2 = EntityId.of(5001);
        var topicId3 = EntityId.of(5002);

        if (loadData) {
            domainBuilder
                    .recordFile()
                    .customize(r ->
                            r.consensusStart(consensusStart).consensusEnd(plus(consensusStart, RECORD_FILE_INTERVAL)))
                    .persist();

            // partition 1
            domainBuilder
                    .topicMessage()
                    .customize(t -> t.consensusTimestamp(partition1Start)
                            .sequenceNumber(1)
                            .topicId(topicId1))
                    .persist();
            domainBuilder
                    .topicMessage()
                    .customize(t -> t.consensusTimestamp(partition1Start + 1)
                            .sequenceNumber(1)
                            .topicId(topicId2))
                    .persist();
            domainBuilder
                    .topicMessage()
                    .customize(t -> t.consensusTimestamp(partition1Start + 2)
                            .sequenceNumber(2)
                            .topicId(topicId2))
                    .persist();

            // partition 2
            domainBuilder
                    .topicMessage()
                    .customize(t -> t.consensusTimestamp(consensusStart)
                            .sequenceNumber(2)
                            .topicId(topicId1))
                    .persist();
            domainBuilder
                    .topicMessage()
                    .customize(t -> t.consensusTimestamp(consensusStart + 1)
                            .sequenceNumber(3)
                            .topicId(topicId2))
                    .persist();
            domainBuilder
                    .topicMessage()
                    .customize(t -> t.consensusTimestamp(consensusStart + 2)
                            .sequenceNumber(1)
                            .topicId(topicId3))
                    .persist();
            domainBuilder
                    .topicMessage()
                    .customize(t -> t.consensusTimestamp(consensusStart + 3)
                            .sequenceNumber(2)
                            .topicId(topicId3))
                    .persist();
        }

        // when
        runMigration();

        // then
        var expected = List.of(
                // partition 1
                TopicMessageLookup.builder()
                        .partition(partition1.getName())
                        .sequenceNumberRange(Range.closedOpen(1L, 2L))
                        .timestampRange(Range.closedOpen(partition1Start, partition1Start + 1))
                        .topicId(topicId1.getId())
                        .build(),
                TopicMessageLookup.builder()
                        .partition(partition1.getName())
                        .sequenceNumberRange(Range.closedOpen(1L, 3L))
                        .timestampRange(Range.closedOpen(partition1Start + 1, partition1Start + 3))
                        .topicId(topicId2.getId())
                        .build(),
                // partition 2
                TopicMessageLookup.builder()
                        .partition(partition2.getName())
                        .sequenceNumberRange(Range.closedOpen(2L, 3L))
                        .timestampRange(Range.closedOpen(consensusStart, consensusStart + 1))
                        .topicId(topicId1.getId())
                        .build(),
                TopicMessageLookup.builder()
                        .partition(partition2.getName())
                        .sequenceNumberRange(Range.closedOpen(3L, 4L))
                        .timestampRange(Range.closedOpen(consensusStart + 1, consensusStart + 2))
                        .topicId(topicId2.getId())
                        .build(),
                TopicMessageLookup.builder()
                        .partition(partition2.getName())
                        .sequenceNumberRange(Range.closedOpen(1L, 3L))
                        .timestampRange(Range.closedOpen(consensusStart + 2, consensusStart + 4))
                        .topicId(topicId3.getId())
                        .build());
        assertThat(topicMessageLookupRepository.findAll()).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(migration.getChecksum()).isOne();
    }

    @EnabledIfV1
    @Test
    void notPartitioned() {
        revertPartitions();
        runMigration();
        assertThat(topicMessageLookupRepository.count()).isZero();
    }

    @SneakyThrows
    private void runMigration() {
        migration.doMigrate();
        waitForCompletion();
    }

    protected void waitForCompletion() {
        await().atMost(Duration.ofSeconds(5))
                .pollDelay(Duration.ofMillis(100))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> assertThat(isMigrationCompleted()).isTrue());
    }

    private boolean isMigrationCompleted() {
        var actual = jdbcOperations.queryForObject(
                SELECT_LAST_CHECKSUM_SQL, Integer.class, migration.getClass().getName());
        return Objects.equals(actual, migration.getSuccessChecksum());
    }
}
