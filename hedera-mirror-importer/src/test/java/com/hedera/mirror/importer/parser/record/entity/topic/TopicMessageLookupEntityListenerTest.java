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

package com.hedera.mirror.importer.parser.record.entity.topic;

import static com.hedera.mirror.importer.TestUtils.plus;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.topic.TopicMessage;
import com.hedera.mirror.common.domain.topic.TopicMessageLookup;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.importer.EnabledIfV1;
import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.parser.record.RecordStreamFileListener;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class TopicMessageLookupEntityListenerTest extends AbstractTopicMessageLookupIntegrationTest {

    private final EntityListener entityListener;
    private final RecordStreamFileListener recordStreamFileListener;
    private final TransactionTemplate transactionTemplate;

    @ParameterizedTest
    @CsvSource(textBlock = """
            false, true
            false, false
            true, false
            """)
    void disabled(boolean topics, boolean topicMessageLookups) {
        // given, when
        entityProperties.getPersist().setTopics(topics);
        entityProperties.getPersist().setTopicMessageLookups(topicMessageLookups);
        setupAndProcessTopicMessages();

        // then
        assertThat(topicMessageLookupRepository.count()).isZero();
    }

    @Test
    void failThenReprocess() {
        // given
        var partition = partitions.get(0);
        long timestamp = partition.getTimestampRange().upperEndpoint() - 1000L;
        var topicMessage1 = domainBuilder
                .topicMessage()
                .customize(t -> t.consensusTimestamp(timestamp).sequenceNumber(1))
                .get();
        var topicMessage2 = domainBuilder
                .topicMessage()
                .customize(t ->
                        t.consensusTimestamp(timestamp + 1).sequenceNumber(2).topicId(topicMessage1.getTopicId()))
                .get();
        var topicMessage3 = domainBuilder
                .topicMessage()
                .customize(t -> t.consensusTimestamp(timestamp + 2).sequenceNumber(5))
                .get();
        var topicMessage4 = domainBuilder
                .topicMessage()
                .customize(t ->
                        t.consensusTimestamp(timestamp + 3).sequenceNumber(6).topicId(topicMessage3.getTopicId()))
                .get();
        var recordFile = recordFile(timestamp);
        var topicMessages = List.of(topicMessage1, topicMessage2, topicMessage3, topicMessage4);

        // when
        completeFileAndFail(topicMessages, recordFile);

        // then
        assertThat(topicMessageLookupRepository.count()).isZero();

        // when topic messages are processed and the record file closes the partition
        completeFileAndSucceed(topicMessages, recordFile);

        // then
        assertThat(topicMessageLookupRepository.findAll())
                .containsExactlyInAnyOrder(
                        TestUtils.toTopicMessageLookup(partition.getName(), topicMessage1, topicMessage2),
                        TestUtils.toTopicMessageLookup(partition.getName(), topicMessage3, topicMessage4));
    }

    @Test
    void spanningTwoPartitions() {
        // given
        var partition1 = partitions.get(0);
        var partition2 = partitions.get(1);
        long partition2From = partition2.getTimestampRange().lowerEndpoint();
        long consensusStart = partition2From - 1000L;
        long topicId1 = domainBuilder.id();
        var topicEntityId1 = EntityId.of(topicId1, EntityType.TOPIC);
        long topicId2 = domainBuilder.id();
        var topicEntityId2 = EntityId.of(topicId2, EntityType.TOPIC);
        // existing topic message lookups
        domainBuilder
                .topicMessageLookup()
                .customize(t -> t.partition(partition1.getName())
                        .sequenceNumberRange(Range.closedOpen(1L, 3L))
                        .timestampRange(Range.closedOpen(consensusStart - 100L, consensusStart - 50L))
                        .topicId(topicId1))
                .persist();
        // new topic messages in the first partition
        var topicMessages = List.of(
                domainBuilder
                        .topicMessage()
                        .customize(t -> t.consensusTimestamp(consensusStart)
                                .sequenceNumber(3L)
                                .topicId(topicEntityId1))
                        .get(),
                domainBuilder
                        .topicMessage()
                        .customize(t -> t.consensusTimestamp(consensusStart + 1)
                                .sequenceNumber(1L)
                                .topicId(topicEntityId2))
                        .get(),
                // new topic messages in the second partition
                domainBuilder
                        .topicMessage()
                        .customize(t -> t.consensusTimestamp(partition2From)
                                .sequenceNumber(4L)
                                .topicId(topicEntityId1))
                        .get(),
                domainBuilder
                        .topicMessage()
                        .customize(t -> t.consensusTimestamp(partition2From + 1)
                                .sequenceNumber(2L)
                                .topicId(topicEntityId2))
                        .get(),
                domainBuilder
                        .topicMessage()
                        .customize(t -> t.consensusTimestamp(partition2From + 2)
                                .sequenceNumber(3L)
                                .topicId(topicEntityId2))
                        .get());

        // when
        completeFileAndSucceed(topicMessages, recordFile(consensusStart));

        // then
        var expected = List.of(
                // for partition1
                TopicMessageLookup.builder()
                        .partition(partition1.getName())
                        .sequenceNumberRange(Range.closedOpen(1L, 4L))
                        .timestampRange(Range.closedOpen(consensusStart - 100L, consensusStart + 1L))
                        .topicId(topicId1)
                        .build(),
                TopicMessageLookup.builder()
                        .partition(partition1.getName())
                        .sequenceNumberRange(Range.closedOpen(1L, 2L))
                        .timestampRange(Range.closedOpen(consensusStart + 1L, consensusStart + 2L))
                        .topicId(topicId2)
                        .build(),
                // for partition2
                TopicMessageLookup.builder()
                        .partition(partition2.getName())
                        .sequenceNumberRange(Range.closedOpen(4L, 5L))
                        .timestampRange(Range.closedOpen(partition2From, partition2From + 1L))
                        .topicId(topicId1)
                        .build(),
                TopicMessageLookup.builder()
                        .partition(partition2.getName())
                        .sequenceNumberRange(Range.closedOpen(2L, 4L))
                        .timestampRange(Range.closedOpen(partition2From + 1L, partition2From + 3L))
                        .topicId(topicId2)
                        .build());
        assertThat(topicMessageLookupRepository.findAll()).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void noTopicMessages() {
        long partitionEnd = partitions.get(0).getEnd();
        completeFileAndSucceed(Collections.emptyList(), recordFile(partitionEnd));
        assertThat(topicMessageLookupRepository.count()).isZero();
    }

    @EnabledIfV1
    @Test
    void notPartitioned() {
        revertPartitions();
        var topicMessages = List.of(
                domainBuilder.topicMessage().get(), domainBuilder.topicMessage().get());
        completeFileAndSucceed(topicMessages, recordFile(topicMessages.get(0).getConsensusTimestamp()));
        assertThat(topicMessageLookupRepository.count()).isZero();
    }

    @Test
    void topicMessages() {
        // given, when
        var expected = setupAndProcessTopicMessages();

        // then
        assertThat(topicMessageLookupRepository.findAll()).containsExactlyInAnyOrderElementsOf(expected);
    }

    private void completeFileAndSucceed(List<TopicMessage> topicMessages, RecordFile recordFile) {
        completeFile(topicMessages, recordFile, true);
    }

    private void completeFileAndFail(List<TopicMessage> topicMessages, RecordFile recordFile) {
        completeFile(topicMessages, recordFile, false);
    }

    private void completeFile(List<TopicMessage> topicMessages, RecordFile recordFile, boolean success) {
        transactionTemplate.executeWithoutResult(s -> {
            recordStreamFileListener.onStart();
            topicMessages.forEach(entityListener::onTopicMessage);
            recordStreamFileListener.onEnd(recordFile);

            if (!success) {
                recordStreamFileListener.onError();
                s.setRollbackOnly();
            }
        });
    }

    private RecordFile recordFile(long consensusStart) {
        return domainBuilder
                .recordFile()
                .customize(
                        r -> r.consensusStart(consensusStart).consensusEnd(plus(consensusStart, RECORD_FILE_INTERVAL)))
                .get();
    }

    private List<TopicMessageLookup> setupAndProcessTopicMessages() {
        // given
        var partition = partitions.get(0);
        long timestamp = partition.getTimestampRange().upperEndpoint() - 1000L;
        var topicMessage1 = domainBuilder
                .topicMessage()
                .customize(t -> t.consensusTimestamp(timestamp).sequenceNumber(1))
                .get();
        var topicMessage2 = domainBuilder
                .topicMessage()
                .customize(t ->
                        t.consensusTimestamp(timestamp + 1).sequenceNumber(2).topicId(topicMessage1.getTopicId()))
                .get();
        var topicMessage3 = domainBuilder
                .topicMessage()
                .customize(t -> t.consensusTimestamp(timestamp + 2).sequenceNumber(5))
                .get();
        var topicMessage4 = domainBuilder
                .topicMessage()
                .customize(t ->
                        t.consensusTimestamp(timestamp + 3).sequenceNumber(6).topicId(topicMessage3.getTopicId()))
                .get();

        // when topic messages are processed and the record file closes the partition
        completeFileAndSucceed(
                List.of(topicMessage1, topicMessage2, topicMessage3, topicMessage4), recordFile(timestamp));

        return List.of(
                TestUtils.toTopicMessageLookup(partition.getName(), topicMessage1, topicMessage2),
                TestUtils.toTopicMessageLookup(partition.getName(), topicMessage3, topicMessage4));
    }
}
