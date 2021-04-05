package com.hedera.mirror.grpc.service;

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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import javax.annotation.Resource;
import javax.validation.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import com.hedera.mirror.grpc.GrpcIntegrationTest;
import com.hedera.mirror.grpc.GrpcProperties;
import com.hedera.mirror.grpc.domain.DomainBuilder;
import com.hedera.mirror.grpc.domain.Entity;
import com.hedera.mirror.grpc.domain.EntityType;
import com.hedera.mirror.grpc.domain.TopicMessage;
import com.hedera.mirror.grpc.domain.TopicMessageFilter;
import com.hedera.mirror.grpc.exception.TopicNotFoundException;
import com.hedera.mirror.grpc.listener.ListenerProperties;
import com.hedera.mirror.grpc.listener.TopicListener;
import com.hedera.mirror.grpc.repository.EntityRepository;
import com.hedera.mirror.grpc.retriever.RetrieverProperties;
import com.hedera.mirror.grpc.retriever.TopicMessageRetriever;

public class TopicMessageServiceTest extends GrpcIntegrationTest {

    private final Instant now = Instant.now();
    private final Instant future = now.plusSeconds(30L);

    @Resource
    private TopicMessageService topicMessageService;

    @Resource
    private DomainBuilder domainBuilder;

    @Resource
    private GrpcProperties grpcProperties;

    @Resource
    private ListenerProperties listenerProperties;

    @Resource
    private RetrieverProperties retrieverProperties;

    @BeforeEach
    void setup() {
        listenerProperties.setEnabled(true);
        domainBuilder.entity().block();
    }

    @AfterEach
    void after() {
        listenerProperties.setEnabled(false);
    }

    @Test
    void invalidFilter() {
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .realmNum(-1)
                .topicNum(-1)
                .startTime(null)
                .limit(-1)
                .build();

        assertThatThrownBy(() -> topicMessageService.subscribeTopic(filter))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("limit: must be greater than or equal to 0")
                .hasMessageContaining("realmNum: must be greater than or equal to 0")
                .hasMessageContaining("startTime: must not be null")
                .hasMessageContaining("topicNum: must be greater than or equal to 0");
    }

    @Test
    void endTimeBeforeStartTime() {
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(now)
                .endTime(now.minus(1, ChronoUnit.DAYS))
                .build();

        assertThatThrownBy(() -> topicMessageService.subscribeTopic(filter))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("End time must be after start time");
    }

    @Test
    void endTimeEqualsStartTime() {
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(now)
                .endTime(now)
                .build();

        assertThatThrownBy(() -> topicMessageService.subscribeTopic(filter))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("End time must be after start time");
    }

    @Test
    void startTimeAfterNow() {
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(future)
                .build();

        assertThatThrownBy(() -> topicMessageService.subscribeTopic(filter))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("Start time must be before the current time");
    }

    @Test
    void topicNotFound() {
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .topicNum(999)
                .build();

        topicMessageService.subscribeTopic(filter)
                .as(StepVerifier::create)
                .expectError(TopicNotFoundException.class)
                .verify(Duration.ofMillis(100));
    }

    @Test
    void topicNotFoundWithCheckTopicExistsFalse() {
        grpcProperties.setCheckTopicExists(false);
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .topicNum(999)
                .build();

        topicMessageService.subscribeTopic(filter)
                .as(StepVerifier::create)
                .expectSubscription()
                .expectNoEvent(Duration.ofMillis(500))
                .thenCancel()
                .verify(Duration.ofMillis(100));

        grpcProperties.setCheckTopicExists(true);
    }

    @Test
    void invalidTopic() {
        domainBuilder.entity(e -> e.type(EntityType.ACCOUNT).num(1L).id(1L)).block();
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .topicNum(1)
                .build();

        topicMessageService.subscribeTopic(filter)
                .as(StepVerifier::create)
                .expectError(IllegalArgumentException.class)
                .verify(Duration.ofMillis(100));
    }

    @Test
    void noMessages() {
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .topicNum(1)
                .build();

        topicMessageService.subscribeTopic(filter)
                .as(StepVerifier::create)
                .thenAwait(Duration.ofMillis(100))
                .expectNextCount(0L)
                .thenCancel()
                .verify(Duration.ofMillis(500));
    }

    @Test
    void noMessagesWithPastEndTime() {
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .endTime(Instant.EPOCH.plusSeconds(1))
                .build();

        topicMessageService.subscribeTopic(filter)
                .as(StepVerifier::create)
                .expectNextCount(0L)
                .expectComplete()
                .verify(Duration.ofMillis(500));
    }

    @Test
    void noMessagesWithFutureEndTime() {
        Instant endTime = now.plusMillis(250);

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(now)
                .endTime(endTime)
                .build();

        topicMessageService.subscribeTopic(filter)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .expectNextCount(0L)
                .expectComplete()
                .verify(Duration.ofMillis(1000));
    }

    @Test
    void historicalMessages() {
        TopicMessage topicMessage1 = domainBuilder.topicMessage().block();
        TopicMessage topicMessage2 = domainBuilder.topicMessage().block();
        TopicMessage topicMessage3 = domainBuilder.topicMessage().block();

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .build();

        topicMessageService.subscribeTopic(filter)
                .as(StepVerifier::create)
                .expectNext(topicMessage1, topicMessage2, topicMessage3)
                .thenCancel()
                .verify(Duration.ofMillis(500));
    }

    @Test
    void historicalMessagesWithEndTimeAfter() {
        TopicMessage topicMessage1 = domainBuilder.topicMessage().block();
        TopicMessage topicMessage2 = domainBuilder.topicMessage().block();
        TopicMessage topicMessage3 = domainBuilder.topicMessage().block();

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .endTime(topicMessage3.getConsensusTimestampInstant().plusNanos(1))
                .build();

        topicMessageService.subscribeTopic(filter)
                .as(StepVerifier::create)
                .expectNext(topicMessage1, topicMessage2, topicMessage3)
                .expectComplete()
                .verify(Duration.ofMillis(500));
    }

    @Test
    void historicalMessagesWithEndTimeEquals() {
        TopicMessage topicMessage1 = domainBuilder.topicMessage().block();
        TopicMessage topicMessage2 = domainBuilder.topicMessage().block();
        TopicMessage topicMessage3 = domainBuilder.topicMessage().block();
        TopicMessage topicMessage4 = domainBuilder.topicMessage().block();

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .endTime(topicMessage4.getConsensusTimestampInstant())
                .build();

        topicMessageService.subscribeTopic(filter)
                .as(StepVerifier::create)
                .expectNext(topicMessage1, topicMessage2, topicMessage3)
                .expectComplete()
                .verify(Duration.ofMillis(500));
    }

    @Test
    void historicalMessagesWithEndTimeExceedsPageSize() {
        int oldMaxPageSize = retrieverProperties.getMaxPageSize();
        retrieverProperties.setMaxPageSize(1);

        TopicMessage topicMessage1 = domainBuilder.topicMessage().block();
        TopicMessage topicMessage2 = domainBuilder.topicMessage().block();
        TopicMessage topicMessage3 = domainBuilder.topicMessage().block();
        TopicMessage topicMessage4 = domainBuilder.topicMessage().block();

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .endTime(topicMessage4.getConsensusTimestampInstant())
                .build();

        topicMessageService.subscribeTopic(filter)
                .as(StepVerifier::create)
                .expectNext(topicMessage1)
                .expectNext(topicMessage2)
                .expectNext(topicMessage3)
                .expectComplete()
                .verify(Duration.ofMillis(500));

        retrieverProperties.setMaxPageSize(oldMaxPageSize);
    }

    @Test
    void historicalMessagesWithLimit() {
        TopicMessage topicMessage1 = domainBuilder.topicMessage().block();
        TopicMessage topicMessage2 = domainBuilder.topicMessage().block();
        domainBuilder.topicMessage().block();

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .limit(2)
                .startTime(Instant.EPOCH)
                .build();

        topicMessageService.subscribeTopic(filter)
                .as(StepVerifier::create)
                .expectNext(topicMessage1)
                .expectNext(topicMessage2)
                .expectComplete()
                .verify(Duration.ofMillis(500));
    }

    @Test
    void incomingMessages() {
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .build();

        topicMessageService.subscribeTopic(filter)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .thenAwait(Duration.ofMillis(100))
                .then(() -> domainBuilder.topicMessages(3, future).blockLast())
                .expectNext(1L, 2L, 3L)
                .thenCancel()
                .verify(Duration.ofMillis(500));
    }

    @Test
    void incomingMessagesWithLimit() {
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .limit(2)
                .startTime(Instant.EPOCH)
                .build();

        topicMessageService.subscribeTopic(filter)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .thenAwait(Duration.ofMillis(100))
                .then(() -> domainBuilder.topicMessages(3, future).blockLast())
                .expectNext(1L, 2L)
                .expectComplete()
                .verify(Duration.ofMillis(500));
    }

    @Test
    void incomingMessagesWithEndTimeBefore() {
        Instant endTime = Instant.now().plusMillis(500);
        Flux<TopicMessage> generator = domainBuilder.topicMessages(2, endTime.minusNanos(2));

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .endTime(endTime)
                .build();

        topicMessageService.subscribeTopic(filter)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .thenAwait(Duration.ofMillis(50))
                .then(generator::blockLast)
                .expectNext(1L, 2L)
                .expectComplete()
                .verify(Duration.ofMillis(1000));
    }

    @Test
    void incomingMessagesWithEndTimeEquals() {
        Flux<TopicMessage> generator = domainBuilder.topicMessages(3, future.minusNanos(2));

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .endTime(future)
                .build();

        topicMessageService.subscribeTopic(filter)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .thenAwait(Duration.ofMillis(100))
                .then(generator::blockLast)
                .expectNext(1L, 2L)
                .expectComplete()
                .verify(Duration.ofMillis(500));
    }

    @Test
    void bothMessages() {
        domainBuilder.topicMessages(3, now).blockLast();

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .limit(5)
                .startTime(Instant.EPOCH)
                .build();

        topicMessageService.subscribeTopic(filter)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .thenAwait(Duration.ofMillis(100))
                .then(() -> domainBuilder.topicMessages(3, future).blockLast())
                .expectNext(1L, 2L, 3L, 4L, 5L)
                .expectComplete()
                .verify(Duration.ofMillis(500));
    }

    @Test
    void bothMessagesWithTopicNum() {
        domainBuilder.entity(e -> e.num(1L).id(1L)).block();
        domainBuilder.entity(e -> e.num(2L).id(2L)).block();
        domainBuilder.topicMessage(t -> t.topicNum(0).sequenceNumber(1)).block();
        domainBuilder.topicMessage(t -> t.topicNum(1).sequenceNumber(1)).block();

        Flux<TopicMessage> generator = Flux.concat(
                domainBuilder
                        .topicMessage(t -> t.topicNum(0).sequenceNumber(2).consensusTimestamp(future.plusNanos(1))),
                domainBuilder
                        .topicMessage(t -> t.topicNum(1).sequenceNumber(2).consensusTimestamp(future.plusNanos(2))),
                domainBuilder.topicMessage(t -> t.topicNum(2).sequenceNumber(1).consensusTimestamp(future.plusNanos(3)))
        );

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .topicNum(1)
                .build();

        topicMessageService.subscribeTopic(filter)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .thenAwait(Duration.ofMillis(100))
                .then(generator::blockLast)
                .expectNext(1L, 2L)
                .thenCancel()
                .verify(Duration.ofMillis(500));
    }

    @Test
    void bothMessagesWithRealmNum() {
        domainBuilder.entity(e -> e.realm(1L).id(1L)).block();
        domainBuilder.entity(e -> e.realm(2L).id(2L)).block();
        domainBuilder.topicMessage(t -> t.realmNum(0).sequenceNumber(1)).block();
        domainBuilder.topicMessage(t -> t.realmNum(1).sequenceNumber(1)).block();

        Flux<TopicMessage> generator = Flux.concat(
                domainBuilder
                        .topicMessage(t -> t.realmNum(0).sequenceNumber(2).consensusTimestamp(future.plusNanos(1))),
                domainBuilder
                        .topicMessage(t -> t.realmNum(1).sequenceNumber(2).consensusTimestamp(future.plusNanos(2))),
                domainBuilder.topicMessage(t -> t.realmNum(2).sequenceNumber(1).consensusTimestamp(future.plusNanos(3)))
        );

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .realmNum(1)
                .build();

        topicMessageService.subscribeTopic(filter)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .thenAwait(Duration.ofMillis(100))
                .then(generator::blockLast)
                .expectNext(1L, 2L)
                .thenCancel()
                .verify(Duration.ofMillis(500));
    }

    @Test
    void duplicateMessages() {
        TopicListener topicListener = Mockito.mock(TopicListener.class);
        EntityRepository entityRepository = Mockito.mock(EntityRepository.class);
        TopicMessageRetriever topicMessageRetriever = Mockito.mock(TopicMessageRetriever.class);
        topicMessageService = new TopicMessageServiceImpl(new GrpcProperties(), topicListener, entityRepository,
                topicMessageRetriever, new SimpleMeterRegistry());

        TopicMessageFilter retrieverFilter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .build();

        Mockito.when(entityRepository
                .findByCompositeKey(0, retrieverFilter.getRealmNum(), retrieverFilter.getTopicNum()))
                .thenReturn(Optional
                        .of(Entity.builder().type(EntityType.TOPIC).build()));

        Mockito.when(topicMessageRetriever
                .retrieve(ArgumentMatchers.isA(TopicMessageFilter.class), ArgumentMatchers.eq(true)))
                .thenReturn(Flux
                        .just(topicMessage(1, Instant.EPOCH),
                                topicMessage(1, Instant.EPOCH.plus(1, ChronoUnit.NANOS)),
                                topicMessage(2, Instant.EPOCH.plus(2, ChronoUnit.NANOS)),
                                topicMessage(1, Instant.EPOCH.plus(3, ChronoUnit.NANOS))));

        Mockito.when(topicListener.listen(ArgumentMatchers
                .any())).thenReturn(Flux.empty());

        topicMessageService.subscribeTopic(retrieverFilter)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .expectNext(1L, 2L)
                .expectComplete()
                .verify(Duration.ofMillis(700));
    }

    @Test
    void missingMessages() {
        TopicListener topicListener = Mockito.mock(TopicListener.class);
        EntityRepository entityRepository = Mockito.mock(EntityRepository.class);
        TopicMessageRetriever topicMessageRetriever = Mockito.mock(TopicMessageRetriever.class);
        topicMessageService = new TopicMessageServiceImpl(new GrpcProperties(), topicListener, entityRepository,
                topicMessageRetriever, new SimpleMeterRegistry());

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .build();

        TopicMessage beforeMissing = topicMessage(1);
        TopicMessage afterMissing = topicMessage(4);

        Mockito.when(entityRepository.findByCompositeKey(0, filter.getRealmNum(), filter.getTopicNum()))
                .thenReturn(Optional
                        .of(Entity.builder().type(EntityType.TOPIC).build()));
        Mockito.when(topicMessageRetriever.retrieve(ArgumentMatchers.eq(filter), ArgumentMatchers.eq(true)))
                .thenReturn(Flux.empty());
        Mockito.when(topicListener.listen(filter)).thenReturn(Flux.just(beforeMissing, afterMissing));
        Mockito.when(topicMessageRetriever.retrieve(ArgumentMatchers
                        .argThat(t -> t.getLimit() == 2 &&
                                t.getStartTime().equals(beforeMissing.getConsensusTimestampInstant().plusNanos(1)) &&
                                t.getEndTime().equals(afterMissing.getConsensusTimestampInstant())),
                ArgumentMatchers.eq(false)))
                .thenReturn(Flux.just(
                        topicMessage(2),
                        topicMessage(3)
                ));

        topicMessageService.subscribeTopic(filter)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .expectNext(1L, 2L, 3L, 4L)
                .thenCancel()
                .verify(Duration.ofMillis(700));
    }

    @Test
    void missingMessagesFromListenerAllRetrieved() {
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .build();

        missingMessagesFromListenerTest(filter, Flux.just(topicMessage(5), topicMessage(6), topicMessage(7)));

        topicMessageService.subscribeTopic(filter)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .expectNext(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L)
                .thenCancel()
                .verify(Duration.ofMillis(700));
    }

    @Test
    void missingMessagesFromListenerSomeRetrieved() {
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .build();

        missingMessagesFromListenerTest(filter, Flux.just(topicMessage(5), topicMessage(6)));

        topicMessageService.subscribeTopic(filter)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .expectNext(1L, 2L, 3L, 4L, 5L, 6L)
                .expectError(IllegalStateException.class)
                .verify(Duration.ofMillis(700));
    }

    @Test
    void missingMessagesFromListenerNoneRetrieved() {
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .build();

        missingMessagesFromListenerTest(filter, Flux.empty());

        topicMessageService.subscribeTopic(filter)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .expectNext(1L, 2L, 3L, 4L)
                .expectError(IllegalStateException.class)
                .verify(Duration.ofMillis(700));
    }

    @Test
    void missingMessagesFromRetrieverAndListener() {
        TopicListener topicListener = Mockito.mock(TopicListener.class);
        EntityRepository entityRepository = Mockito.mock(EntityRepository.class);
        TopicMessageRetriever topicMessageRetriever = Mockito.mock(TopicMessageRetriever.class);
        topicMessageService = new TopicMessageServiceImpl(new GrpcProperties(), topicListener, entityRepository,
                topicMessageRetriever, new SimpleMeterRegistry());

        TopicMessageFilter retrieverFilter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .build();

        TopicMessage retrieved1 = topicMessage(1);
        TopicMessage retrieved2 = topicMessage(2);

        TopicMessage beforeMissing1 = topicMessage(3);
        TopicMessage beforeMissing2 = topicMessage(4);
        TopicMessage afterMissing1 = topicMessage(8);
        TopicMessage afterMissing2 = topicMessage(9);
        TopicMessage afterMissing3 = topicMessage(10);

        Mockito.when(entityRepository
                .findByCompositeKey(0, retrieverFilter.getRealmNum(), retrieverFilter.getTopicNum()))
                .thenReturn(Optional
                        .of(Entity.builder().type(EntityType.TOPIC).build()));

        TopicMessageFilter listenerFilter = TopicMessageFilter.builder()
                .startTime(retrieved2.getConsensusTimestampInstant())
                .build();

        Mockito.when(topicListener
                .listen(ArgumentMatchers.argThat(l -> l.getStartTime().equals(listenerFilter.getStartTime()))))
                .thenReturn(Flux.just(beforeMissing1, beforeMissing2, afterMissing1, afterMissing2, afterMissing3));

        Mockito.when(topicMessageRetriever
                .retrieve(ArgumentMatchers.isA(TopicMessageFilter.class), ArgumentMatchers.eq(true)))
                .thenReturn(
                        Flux.just(retrieved1)
                );
        Mockito.when(topicMessageRetriever
                .retrieve(ArgumentMatchers.isA(TopicMessageFilter.class), ArgumentMatchers.eq(false)))
                .thenReturn(
                        Flux.just(retrieved2), // missing historic
                        Flux.just(
                                topicMessage(5), // missing incoming
                                topicMessage(6),
                                topicMessage(7)
                        ));

        topicMessageService.subscribeTopic(retrieverFilter)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .expectNext(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L)
                .expectComplete()
                .verify(Duration.ofMillis(700));
    }

    private void missingMessagesFromListenerTest(TopicMessageFilter filter, Flux<TopicMessage> missingMessages) {
        TopicListener topicListener = Mockito.mock(TopicListener.class);
        EntityRepository entityRepository = Mockito.mock(EntityRepository.class);
        TopicMessageRetriever topicMessageRetriever = Mockito.mock(TopicMessageRetriever.class);
        topicMessageService = new TopicMessageServiceImpl(new GrpcProperties(), topicListener, entityRepository,
                topicMessageRetriever, new SimpleMeterRegistry());

        // historic messages
        TopicMessage retrieved1 = topicMessage(1);
        TopicMessage retrieved2 = topicMessage(2);

        // incoming messages before gap
        TopicMessage beforeMissing1 = topicMessage(3);
        TopicMessage beforeMissing2 = topicMessage(4);

        // incoming messages after gap
        TopicMessage afterMissing1 = topicMessage(8);
        TopicMessage afterMissing2 = topicMessage(9);
        TopicMessage afterMissing3 = topicMessage(10);

        // mock entity type check
        Mockito.when(entityRepository
                .findByCompositeKey(0, filter.getRealmNum(), filter.getTopicNum()))
                .thenReturn(Optional
                        .of(Entity.builder().type(EntityType.TOPIC).build()));
        Mockito.when(topicMessageRetriever.retrieve(ArgumentMatchers.eq(filter), ArgumentMatchers.eq(true)))
                .thenReturn(Flux.just(retrieved1, retrieved2));

        TopicMessageFilter listenerFilter = TopicMessageFilter.builder()
                .startTime(beforeMissing1.getConsensusTimestampInstant())
                .build();

        Mockito.when(topicListener
                .listen(ArgumentMatchers.argThat(l -> l.getStartTime().equals(listenerFilter.getStartTime()))))
                .thenReturn(Flux.just(beforeMissing1, beforeMissing2, afterMissing1, afterMissing2, afterMissing3));
        Mockito.when(topicMessageRetriever.retrieve(ArgumentMatchers
                        .argThat(t -> t.getLimit() == 3 &&
                                t.getStartTime().equals(beforeMissing2.getConsensusTimestampInstant().plusNanos(1)) &&
                                t.getEndTime().equals(afterMissing1.getConsensusTimestampInstant())),
                ArgumentMatchers.eq(false)))
                .thenReturn(missingMessages);
    }

    private TopicMessage topicMessage(long sequenceNumber) {
        return topicMessage(sequenceNumber, Instant.EPOCH.plus(sequenceNumber, ChronoUnit.NANOS));
    }

    private TopicMessage topicMessage(long sequenceNumber, Instant consensusTimestamp) {
        return TopicMessage.builder()
                .consensusTimestamp(consensusTimestamp)
                .realmNum(0)
                .sequenceNumber(sequenceNumber)
                .message(new byte[] {0, 1, 2})
                .runningHash(new byte[] {3, 4, 5})
                .topicNum(0)
                .runningHashVersion(2)
                .build();
    }
}
