/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.parser.record.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.topic.TopicMessage;
import com.hedera.mirror.importer.ImporterIntegrationTest;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@RequiredArgsConstructor
public abstract class BatchEntityListenerTest extends ImporterIntegrationTest {

    protected final BatchEntityListener entityListener;
    protected final EntityListenerProperties properties;

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
    void onTopicMessage() {
        // given
        var topicId = EntityId.of(0L, 0L, 1000L);
        var topicMessage1 =
                domainBuilder.topicMessage().customize(t -> t.topicId(topicId)).get();
        var topicMessage2 =
                domainBuilder.topicMessage().customize(t -> t.topicId(topicId)).get();
        var topicMessages = subscribe(topicId);

        // then
        StepVerifier.withVirtualTime(() -> topicMessages)
                .thenAwait(Duration.ofSeconds(10L))
                .then(() -> {
                    try {
                        // when
                        entityListener.onTopicMessage(topicMessage1);
                        entityListener.onTopicMessage(topicMessage2);
                        entityListener.onSave(new EntityBatchSaveEvent(this));
                        entityListener.onCleanup(new EntityBatchCleanupEvent(this));
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                })
                .thenAwait(Duration.ofSeconds(2L))
                .expectNext(topicMessage1, topicMessage2)
                .thenCancel()
                .verify(Duration.ofMillis(2000));
    }

    @Test
    void onTopicMessageEmpty() throws InterruptedException {
        // given
        var topicMessages = subscribe(EntityId.of(1L));

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
        var topicMessage = domainBuilder.topicMessage().get();
        var topicMessages = subscribe(topicMessage.getTopicId());

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
        var topicMessage = domainBuilder.topicMessage().get();
        var topicMessages = subscribe(topicMessage.getTopicId());

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

    protected abstract Flux<TopicMessage> subscribe(EntityId topicId);
}
