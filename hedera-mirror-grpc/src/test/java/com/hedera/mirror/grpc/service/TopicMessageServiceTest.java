package com.hedera.mirror.grpc.service;

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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import javax.annotation.Resource;
import javax.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import com.hedera.mirror.grpc.GrpcIntegrationTest;
import com.hedera.mirror.grpc.GrpcProperties;
import com.hedera.mirror.grpc.domain.DomainBuilder;
import com.hedera.mirror.grpc.domain.Entity;
import com.hedera.mirror.grpc.domain.EntityType;
import com.hedera.mirror.grpc.domain.TopicMessage;
import com.hedera.mirror.grpc.domain.TopicMessageFilter;
import com.hedera.mirror.grpc.exception.TopicNotFoundException;
import com.hedera.mirror.grpc.listener.SharedPollingTopicListener;
import com.hedera.mirror.grpc.listener.TopicListener;
import com.hedera.mirror.grpc.repository.EntityRepository;
import com.hedera.mirror.grpc.repository.TopicMessageRepository;
import com.hedera.mirror.grpc.retriever.RetrieverProperties;
import com.hedera.mirror.grpc.retriever.TopicMessageRetriever;

public class TopicMessageServiceTest extends GrpcIntegrationTest {

    @Resource
    private TopicMessageService topicMessageService;

    @Resource
    private DomainBuilder domainBuilder;

    @Resource
    private GrpcProperties grpcProperties;

    @Resource
    private RetrieverProperties retrieverProperties;

    @Resource
    private SharedPollingTopicListener sharedPollingTopicListener;

    @BeforeEach
    void setup() {
        sharedPollingTopicListener.init(); // Clear the buffer between runs
        domainBuilder.entity().block();
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
                .startTime(Instant.now())
                .endTime(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();

        assertThatThrownBy(() -> topicMessageService.subscribeTopic(filter))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("End time must be after start time");
    }

    @Test
    void endTimeEqualsStartTime() {
        Instant now = Instant.now();
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
        Instant now = Instant.now();
        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(now.plusSeconds(10L))
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
                .thenCancel()
                .verify(Duration.ofMillis(100));

        grpcProperties.setCheckTopicExists(true);
    }

    @Test
    void invalidTopic() {
        domainBuilder.entity(e -> e.entityTypeId(EntityType.ACCOUNT).entityNum(1L)).block();
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
    void historicalMessagesWithEndTime() {
        TopicMessage topicMessage1 = domainBuilder.topicMessage().block();
        TopicMessage topicMessage2 = domainBuilder.topicMessage().block();
        TopicMessage topicMessage3 = domainBuilder.topicMessage().block();
        TopicMessage topicMessage4 = domainBuilder.topicMessage().block();

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .endTime(topicMessage4.getConsensusTimestamp())
                .build();

        topicMessageService.subscribeTopic(filter)
                .as(StepVerifier::create)
                .expectNext(topicMessage1)
                .expectNext(topicMessage2)
                .expectNext(topicMessage3)
                .thenCancel()
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
                .endTime(topicMessage4.getConsensusTimestamp())
                .build();

        topicMessageService.subscribeTopic(filter)
                .as(StepVerifier::create)
                .expectNext(topicMessage1)
                .expectNext(topicMessage2)
                .expectNext(topicMessage3)
                .thenCancel()
                .verify(Duration.ofMillis(500));

        retrieverProperties.setMaxPageSize(oldMaxPageSize);
    }

    @Test
    void historicalMessagesWithLimit() {
        TopicMessage topicMessage1 = domainBuilder.topicMessage().block();
        TopicMessage topicMessage2 = domainBuilder.topicMessage().block();
        TopicMessage topicMessage3 = domainBuilder.topicMessage().block();

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .limit(2)
                .startTime(Instant.EPOCH)
                .build();

        topicMessageService.subscribeTopic(filter)
                .as(StepVerifier::create)
                .expectNext(topicMessage1)
                .expectNext(topicMessage2)
                .verifyComplete();
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
                .then(() -> domainBuilder.topicMessages(3).blockLast())
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
                .then(() -> domainBuilder.topicMessages(3).blockLast())
                .expectNext(1L, 2L)
                .verifyComplete();
    }

    @Test
    void incomingMessagesWithEndTime() {
        Instant endTime = Instant.now().plusSeconds(10);
        Flux<TopicMessage> generator = Flux.concat(
                domainBuilder.topicMessage(t -> t.consensusTimestamp(endTime.minusNanos(2))),
                domainBuilder.topicMessage(t -> t.consensusTimestamp(endTime.minusNanos(1))),
                domainBuilder.topicMessage(t -> t.consensusTimestamp(endTime))
        );

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .endTime(endTime)
                .build();

        topicMessageService.subscribeTopic(filter)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .thenAwait(Duration.ofMillis(100))
                .then(() -> generator.blockLast())
                .expectNext(1L, 2L)
                .thenCancel()
                .verify(Duration.ofMillis(500));
    }

    @Test
    void bothMessages() {
        TopicMessage topicMessage1 = domainBuilder.topicMessage().block();
        TopicMessage topicMessage2 = domainBuilder.topicMessage().block();
        TopicMessage topicMessage3 = domainBuilder.topicMessage().block();

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .limit(5)
                .startTime(Instant.EPOCH)
                .build();

        topicMessageService.subscribeTopic(filter)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .thenAwait(Duration.ofMillis(100))
                .then(() -> domainBuilder.topicMessages(3).blockLast())
                .expectNext(1L, 2L, 3L, 4L, 5L)
                .verifyComplete();
    }

    @Test
    void bothMessagesWithTopicNum() {
        domainBuilder.entity(e -> e.entityNum(1L)).block();
        domainBuilder.entity(e -> e.entityNum(2L)).block();
        domainBuilder.topicMessage(t -> t.topicNum(0)).block();
        domainBuilder.topicMessage(t -> t.topicNum(1)).block();

        Flux<TopicMessage> generator = Flux.concat(
                domainBuilder.topicMessage(t -> t.topicNum(0)),
                domainBuilder.topicMessage(t -> t.topicNum(1)),
                domainBuilder.topicMessage(t -> t.topicNum(2))
        );

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .topicNum(1)
                .build();

        topicMessageService.subscribeTopic(filter)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .thenAwait(Duration.ofMillis(100))
                .then(() -> generator.blockLast())
                .expectNext(2L, 4L)
                .thenCancel()
                .verify(Duration.ofMillis(500));
    }

    @Test
    void bothMessagesWithRealmNum() {
        domainBuilder.entity(e -> e.entityRealm(1L)).block();
        domainBuilder.entity(e -> e.entityRealm(2L)).block();
        domainBuilder.topicMessage(t -> t.realmNum(0)).block();
        domainBuilder.topicMessage(t -> t.realmNum(1)).block();

        Flux<TopicMessage> generator = Flux.concat(
                domainBuilder.topicMessage(t -> t.realmNum(0)),
                domainBuilder.topicMessage(t -> t.realmNum(1)),
                domainBuilder.topicMessage(t -> t.realmNum(2))
        );

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .realmNum(1)
                .build();

        topicMessageService.subscribeTopic(filter)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .thenAwait(Duration.ofMillis(100))
                .then(() -> generator.blockLast())
                .expectNext(2L, 4L)
                .thenCancel()
                .verify(Duration.ofMillis(500));
    }

    @Test
    void duplicateMessages() {
        Instant now = Instant.now();

        // Timestamps have to be unique in the DB, so just fake sequence numbers
        Flux<TopicMessage> generator = Flux.concat(
                domainBuilder.topicMessage(t -> t.sequenceNumber(1).consensusTimestamp(now)),
                domainBuilder.topicMessage(t -> t.sequenceNumber(1).consensusTimestamp(now.plusSeconds(1))),
                domainBuilder.topicMessage(t -> t.sequenceNumber(2).consensusTimestamp(now.plusSeconds(2))),
                domainBuilder.topicMessage(t -> t.sequenceNumber(1).consensusTimestamp(now.plusSeconds(3)))
        );

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .build();

        topicMessageService.subscribeTopic(filter)
                .map(TopicMessage::getSequenceNumber)
                .as(StepVerifier::create)
                .thenAwait(Duration.ofMillis(100))
                .then(() -> generator.blockLast())
                .expectNext(1L, 2L)
                .thenCancel()
                .verify(Duration.ofMillis(500));
    }

    @Test
    void missingMessages() {
        TopicListener topicListener = Mockito.mock(TopicListener.class);
        EntityRepository entityRepository = Mockito.mock(EntityRepository.class);
        TopicMessageRepository topicMessageRepository = Mockito.mock(TopicMessageRepository.class);
        TopicMessageRetriever topicMessageRetriever = Mockito.mock(TopicMessageRetriever.class);
        topicMessageService = new TopicMessageServiceImpl(new GrpcProperties(), topicListener, entityRepository,
                topicMessageRepository, topicMessageRetriever);

        TopicMessageFilter filter = TopicMessageFilter.builder()
                .startTime(Instant.EPOCH)
                .build();

        TopicMessage beforeMissing = topicMessage(1);
        TopicMessage afterMissing = topicMessage(4);

        Mockito.when(entityRepository.findByCompositeKey(0, filter.getRealmNum(), filter.getTopicNum())).thenReturn(Mono
                .just(Entity.builder().entityTypeId(EntityType.TOPIC).build()));
        Mockito.when(topicMessageRetriever.retrieve(filter)).thenReturn(Flux.empty());
        Mockito.when(topicListener.listen(filter)).thenReturn(Flux.just(beforeMissing, afterMissing));
        Mockito.when(topicMessageRepository.findByFilter(ArgumentMatchers
                .argThat(t -> t.getLimit() == 2 &&
                        t.getStartTime().equals(beforeMissing.getConsensusTimestamp().plusNanos(1)) &&
                        t.getEndTime().equals(afterMissing.getConsensusTimestamp()))))
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

    private TopicMessage topicMessage(long sequenceNumber) {
        return TopicMessage.builder()
                .consensusTimestamp(Instant.EPOCH.plus(sequenceNumber, ChronoUnit.NANOS))
                .realmNum(0)
                .sequenceNumber(sequenceNumber)
                .build();
    }
}
