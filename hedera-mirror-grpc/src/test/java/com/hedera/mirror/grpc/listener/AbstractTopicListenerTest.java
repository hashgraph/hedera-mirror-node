/*
 * Copyright (C) 2019-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.grpc.listener;

import com.google.common.util.concurrent.Uninterruptibles;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.topic.TopicMessage;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.grpc.GrpcIntegrationTest;
import com.hedera.mirror.grpc.domain.ReactiveDomainBuilder;
import com.hedera.mirror.grpc.domain.TopicMessageFilter;
import jakarta.annotation.Resource;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

public abstract class AbstractTopicListenerTest extends GrpcIntegrationTest {

    protected final long future = DomainUtils.convertToNanosMax(Instant.now().plusSeconds(30L));
    protected final EntityId topicId = EntityId.of(100L);

    @Autowired
    protected ReactiveDomainBuilder domainBuilder;

    @Resource
    protected ListenerProperties listenerProperties;

    @Resource
    protected CompositeTopicListener topicListener;

    private Duration defaultInterval;
    private ListenerProperties.ListenerType defaultType;

    protected abstract ListenerProperties.ListenerType getType();

    @BeforeEach
    void setup() {
        defaultInterval = listenerProperties.getInterval();
        defaultType = listenerProperties.getType();
        listenerProperties.setEnabled(true);
        listenerProperties.setType(getType());
    }

    @AfterEach
    void after() {
        listenerProperties.setEnabled(false);
        listenerProperties.setType(defaultType);
        listenerProperties.setInterval(defaultInterval);
    }

    private void publish(Publisher<TopicMessage> publisher) {
        publish(Flux.from(publisher));
    }

    protected void publish(Flux<TopicMessage> publisher) {
        publisher.blockLast();
    }

    @Test
    void noMessages() {
        TopicMessageFilter filter =
                TopicMessageFilter.builder().startTime(0).topicId(topicId).build();

        topicListener
                .listen(filter)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .expectSubscription()
                .expectTimeout(Duration.ofSeconds(2))
                .verify();
    }

    @Test
    void lessThanPageSize() {
        TopicMessageFilter filter =
                TopicMessageFilter.builder().startTime(0).topicId(topicId).build();

        topicListener
                .listen(filter)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .thenAwait(Duration.ofMillis(250))
                .then(() -> publish(domainBuilder.topicMessages(2, future)))
                .expectNext(1L, 2L)
                .thenCancel()
                .verify(Duration.ofSeconds(2));
    }

    @Test
    void equalPageSize() {
        int maxPageSize = listenerProperties.getMaxPageSize();
        listenerProperties.setMaxPageSize(2);

        TopicMessageFilter filter =
                TopicMessageFilter.builder().startTime(0).topicId(topicId).build();

        topicListener
                .listen(filter)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .thenAwait(Duration.ofMillis(250))
                .then(() -> publish(domainBuilder.topicMessages(2, future)))
                .expectNext(1L, 2L)
                .thenCancel()
                .verify(Duration.ofSeconds(2));

        listenerProperties.setMaxPageSize(maxPageSize);
    }

    @Test
    void greaterThanPageSize() {
        int maxPageSize = listenerProperties.getMaxPageSize();
        listenerProperties.setMaxPageSize(2);

        TopicMessageFilter filter =
                TopicMessageFilter.builder().startTime(0).topicId(topicId).build();

        topicListener
                .listen(filter)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .thenAwait(Duration.ofMillis(250))
                .then(() -> publish(domainBuilder.topicMessages(3, future)))
                .expectNext(1L, 2L, 3L)
                .thenCancel()
                .verify(Duration.ofSeconds(2));

        listenerProperties.setMaxPageSize(maxPageSize);
    }

    @Test
    void startTimeBefore() {
        TopicMessageFilter filter =
                TopicMessageFilter.builder().startTime(0).topicId(topicId).build();

        topicListener
                .listen(filter)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .thenAwait(Duration.ofMillis(250))
                .then(() -> publish(domainBuilder.topicMessages(10, future)))
                .expectNext(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L)
                .thenCancel()
                .verify(Duration.ofSeconds(2));
    }

    @Test
    void startTimeEquals() {
        Mono<TopicMessage> topicMessage = domainBuilder.topicMessage(t -> t.consensusTimestamp(future));
        TopicMessageFilter filter =
                TopicMessageFilter.builder().startTime(future).topicId(topicId).build();

        topicListener
                .listen(filter)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .thenAwait(Duration.ofMillis(250))
                .then(() -> publish(topicMessage))
                .expectNext(1L)
                .thenCancel()
                .verify(Duration.ofSeconds(2));
    }

    @Test
    void startTimeAfter() {
        Mono<TopicMessage> topicMessage = domainBuilder.topicMessage(t -> t.consensusTimestamp(future - 1));
        TopicMessageFilter filter =
                TopicMessageFilter.builder().startTime(future).topicId(topicId).build();

        topicListener
                .listen(filter)
                .as(StepVerifier::create)
                .thenAwait(Duration.ofMillis(250))
                .then(() -> publish(topicMessage))
                .expectNextCount(0)
                .thenCancel()
                .verify(Duration.ofSeconds(2));
    }

    @Test
    void topicId() {
        Flux<TopicMessage> generator = Flux.concat(
                domainBuilder.topicMessage(t -> t.topicId(EntityId.of(1L)).consensusTimestamp(future + 1L)),
                domainBuilder.topicMessage(t -> t.topicId(EntityId.of(2L)).consensusTimestamp(future + 2L)),
                domainBuilder.topicMessage(t -> t.topicId(EntityId.of(3L)).consensusTimestamp(future + 3L)));

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(0)
                .topicId(EntityId.of(2L))
                .build();

        topicListener
                .listen(filter)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .thenAwait(Duration.ofMillis(250))
                .then(() -> publish(generator))
                .expectNext(2L)
                .thenCancel()
                .verify(Duration.ofSeconds(2));
    }

    @Test
    void multipleSubscribers() {
        Flux<TopicMessage> generator = Flux.concat(
                domainBuilder.topicMessage(
                        t -> t.topicId(EntityId.of(1L)).sequenceNumber(1).consensusTimestamp(future + 1L)),
                domainBuilder.topicMessage(
                        t -> t.topicId(EntityId.of(1L)).sequenceNumber(2).consensusTimestamp(future + 2L)),
                domainBuilder.topicMessage(
                        t -> t.topicId(EntityId.of(2L)).sequenceNumber(7).consensusTimestamp(future + 3L)),
                domainBuilder.topicMessage(
                        t -> t.topicId(EntityId.of(2L)).sequenceNumber(8).consensusTimestamp(future + 4L)),
                domainBuilder.topicMessage(
                        t -> t.topicId(EntityId.of(1L)).sequenceNumber(3).consensusTimestamp(future + 5L)));

        TopicMessageFilter filter1 = TopicMessageFilter.builder()
                .startTime(0)
                .topicId(EntityId.of(1L))
                .build();
        TopicMessageFilter filter2 = TopicMessageFilter.builder()
                .startTime(0)
                .topicId(EntityId.of(2L))
                .build();

        StepVerifier stepVerifier1 = topicListener
                .listen(filter1)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .expectNext(1L, 2L, 3L)
                .thenCancel()
                .verifyLater();

        StepVerifier stepVerifier2 = topicListener
                .listen(filter2)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .expectNext(7L, 8L)
                .thenCancel()
                .verifyLater();

        Uninterruptibles.sleepUninterruptibly(50, TimeUnit.MILLISECONDS);
        publish(generator);

        stepVerifier1.verify(Duration.ofSeconds(2));
        stepVerifier2.verify(Duration.ofSeconds(2));

        topicListener
                .listen(filter1)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .as("Verify can still re-subscribe after poller cancelled when no subscriptions")
                .expectNextCount(0)
                .thenCancel()
                .verify(Duration.ofSeconds(2));
    }
}
