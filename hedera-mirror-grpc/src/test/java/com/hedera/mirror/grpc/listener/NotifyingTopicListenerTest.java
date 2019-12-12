package com.hedera.mirror.grpc.listener;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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
import org.springframework.data.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import com.hedera.mirror.grpc.GrpcIntegrationTest;
import com.hedera.mirror.grpc.domain.DomainBuilder;
import com.hedera.mirror.grpc.domain.TopicMessage;
import com.hedera.mirror.grpc.domain.TopicMessageFilter;

public class NotifyingTopicListenerTest extends GrpcIntegrationTest {

    @Resource
    private DomainBuilder domainBuilder;

    @Resource
    private TopicListener topicListener;

    @Resource
    private DatabaseClient databaseClient;

    @Test
    void startTimeBefore() {
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .build();

        topicListener.listen(filter)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .thenAwait(Duration.ofMillis(50))
                .then(() -> domainBuilder.topicMessages(10).blockLast())
                .expectNext(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L)
                .thenCancel()
                .verify(Duration.ofMillis(500));
    }

    @Test
    void startTimeEquals() {
        Instant now = Instant.now();
        Mono<TopicMessage> topicMessage = domainBuilder.topicMessage(t -> t.consensusTimestamp(now));
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(now)
                .build();

        topicListener.listen(filter)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .thenAwait(Duration.ofMillis(50))
                .then(() -> topicMessage.block())
                .expectNext(1L)
                .thenCancel()
                .verify(Duration.ofMillis(500));
    }

    @Test
    void startTimeAfter() {
        Instant now = Instant.now();
        Mono<TopicMessage> topicMessage = domainBuilder.topicMessage(t -> t.consensusTimestamp(now.minusNanos(1)));
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(now)
                .build();

        topicListener.listen(filter)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .then(() -> topicMessage.subscribe())
                .expectNoEvent(Duration.ofMillis(50))
                .thenCancel()
                .verify(Duration.ofMillis(500));
    }

    @Test
    void topicNum() {
        Flux<TopicMessage> generator = Flux.concat(
                domainBuilder.topicMessage(t -> t.topicNum(0)),
                domainBuilder.topicMessage(t -> t.topicNum(1)),
                domainBuilder.topicMessage(t -> t.topicNum(2))
        );

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .topicNum(1)
                .build();

        topicListener.listen(filter)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .thenAwait(Duration.ofMillis(50))
                .then(() -> generator.blockLast())
                .expectNext(2L)
                .thenCancel()
                .verify(Duration.ofMillis(500));
    }

    @Test
    void realmNum() {
        Flux<TopicMessage> generator = Flux.concat(
                domainBuilder.topicMessage(t -> t.realmNum(0)),
                domainBuilder.topicMessage(t -> t.realmNum(1)),
                domainBuilder.topicMessage(t -> t.realmNum(2))
        );

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .realmNum(1)
                .build();

        topicListener.listen(filter)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .thenAwait(Duration.ofMillis(50))
                .then(() -> generator.blockLast())
                .expectNext(2L)
                .thenCancel()
                .verify(Duration.ofMillis(500));
    }

    @Test
    void jsonError() {
        Mono<?> generator = databaseClient.execute("NOTIFY topic_message, 'invalid'").fetch().first();

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .build();

        topicListener.listen(filter)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .thenAwait(Duration.ofMillis(50))
                .then(() -> generator.block())
                .expectError(RuntimeException.class)
                .verify(Duration.ofMillis(500));
    }
}
