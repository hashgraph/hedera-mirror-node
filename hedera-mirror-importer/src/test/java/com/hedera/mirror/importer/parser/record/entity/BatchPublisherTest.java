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
public abstract class BatchPublisherTest extends ImporterIntegrationTest {

    protected final BatchPublisher batchPublisher;
    protected final ParserContext parserContext;
    protected final BatchPublisherProperties properties;

    @BeforeEach
    void setup() {
        properties.setEnabled(true);
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
        parserContext.add(topicMessage1);
        parserContext.add(topicMessage2);

        // then
        StepVerifier.create(topicMessages)
                .thenAwait(Duration.ofMillis(250L))
                .then(() -> batchPublisher.onEnd(null))
                .thenAwait(Duration.ofMillis(250L))
                .expectNext(topicMessage1, topicMessage2)
                .thenCancel()
                .verify(Duration.ofMillis(2000));
    }

    @Test
    void onTopicMessageEmpty() {
        // given
        var topicMessages = subscribe(EntityId.of(1L));

        // when no published messages

        // then
        StepVerifier.create(topicMessages)
                .thenAwait(Duration.ofMillis(250L))
                .then(() -> batchPublisher.onEnd(null))
                .thenAwait(Duration.ofMillis(250L))
                .expectNextCount(0L)
                .thenCancel()
                .verify(Duration.ofMillis(2000));
    }

    @Test
    void onTopicMessageDisabled() {
        // given
        properties.setEnabled(false);
        var topicMessage = domainBuilder.topicMessage().get();
        var topicMessages = subscribe(topicMessage.getTopicId());

        // when
        parserContext.add(topicMessage);

        // then
        StepVerifier.create(topicMessages)
                .thenAwait(Duration.ofMillis(250L))
                .then(() -> batchPublisher.onEnd(null))
                .thenAwait(Duration.ofMillis(250L))
                .expectNextCount(0L)
                .thenCancel()
                .verify(Duration.ofMillis(500));
    }

    protected abstract Flux<TopicMessage> subscribe(EntityId topicId);
}
