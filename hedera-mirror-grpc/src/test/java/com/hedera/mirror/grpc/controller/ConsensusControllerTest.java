package com.hedera.mirror.grpc.controller;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.grpc.controller.ConsensusController.toResponse;
import static org.assertj.core.api.Assertions.assertThat;

import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TopicID;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.time.Duration;
import javax.annotation.Resource;
import lombok.extern.log4j.Log4j2;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import com.hedera.mirror.api.proto.ConsensusServiceGrpc;
import com.hedera.mirror.api.proto.ConsensusTopicQuery;
import com.hedera.mirror.api.proto.ConsensusTopicResponse;
import com.hedera.mirror.api.proto.ReactorConsensusServiceGrpc;
import com.hedera.mirror.grpc.GrpcIntegrationTest;
import com.hedera.mirror.grpc.domain.DomainBuilder;
import com.hedera.mirror.grpc.domain.TopicMessage;
import com.hedera.mirror.grpc.listener.ListenerProperties;
import com.hedera.mirror.grpc.listener.SharedPollingTopicListener;

@Log4j2
public class ConsensusControllerTest extends GrpcIntegrationTest {
    @GrpcClient("local")
    private ReactorConsensusServiceGrpc.ReactorConsensusServiceStub grpcConsensusService;

    @GrpcClient("local")
    private ConsensusServiceGrpc.ConsensusServiceBlockingStub blockingService;

    @Resource
    private DomainBuilder domainBuilder;

    @Resource
    private ListenerProperties listenerProperties;

    @Resource
    private SharedPollingTopicListener sharedPollingTopicListener;

    @BeforeEach
    void setup() {
        listenerProperties.setEnabled(true);
        domainBuilder.entity().block();
        sharedPollingTopicListener.init();  // Clear the buffer between runs
    }

    @AfterEach
    void after() {
        listenerProperties.setEnabled(false);
    }

    @Test
    void missingTopicID() {
        ConsensusTopicQuery query = ConsensusTopicQuery.newBuilder().build();
        grpcConsensusService.subscribeTopic(Mono.just(query))
                .as(StepVerifier::create)
                .expectErrorSatisfies(t -> assertException(t, Status.Code.INVALID_ARGUMENT, "Missing required topicID"))
                .verify(Duration.ofMillis(500));
    }

    @Test
    void constraintViolationException() {
        ConsensusTopicQuery query = ConsensusTopicQuery.newBuilder()
                .setTopicID(TopicID.newBuilder().build())
                .setLimit(-1)
                .build();

        grpcConsensusService.subscribeTopic(Mono.just(query))
                .as(StepVerifier::create)
                .expectErrorSatisfies(t -> assertException(t, Status.Code.INVALID_ARGUMENT, "limit: must be greater " +
                        "than or equal to 0"))
                .verify(Duration.ofMillis(500));
    }

    @Test
    void subscribeTopicReactive() throws Exception {
        TopicMessage topicMessage1 = domainBuilder.topicMessage().block();
        TopicMessage topicMessage2 = domainBuilder.topicMessage().block();
        TopicMessage topicMessage3 = domainBuilder.topicMessage().block();

        ConsensusTopicQuery query = ConsensusTopicQuery.newBuilder()
                .setLimit(5L)
                .setConsensusStartTime(Timestamp.newBuilder().setSeconds(0).build())
                .setTopicID(TopicID.newBuilder().setRealmNum(0).setTopicNum(0).build())
                .build();

        Flux<TopicMessage> generator = Flux.concat(domainBuilder.topicMessage(), domainBuilder.topicMessage());

        grpcConsensusService.subscribeTopic(Mono.just(query))
                .as(StepVerifier::create)
                .expectNext(toResponse(topicMessage1))
                .expectNext(toResponse(topicMessage2))
                .expectNext(toResponse(topicMessage3))
                .thenAwait(Duration.ofMillis(50))
                .then(() -> generator.blockLast())
                .expectNextCount(2)
                .expectComplete()
                .verify(Duration.ofMillis(500));
    }

    @Test
    void subscribeTopicBlocking() throws Exception {
        TopicMessage topicMessage1 = domainBuilder.topicMessage().block();
        TopicMessage topicMessage2 = domainBuilder.topicMessage().block();
        TopicMessage topicMessage3 = domainBuilder.topicMessage().block();
        ConsensusTopicQuery query = ConsensusTopicQuery.newBuilder()
                .setLimit(3L)
                .setConsensusStartTime(Timestamp.newBuilder().setSeconds(0).build())
                .setTopicID(TopicID.newBuilder().setRealmNum(0).setTopicNum(0).build())
                .build();

        assertThat(blockingService.subscribeTopic(query))
                .toIterable()
                .hasSize(3)
                .containsSequence(toResponse(topicMessage1), toResponse(topicMessage2), toResponse(topicMessage3));
    }

    @Test
    void subscribeTopicQueryPreEpochStartTime() throws Exception {
        TopicMessage topicMessage1 = domainBuilder.topicMessage().block();
        TopicMessage topicMessage2 = domainBuilder.topicMessage().block();
        TopicMessage topicMessage3 = domainBuilder.topicMessage().block();

        ConsensusTopicQuery query = ConsensusTopicQuery.newBuilder()
                .setLimit(5L)
                .setConsensusStartTime(Timestamp.newBuilder().setSeconds(-123).setNanos(-456).build())
                .setTopicID(TopicID.newBuilder().setRealmNum(0).setTopicNum(0).build())
                .build();

        Flux<TopicMessage> generator = Flux.concat(domainBuilder.topicMessage(), domainBuilder.topicMessage());

        grpcConsensusService.subscribeTopic(Mono.just(query))
                .as(StepVerifier::create)
                .expectNext(toResponse(topicMessage1))
                .expectNext(toResponse(topicMessage2))
                .expectNext(toResponse(topicMessage3))
                .thenAwait(Duration.ofMillis(50))
                .then(() -> generator.blockLast())
                .expectNextCount(2)
                .expectComplete()
                .verify(Duration.ofMillis(500));
    }

    @Test
    void subscribeTopicQueryLongOverflowEndTime() throws Exception {
        TopicMessage topicMessage1 = domainBuilder.topicMessage().block();
        TopicMessage topicMessage2 = domainBuilder.topicMessage().block();
        TopicMessage topicMessage3 = domainBuilder.topicMessage().block();

        ConsensusTopicQuery query = ConsensusTopicQuery.newBuilder()
                .setLimit(5L)
                .setConsensusStartTime(Timestamp.newBuilder().setSeconds(1).setNanos(2).build())
                .setConsensusEndTime(Timestamp.newBuilder().setSeconds(31556889864403199L)
                        .setNanos(999999999).build())
                .setTopicID(TopicID.newBuilder().setRealmNum(0).setTopicNum(0).build())
                .build();

        Flux<TopicMessage> generator = Flux.concat(domainBuilder.topicMessage(), domainBuilder.topicMessage());

        grpcConsensusService.subscribeTopic(Mono.just(query))
                .as(StepVerifier::create)
                .expectNext(toResponse(topicMessage1))
                .expectNext(toResponse(topicMessage2))
                .expectNext(toResponse(topicMessage3))
                .thenAwait(Duration.ofMillis(50))
                .then(() -> generator.blockLast())
                .expectNextCount(2)
                .expectComplete()
                .verify(Duration.ofMillis(500));
    }

    @Test
    void subscribeVerifySequence() throws Exception {
        domainBuilder.topicMessage().block();
        domainBuilder.topicMessage().block();
        domainBuilder.topicMessage().block();

        ConsensusTopicQuery query = ConsensusTopicQuery.newBuilder()
                .setLimit(7L)
                .setConsensusStartTime(Timestamp.newBuilder().setSeconds(0).build())
                .setTopicID(TopicID.newBuilder().setRealmNum(0).setTopicNum(0).build())
                .build();

        Flux<TopicMessage> generator = Flux
                .concat(domainBuilder.topicMessage(), domainBuilder.topicMessage(), domainBuilder
                        .topicMessage(), domainBuilder.topicMessage());

        grpcConsensusService.subscribeTopic(Mono.just(query))
                .map(ConsensusTopicResponse::getSequenceNumber)
                .as(StepVerifier::create)
                .expectNext(1L, 2L, 3L)
                .thenAwait(Duration.ofMillis(50))
                .then(() -> generator.blockLast())
                .expectNext(4L, 5L, 6L, 7L)
                .expectComplete()
                .verify(Duration.ofMillis(1000));
    }

    void assertException(Throwable t, Status.Code status, String message) {
        assertThat(t).isNotNull()
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining(message);

        StatusRuntimeException statusRuntimeException = (StatusRuntimeException) t;
        assertThat(statusRuntimeException.getStatus().getCode()).isEqualTo(status);
    }
}
