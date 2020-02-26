package com.hedera.mirror.grpc.retriever;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2020 Hedera Hashgraph, LLC
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
import javax.annotation.Resource;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import com.hedera.mirror.grpc.GrpcIntegrationTest;
import com.hedera.mirror.grpc.domain.DomainBuilder;
import com.hedera.mirror.grpc.domain.TopicMessage;
import com.hedera.mirror.grpc.domain.TopicMessageFilter;

public class PollingTopicMessageRetrieverTest extends GrpcIntegrationTest {

    @Resource
    private DomainBuilder domainBuilder;

    @Resource
    private PollingTopicMessageRetriever pollingTopicMessageRetriever;

    @Resource
    private RetrieverProperties retrieverProperties;

    @Test
    void notEnabled() {
        retrieverProperties.setEnabled(false);
        domainBuilder.topicMessage().block();
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .build();

        pollingTopicMessageRetriever.retrieve(filter)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .expectNextCount(0L)
                .expectComplete()
                .verify(Duration.ofMillis(500));

        retrieverProperties.setEnabled(true);
    }

    @Test
    void noMessages() {
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .build();

        pollingTopicMessageRetriever.retrieve(filter)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .expectNextCount(0L)
                .expectComplete()
                .verify(Duration.ofMillis(500));
    }

    @Test
    void lessThanPageSize() {
        domainBuilder.topicMessage().block();
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .build();

        pollingTopicMessageRetriever.retrieve(filter)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .expectNext(1L)
                .expectComplete()
                .verify(Duration.ofMillis(500));
    }

    @Test
    void equalPageSize() {
        int maxPageSize = retrieverProperties.getMaxPageSize();
        retrieverProperties.setMaxPageSize(2);
        domainBuilder.topicMessage().block();
        domainBuilder.topicMessage().block();

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .build();

        pollingTopicMessageRetriever.retrieve(filter)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .expectNext(1L, 2L)
                .expectComplete()
                .verify(Duration.ofMillis(500));

        retrieverProperties.setMaxPageSize(maxPageSize);
    }

    @Test
    void greaterThanPageSize() {
        int maxPageSize = retrieverProperties.getMaxPageSize();
        retrieverProperties.setMaxPageSize(2);
        domainBuilder.topicMessage().block();
        domainBuilder.topicMessage().block();
        domainBuilder.topicMessage().block();

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .build();

        pollingTopicMessageRetriever.retrieve(filter)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .expectNext(1L, 2L, 3L)
                .expectComplete()
                .verify(Duration.ofMillis(500));

        retrieverProperties.setMaxPageSize(maxPageSize);
    }

    @Test
    void startTimeBefore() {
        domainBuilder.topicMessages(10).blockLast();
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .build();

        pollingTopicMessageRetriever.retrieve(filter)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .expectNext(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L)
                .thenCancel()
                .verify(Duration.ofMillis(500));
    }

    @Test
    void startTimeEquals() {
        Instant now = Instant.now();
        domainBuilder.topicMessage(t -> t.consensusTimestamp(now)).block();
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(now)
                .build();

        pollingTopicMessageRetriever.retrieve(filter)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .expectNext(1L)
                .thenCancel()
                .verify(Duration.ofMillis(500));
    }

    @Test
    void startTimeAfter() {
        Instant now = Instant.now();
        domainBuilder.topicMessage(t -> t.consensusTimestamp(now.minusNanos(1))).block();
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(now)
                .build();

        pollingTopicMessageRetriever.retrieve(filter)
                .as(StepVerifier::create)
                .expectNextCount(0)
                .thenCancel()
                .verify(Duration.ofMillis(500));
    }

    @Test
    void topicNum() {
        domainBuilder.topicMessage(t -> t.topicNum(0)).block();
        domainBuilder.topicMessage(t -> t.topicNum(1)).block();
        domainBuilder.topicMessage(t -> t.topicNum(2)).block();

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .topicNum(1)
                .build();

        pollingTopicMessageRetriever.retrieve(filter)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .expectNext(2L)
                .thenCancel()
                .verify(Duration.ofMillis(500));
    }

    @Test
    void realmNum() {
        domainBuilder.topicMessage(t -> t.realmNum(0)).block();
        domainBuilder.topicMessage(t -> t.realmNum(1)).block();
        domainBuilder.topicMessage(t -> t.realmNum(2)).block();

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .realmNum(1)
                .build();

        pollingTopicMessageRetriever.retrieve(filter)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .expectNext(2L)
                .thenCancel()
                .verify(Duration.ofMillis(500));
    }

    @Test
    void timeout() {
        int maxPageSize = retrieverProperties.getMaxPageSize();
        Duration timeout = retrieverProperties.getTimeout();
        retrieverProperties.setMaxPageSize(1);
        retrieverProperties.setTimeout(Duration.ofMillis(10));

        domainBuilder.topicMessages(10).blockLast();
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .build();

        pollingTopicMessageRetriever.retrieve(filter)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .thenConsumeWhile(i -> true)
                .expectTimeout(Duration.ofMillis(500))
                .verify();

        retrieverProperties.setMaxPageSize(maxPageSize);
        retrieverProperties.setTimeout(timeout);
    }
}
