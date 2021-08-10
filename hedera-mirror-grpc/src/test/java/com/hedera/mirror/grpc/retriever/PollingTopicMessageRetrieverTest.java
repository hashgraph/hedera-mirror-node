package com.hedera.mirror.grpc.retriever;

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

import java.time.Duration;
import java.time.Instant;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import javax.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import com.hedera.mirror.grpc.GrpcIntegrationTest;
import com.hedera.mirror.grpc.domain.DomainBuilder;
import com.hedera.mirror.grpc.domain.TopicMessage;
import com.hedera.mirror.grpc.domain.TopicMessageFilter;

class PollingTopicMessageRetrieverTest extends GrpcIntegrationTest {

    @Resource
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
                .build();

        pollingTopicMessageRetriever.retrieve(filter, throttled)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .expectNextCount(0L)
                .expectComplete()
                .verify(Duration.ofMillis(500));

        retrieverProperties.setEnabled(true);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void noMessages(boolean throttled) {
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .build();

        pollingTopicMessageRetriever.retrieve(filter, throttled)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .expectNextCount(0L)
                .expectComplete()
                .verify(Duration.ofMillis(500));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void lessThanPageSize(boolean throttle) {
        domainBuilder.topicMessage().block();
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .build();

        pollingTopicMessageRetriever.retrieve(filter, throttle)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .expectNext(1L)
                .expectComplete()
                .verify(Duration.ofMillis(500));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void equalPageSize(boolean throttle) {
        int maxPageSize = overrideMaxPageSize(throttle, 2);

        domainBuilder.topicMessage().block();
        domainBuilder.topicMessage().block();

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .build();

        pollingTopicMessageRetriever.retrieve(filter, throttle)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .expectNext(1L, 2L)
                .expectComplete()
                .verify(Duration.ofMillis(500));

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
                .build();

        pollingTopicMessageRetriever.retrieve(filter, throttle)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .expectNext(1L, 2L)
                .expectComplete()
                .verify(Duration.ofMillis(500));

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
                .build();

        pollingTopicMessageRetriever.retrieve(filter, throttle)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .expectNext(1L, 2L, 3L)
                .expectComplete()
                .verify(Duration.ofMillis(500));

        restoreMaxPageSize(throttle, maxPageSize);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void startTimeBefore(boolean throttle) {
        domainBuilder.topicMessages(10, Instant.now()).blockLast();
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .build();

        pollingTopicMessageRetriever.retrieve(filter, throttle)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .expectNext(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L)
                .thenCancel()
                .verify(Duration.ofMillis(1000));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void startTimeEquals(boolean throttle) {
        Instant now = Instant.now();
        domainBuilder.topicMessage(t -> t.consensusTimestamp(now)).block();
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(now)
                .build();

        pollingTopicMessageRetriever.retrieve(filter, throttle)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .expectNext(1L)
                .thenCancel()
                .verify(Duration.ofMillis(500));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void startTimeAfter(boolean throttle) {
        Instant now = Instant.now();
        domainBuilder.topicMessage(t -> t.consensusTimestamp(now.minusNanos(1))).block();
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(now)
                .build();

        pollingTopicMessageRetriever.retrieve(filter, throttle)
                .as(StepVerifier::create)
                .expectNextCount(0)
                .thenCancel()
                .verify(Duration.ofMillis(500));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void topicNum(boolean throttle) {
        domainBuilder.topicMessage(t -> t.topicNum(0)).block();
        domainBuilder.topicMessage(t -> t.topicNum(1)).block();
        domainBuilder.topicMessage(t -> t.topicNum(2)).block();

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .topicNum(1)
                .build();

        pollingTopicMessageRetriever.retrieve(filter, throttle)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .expectNext(2L)
                .thenCancel()
                .verify(Duration.ofMillis(500));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void realmNum(boolean throttle) {
        domainBuilder.topicMessage(t -> t.realmNum(0)).block();
        domainBuilder.topicMessage(t -> t.realmNum(1)).block();
        domainBuilder.topicMessage(t -> t.realmNum(2)).block();

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .realmNum(1)
                .build();

        pollingTopicMessageRetriever.retrieve(filter, throttle)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .expectNext(2L)
                .thenCancel()
                .verify(Duration.ofMillis(500));
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
                .build();

        pollingTopicMessageRetriever.retrieve(filter, throttle)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .thenConsumeWhile(i -> true)
                .expectTimeout(Duration.ofMillis(500))
                .verify();

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
                .build();

        // in unthrottled mode, the retriever should query the db for up to MaxPolls + 1 times when no limit is set,
        // regardless of whether a db query returns less rows than MaxPageSize
        pollingTopicMessageRetriever.retrieve(filter, false)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .thenAwait(Duration.ofMillis(20L))
                .then(firstBatch::blockLast)
                .thenAwait(Duration.ofMillis(20L))
                .then(secondBatch::blockLast)
                .expectNextSequence(LongStream.range(1, 11).boxed().collect(Collectors.toList()))
                .expectComplete()
                .verify(Duration.ofMillis(500));
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
