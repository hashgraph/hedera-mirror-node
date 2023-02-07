package com.hedera.mirror.importer.parser.record.entity;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.topic.TopicMessage;
import com.hedera.mirror.importer.IntegrationTest;

@RequiredArgsConstructor
public abstract class BatchEntityListenerTest extends IntegrationTest {

    protected final BatchEntityListener entityListener;
    protected final EntityListenerProperties properties;

    private long consensusTimestamp = 1;
    private long sequenceNumber = 1;

    @BeforeEach
    void setup() {
        properties.setEnabled(true);
    }

    @Test
    void isEnabled() {
        properties.setEnabled(false);
        assertThat(entityListener.isEnabled()).isFalse();

        properties.setEnabled(true);
        assertThat(entityListener.isEnabled()).isTrue();
    }

    @Test
    void onTopicMessage() throws InterruptedException {
        // given
        TopicMessage topicMessage1 = topicMessage();
        TopicMessage topicMessage2 = topicMessage();
        Flux<TopicMessage> topicMessages = subscribe(topicMessage1.getTopicId().getId());

        // when
        entityListener.onTopicMessage(topicMessage1);
        entityListener.onTopicMessage(topicMessage2);
        entityListener.onSave(new EntityBatchSaveEvent(this));
        entityListener.onCleanup(new EntityBatchCleanupEvent(this));

        // then
        StepVerifier.withVirtualTime(() -> topicMessages)
                .thenAwait(Duration.ofSeconds(10L))
                .expectNext(topicMessage1, topicMessage2)
                .thenCancel()
                .verify(Duration.ofMillis(2000));
    }

    @Test
    void onTopicMessageEmpty() throws InterruptedException {
        // given
        Flux<TopicMessage> topicMessages = subscribe(1);

        // when
        entityListener.onSave(new EntityBatchSaveEvent(this));
        entityListener.onCleanup(new EntityBatchCleanupEvent(this));

        // then
        StepVerifier.withVirtualTime(() -> topicMessages)
                .thenAwait(Duration.ofSeconds(10L))
                .expectNextCount(0L)
                .thenCancel()
                .verify(Duration.ofMillis(500));
    }

    @Test
    void onTopicMessageDisabled() throws InterruptedException {
        // given
        properties.setEnabled(false);
        TopicMessage topicMessage = topicMessage();
        Flux<TopicMessage> topicMessages = subscribe(topicMessage.getTopicId().getId());

        // when
        entityListener.onTopicMessage(topicMessage);
        entityListener.onSave(new EntityBatchSaveEvent(this));
        entityListener.onCleanup(new EntityBatchCleanupEvent(this));

        // then
        StepVerifier.withVirtualTime(() -> topicMessages)
                .thenAwait(Duration.ofSeconds(10L))
                .expectNextCount(0L)
                .thenCancel()
                .verify(Duration.ofMillis(500));
    }

    @Test
    void onCleanup() throws InterruptedException {
        // given
        TopicMessage topicMessage = topicMessage();
        Flux<TopicMessage> topicMessages = subscribe(topicMessage.getTopicId().getId());

        // when
        entityListener.onTopicMessage(topicMessage);
        entityListener.onCleanup(new EntityBatchCleanupEvent(this));
        entityListener.onSave(new EntityBatchSaveEvent(this));

        // then
        StepVerifier.withVirtualTime(() -> topicMessages)
                .thenAwait(Duration.ofSeconds(10L))
                .expectNextCount(0L)
                .thenCancel()
                .verify(Duration.ofMillis(500));
    }

    protected abstract Flux<TopicMessage> subscribe(long topicNum);

    protected TopicMessage topicMessage() {
        TopicMessage topicMessage = new TopicMessage();
        topicMessage.setChunkNum(1);
        topicMessage.setChunkTotal(2);
        topicMessage.setConsensusTimestamp(consensusTimestamp++);
        topicMessage.setMessage("test message".getBytes());
        topicMessage.setPayerAccountId(EntityId.of("0.1.1000", EntityType.ACCOUNT));
        topicMessage.setRunningHash("running hash".getBytes());
        topicMessage.setRunningHashVersion(2);
        topicMessage.setSequenceNumber(sequenceNumber++);
        topicMessage.setTopicId(EntityId.of("0.0.101", EntityType.TOPIC));
        topicMessage.setValidStartTimestamp(4L);
        return topicMessage;
    }
}
