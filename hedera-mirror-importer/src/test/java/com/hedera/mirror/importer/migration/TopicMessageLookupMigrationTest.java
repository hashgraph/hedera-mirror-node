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

package com.hedera.mirror.importer.migration;

import static com.hedera.mirror.common.domain.entity.EntityType.TOPIC;
import static com.hedera.mirror.importer.TestUtils.plus;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.topic.TopicMessageLookup;
import com.hedera.mirror.importer.EnabledIfV1;
import com.hedera.mirror.importer.parser.record.entity.topic.AbstractTopicMessageLookupIntegrationTest;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Tag("migration")
class TopicMessageLookupMigrationTest extends AbstractTopicMessageLookupIntegrationTest {

    private final TopicMessageLookupMigration migration;

    @Test
    void checksum() {
        assertThat(migration.getChecksum()).isOne();
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
        assertThat(topicMessageLookupRepository.count()).isZero();
    }

    @Test
    void idempotent() {
        migrate(true);
        migrate(false);
    }

    @Test
    void migrateOnce() {
        migrate(true);
    }

    private void migrate(boolean loadData) {
        // given
        // the first partition is closed
        var partition1 = partitions.get(0);
        var partition2 = partitions.get(1);
        long partition1Start = partition1.getTimestampRange().lowerEndpoint();
        long consensusStart = plus(partition2.getTimestampRange().lowerEndpoint(), Duration.ofSeconds(10));
        var topicId1 = EntityId.of(5000, TOPIC);
        var topicId2 = EntityId.of(5001, TOPIC);
        var topicId3 = EntityId.of(5002, TOPIC);

        // data in topic_message_lookup table before migration
        domainBuilder.topicMessageLookup().persist();

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
    }
}
