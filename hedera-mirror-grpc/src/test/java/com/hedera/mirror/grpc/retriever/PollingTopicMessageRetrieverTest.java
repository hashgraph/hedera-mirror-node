package com.hedera.mirror.grpc.retriever;

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

import java.time.Duration;
import java.time.Instant;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import javax.annotation.Resource;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import com.hedera.mirror.grpc.GrpcIntegrationTest;
import com.hedera.mirror.grpc.domain.DomainBuilder;
import com.hedera.mirror.grpc.domain.TopicMessage;
import com.hedera.mirror.grpc.domain.TopicMessageFilter;

class PollingTopicMessageRetrieverTest extends GrpcIntegrationTest {

    private static final EntityId TOPIC_ID = EntityId.of(100L, EntityType.TOPIC);
    private static final Duration WAIT = Duration.ofSeconds(10L);

    @Autowired
    private DomainBuilder domainBuilder;

    @Resource
    private PollingTopicMessageRetriever pollingTopicMessageRetriever;

    @Resource
    private RetrieverProperties retrieverProperties;

    private long unthrottledMaxPolls;

    private Duration unthrottledPollingFrequency;

    @BeforeEach
    void setup() {
        unthrottledMaxPolls = retrieverProperties.getUnthrottled().getMaxPolls();
        retrieverProperties.getUnthrottled().setMaxPolls(2);

        unthrottledPollingFrequency = retrieverProperties.getUnthrottled().getPollingFrequency();
        retrieverProperties.getUnthrottled().setPollingFrequency(Duration.ofMillis(5L));
    }

    @AfterEach
    void teardown() {
        retrieverProperties.getUnthrottled().setMaxPolls(unthrottledMaxPolls);
        retrieverProperties.getUnthrottled().setPollingFrequency(unthrottledPollingFrequency);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void notEnabled(boolean throttled) {
        retrieverProperties.setEnabled(false);
        domainBuilder.topicMessage().block();
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .topicId(TOPIC_ID)
                .build();

        StepVerifier.withVirtualTime(() -> pollingTopicMessageRetriever.retrieve(filter, throttled)
                        .map(TopicMessage::getSequenceNumber))
                .thenAwait(WAIT)
                .expectNextCount(0L)
                .expectComplete()
                .verify(WAIT);

        retrieverProperties.setEnabled(true);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void noMessages(boolean throttled) {
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .topicId(TOPIC_ID)
                .build();

        StepVerifier.withVirtualTime(() -> pollingTopicMessageRetriever.retrieve(filter, throttled).map(TopicMessage::getSequenceNumber))
                .thenAwait(WAIT)
                .expectNextCount(0L)
                .expectComplete()
                .verify(WAIT);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void lessThanPageSize(boolean throttle) {
        domainBuilder.topicMessage().block();
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .topicId(TOPIC_ID)
                .build();

        StepVerifier.withVirtualTime(() -> pollingTopicMessageRetriever.retrieve(filter, throttle).map(TopicMessage::getSequenceNumber))
                .thenAwait(WAIT)
                .expectNext(1L)
                .expectComplete()
                .verify(WAIT);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void equalPageSize(boolean throttle) {
        int maxPageSize = overrideMaxPageSize(throttle, 2);

        domainBuilder.topicMessage().block();
        domainBuilder.topicMessage().block();

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .topicId(TOPIC_ID)
                .build();

        StepVerifier.withVirtualTime(() -> pollingTopicMessageRetriever.retrieve(filter, throttle)
                        .map(TopicMessage::getSequenceNumber))
                .thenAwait(WAIT)
                .expectNext(1L, 2L)
                .expectComplete()
                .verify(WAIT);

        restoreMaxPageSize(throttle, maxPageSize);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void limitEqualPageSize(boolean throttle) {
        int maxPageSize = overrideMaxPageSize(throttle, 2);

        domainBuilder.topicMessages(4, Instant.now()).blockLast();

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .limit(2L)
                .topicId(TOPIC_ID)
                .build();

        StepVerifier.withVirtualTime(() -> pollingTopicMessageRetriever.retrieve(filter, throttle)
                        .map(TopicMessage::getSequenceNumber))
                .thenAwait(WAIT)
                .expectNext(1L, 2L)
                .expectComplete()
                .verify(WAIT);

        restoreMaxPageSize(throttle, maxPageSize);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void greaterThanPageSize(boolean throttle) {
        int maxPageSize = overrideMaxPageSize(throttle, 2);

        domainBuilder.topicMessage().block();
        domainBuilder.topicMessage().block();
        domainBuilder.topicMessage().block();

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .topicId(TOPIC_ID)
                .build();

        StepVerifier.withVirtualTime(() -> pollingTopicMessageRetriever.retrieve(filter, throttle)
                        .map(TopicMessage::getSequenceNumber))
                .thenAwait(WAIT)
                .expectNext(1L, 2L, 3L)
                .expectComplete()
                .verify(WAIT);

        restoreMaxPageSize(throttle, maxPageSize);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void startTimeBefore(boolean throttle) {
        domainBuilder.topicMessages(10, Instant.now()).blockLast();
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .topicId(TOPIC_ID)
                .build();

        StepVerifier.withVirtualTime(() -> pollingTopicMessageRetriever.retrieve(filter, throttle)
                        .map(TopicMessage::getSequenceNumber))
                .thenAwait(WAIT)
                .expectNext(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L)
                .thenCancel()
                .verify(WAIT);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void startTimeEquals(boolean throttle) {
        Instant now = Instant.now();
        domainBuilder.topicMessage(t -> t.consensusTimestamp(now)).block();
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(now)
                .topicId(TOPIC_ID)
                .build();

        StepVerifier.withVirtualTime(() -> pollingTopicMessageRetriever.retrieve(filter, throttle)
                        .map(TopicMessage::getSequenceNumber))
                .thenAwait(WAIT)
                .expectNext(1L)
                .thenCancel()
                .verify(WAIT);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void startTimeAfter(boolean throttle) {
        Instant now = Instant.now();
        domainBuilder.topicMessage(t -> t.consensusTimestamp(now.minusNanos(1))).block();
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(now)
                .topicId(TOPIC_ID)
                .build();

        StepVerifier.withVirtualTime(() -> pollingTopicMessageRetriever.retrieve(filter, throttle))
                .thenAwait(WAIT)
                .expectNextCount(0)
                .thenCancel()
                .verify(WAIT);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void topicId(boolean throttle) {
        domainBuilder.topicMessage(t -> t.topicId(0)).block();
        domainBuilder.topicMessage(t -> t.topicId(1)).block();
        domainBuilder.topicMessage(t -> t.topicId(2)).block();

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .topicId(EntityId.of(1L, EntityType.TOPIC))
                .build();

        StepVerifier.withVirtualTime(() -> pollingTopicMessageRetriever.retrieve(filter, throttle)
                        .map(TopicMessage::getSequenceNumber))
                .thenAwait(WAIT)
                .expectNext(2L)
                .thenCancel()
                .verify(WAIT);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void timeout(boolean throttle) {
        int maxPageSize = retrieverProperties.getMaxPageSize();
        Duration timeout = retrieverProperties.getTimeout();
        retrieverProperties.setMaxPageSize(1);
        retrieverProperties.setTimeout(Duration.ofMillis(10));

        domainBuilder.topicMessages(10, Instant.now()).blockLast();
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .topicId(TOPIC_ID)
                .build();

        StepVerifier.withVirtualTime(() -> pollingTopicMessageRetriever.retrieve(filter, throttle)
                        .map(TopicMessage::getSequenceNumber))
                .thenAwait(WAIT)
                .thenConsumeWhile(i -> true)
                .expectTimeout(Duration.ofMillis(500))
                .verify(WAIT);

        retrieverProperties.setMaxPageSize(maxPageSize);
        retrieverProperties.setTimeout(timeout);
    }

    @Test
    void unthrottledShouldKeepPolling() {
        retrieverProperties.getUnthrottled().setMaxPolls(20);

        Instant now = Instant.now();
        Flux<TopicMessage> firstBatch = domainBuilder.topicMessages(5, now);
        Flux<TopicMessage> secondBatch = domainBuilder.topicMessages(5, now.plusNanos(5));
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .topicId(TOPIC_ID)
                .build();

        // in unthrottled mode, the retriever should query the db for up to MaxPolls + 1 times when no limit is set,
        // regardless of whether a db query returns less rows than MaxPageSize
        StepVerifier.withVirtualTime(() -> pollingTopicMessageRetriever.retrieve(filter, false)
                        .map(TopicMessage::getSequenceNumber))
                .then(firstBatch::blockLast)
                .then(secondBatch::blockLast)
                .thenAwait(WAIT)
                .expectNextSequence(LongStream.range(1, 11).boxed().collect(Collectors.toList()))
                .expectComplete()
                .verify(WAIT);
    }

    int overrideMaxPageSize(boolean throttle, int newMaxPageSize) {
        int maxPageSize;

        if (throttle) {
            maxPageSize = retrieverProperties.getMaxPageSize();
            retrieverProperties.setMaxPageSize(newMaxPageSize);
        } else {
            maxPageSize = retrieverProperties.getUnthrottled().getMaxPageSize();
            retrieverProperties.getUnthrottled().setMaxPageSize(newMaxPageSize);
        }

        return maxPageSize;
    }

    void restoreMaxPageSize(boolean throttle, int maxPageSize) {
        if (throttle) {
            retrieverProperties.setMaxPageSize(maxPageSize);
        } else {
            retrieverProperties.getUnthrottled().setMaxPageSize(maxPageSize);
        }
    }
}
