package com.hedera.mirror.grpc.listener;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

import com.google.common.util.concurrent.Uninterruptibles;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import javax.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import com.hedera.mirror.grpc.GrpcIntegrationTest;
import com.hedera.mirror.grpc.domain.DomainBuilder;
import com.hedera.mirror.grpc.domain.TopicMessage;
import com.hedera.mirror.grpc.domain.TopicMessageFilter;

public abstract class AbstractTopicListenerTest extends GrpcIntegrationTest {

    protected final Instant future = Instant.now().plusSeconds(30L);

    @Resource
    protected DomainBuilder domainBuilder;

    @Resource
    protected ListenerProperties listenerProperties;

    @Resource
    protected CompositeTopicListener topicListener;

    protected abstract ListenerProperties.ListenerType getType();

    private Duration defaultFrequency;

    private ListenerProperties.ListenerType defaultType;

    @BeforeEach
    void setup() {
        defaultFrequency = listenerProperties.getFrequency();
        defaultType = listenerProperties.getType();
        listenerProperties.setEnabled(true);
        listenerProperties.setType(getType());
    }

    @AfterEach
    void after() {
        listenerProperties.setEnabled(false);
        listenerProperties.setType(defaultType);
        listenerProperties.setFrequency(defaultFrequency);
    }

    private void publish(Publisher<TopicMessage> publisher) {
        publish(Flux.from(publisher));
    }

    protected void publish(Flux<TopicMessage> publisher) {
        publisher.blockLast();
    }

    @Test
    void noMessages() {
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .build();

        topicListener.listen(filter)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .expectSubscription()
                .expectTimeout(Duration.ofMillis(500L))
                .verify();
    }

    @Test
    void lessThanPageSize() {
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .build();

        topicListener.listen(filter)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .thenAwait(Duration.ofMillis(50))
                .then(() -> publish(domainBuilder.topicMessages(2, future)))
                .expectNext(1L, 2L)
                .thenCancel()
                .verify(Duration.ofMillis(500));
    }

    @Test
    void equalPageSize() {
        int maxPageSize = listenerProperties.getMaxPageSize();
        listenerProperties.setMaxPageSize(2);

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .build();

        topicListener.listen(filter)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .thenAwait(Duration.ofMillis(50))
                .then(() -> publish(domainBuilder.topicMessages(2, future)))
                .expectNext(1L, 2L)
                .thenCancel()
                .verify(Duration.ofMillis(500));

        listenerProperties.setMaxPageSize(maxPageSize);
    }

    @Test
    void greaterThanPageSize() {
        int maxPageSize = listenerProperties.getMaxPageSize();
        listenerProperties.setMaxPageSize(2);

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .build();

        topicListener.listen(filter)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .thenAwait(Duration.ofMillis(50))
                .then(() -> publish(domainBuilder.topicMessages(3, future)))
                .expectNext(1L, 2L, 3L)
                .thenCancel()
                .verify(Duration.ofMillis(500));

        listenerProperties.setMaxPageSize(maxPageSize);
    }

    @Test
    void startTimeBefore() {
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .build();

        topicListener.listen(filter)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .thenAwait(Duration.ofMillis(50))
                .then(() -> publish(domainBuilder.topicMessages(10, future)))
                .expectNext(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L)
                .thenCancel()
                .verify(Duration.ofMillis(500));
    }

    @Test
    void startTimeEquals() {
        Mono<TopicMessage> topicMessage = domainBuilder.topicMessage(t -> t.consensusTimestamp(future));
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(future)
                .build();

        topicListener.listen(filter)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .thenAwait(Duration.ofMillis(50))
                .then(() -> publish(topicMessage))
                .expectNext(1L)
                .thenCancel()
                .verify(Duration.ofMillis(500));
    }

    @Test
    void startTimeAfter() {
        Mono<TopicMessage> topicMessage = domainBuilder.topicMessage(t -> t.consensusTimestamp(future.minusNanos(1)));
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(future)
                .build();

        topicListener.listen(filter)
                .as(StepVerifier::create)
                .thenAwait(Duration.ofMillis(100))
                .then(() -> publish(topicMessage))
                .expectNextCount(0)
                .thenCancel()
                .verify(Duration.ofMillis(500));
    }

    @Test
    void topicNum() {
        Flux<TopicMessage> generator = Flux.concat(
                domainBuilder.topicMessage(t -> t.topicNum(0).consensusTimestamp(future.plusNanos(1L))),
                domainBuilder.topicMessage(t -> t.topicNum(1).consensusTimestamp(future.plusNanos(2L))),
                domainBuilder.topicMessage(t -> t.topicNum(2).consensusTimestamp(future.plusNanos(3L)))
        );

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .topicNum(1)
                .build();

        topicListener.listen(filter)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .thenAwait(Duration.ofMillis(50))
                .then(() -> publish(generator))
                .expectNext(2L)
                .thenCancel()
                .verify(Duration.ofMillis(500));
    }

    @Test
    void realmNum() {
        Flux<TopicMessage> generator = Flux.concat(
                domainBuilder.topicMessage(t -> t.realmNum(0).consensusTimestamp(future.plusNanos(1L))),
                domainBuilder.topicMessage(t -> t.realmNum(1).consensusTimestamp(future.plusNanos(2L))),
                domainBuilder.topicMessage(t -> t.realmNum(2).consensusTimestamp(future.plusNanos(3L)))
        );

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .realmNum(1)
                .build();

        topicListener.listen(filter)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .thenAwait(Duration.ofMillis(50))
                .then(() -> publish(generator))
                .expectNext(2L)
                .thenCancel()
                .verify(Duration.ofMillis(500));
    }

    @Test
    void multipleSubscribers() {
        // @formatter:off
        Flux<TopicMessage> generator = Flux.concat(
                domainBuilder.topicMessage(t -> t.topicNum(1).sequenceNumber(1).consensusTimestamp(future.plusNanos(1L))),
                domainBuilder.topicMessage(t -> t.topicNum(1).sequenceNumber(2).consensusTimestamp(future.plusNanos(2L))),
                domainBuilder.topicMessage(t -> t.topicNum(2).sequenceNumber(7).consensusTimestamp(future.plusNanos(3L))),
                domainBuilder.topicMessage(t -> t.topicNum(2).sequenceNumber(8).consensusTimestamp(future.plusNanos(4L))),
                domainBuilder.topicMessage(t -> t.topicNum(1).sequenceNumber(3).consensusTimestamp(future.plusNanos(5L)))
        );
        // @formatter:on

        TopicMessageFilter filter1 = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .topicNum(1)
                .build();
        TopicMessageFilter filter2 = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .topicNum(2)
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

        stepVerifier1.verify(Duration.ofMillis(500));
        stepVerifier2.verify(Duration.ofMillis(500));

        topicListener
                .listen(filter1)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .as("Verify can still re-subscribe after poller cancelled when no subscriptions")
                .expectNextCount(0)
                .thenCancel()
                .verify(Duration.ofMillis(100));
    }
}
