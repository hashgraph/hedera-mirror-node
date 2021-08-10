package com.hedera.mirror.monitor.subscribe.grpc;

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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ByteString;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import com.hedera.hashgraph.sdk.proto.Timestamp;
import com.hedera.hashgraph.sdk.proto.TopicID;
import com.hedera.hashgraph.sdk.proto.mirror.ConsensusServiceGrpc;
import com.hedera.hashgraph.sdk.proto.mirror.ConsensusTopicQuery;
import com.hedera.hashgraph.sdk.proto.mirror.ConsensusTopicResponse;
import com.hedera.mirror.monitor.MonitorProperties;
import com.hedera.mirror.monitor.subscribe.SubscribeProperties;
import com.hedera.mirror.monitor.util.Utility;

@Log4j2
class GrpcClientSDKTest {

    private static final Instant START_TIME = Instant.now();

    private ConsensusServiceStub consensusServiceStub;
    private GrpcClientSDK grpcClientSDK;
    private MonitorProperties monitorProperties;
    private GrpcSubscriberProperties properties;
    private Server server;
    private GrpcSubscription subscription;

    @BeforeEach
    void setup(TestInfo testInfo) throws Exception {
        log.info("Executing: {}", testInfo.getDisplayName());
        properties = new GrpcSubscriberProperties();
        properties.setName(testInfo.getDisplayName());
        properties.setTopicId("0.0.1000");
        subscription = new GrpcSubscription(1, properties);
        monitorProperties = new MonitorProperties();
        monitorProperties.getMirrorNode().getGrpc().setHost("in-process:test");
        grpcClientSDK = new GrpcClientSDK(monitorProperties, new SubscribeProperties());

        consensusServiceStub = new ConsensusServiceStub();
        server = InProcessServerBuilder.forName("test")
                .addService(consensusServiceStub)
                .build()
                .start();
    }

    @AfterEach
    void teardown() throws Exception {
        grpcClientSDK.close();
        if (server != null) {
            server.shutdown();
            server.awaitTermination();
        }
    }

    @Test
    void subscribe() {
        consensusServiceStub.setResponses(Flux.just(response(1L), response(2L)));
        grpcClientSDK.subscribe(subscription)
                .as(StepVerifier::create)
                .expectNextCount(2L)
                .expectComplete()
                .verify(Duration.ofSeconds(5L));
        assertThat(subscription)
                .returns(2L, GrpcSubscription::getCount)
                .returns(Map.of(), GrpcSubscription::getErrors)
                .extracting(GrpcSubscription::getStopwatch)
                .matches(s -> !s.isRunning());
        assertThat(subscription.getLast()).get().matches(p -> p.sequenceNumber == 2L);
    }

    @Test
    void multipleSubscriptions() {
        consensusServiceStub.setResponses(Flux.just(response(1L), response(2L)));
        StepVerifier stepVerifier = grpcClientSDK.subscribe(subscription)
                .as(StepVerifier::create)
                .expectNextCount(2L)
                .expectComplete()
                .verifyLater();

        GrpcSubscription subscription2 = new GrpcSubscription(2, properties);
        grpcClientSDK.subscribe(subscription2)
                .as(StepVerifier::create)
                .thenAwait(Duration.ofSeconds(1L))
                .expectNextCount(2L)
                .expectComplete()
                .verify(Duration.ofSeconds(5L));

        stepVerifier.verify(Duration.ofSeconds(2L));

        assertThat(subscription)
                .returns(2L, GrpcSubscription::getCount)
                .returns(Map.of(), GrpcSubscription::getErrors);
        assertThat(subscription2)
                .returns(2L, GrpcSubscription::getCount)
                .returns(Map.of(), GrpcSubscription::getErrors);
    }

    @Test
    void resubscribe() {
        properties.setLimit(2L);
        consensusServiceStub.getRequest().setLimit(2L);
        ConsensusTopicResponse response1 = response(1L);
        consensusServiceStub.setResponses(Flux.just(response1).delayElements(Duration.ofSeconds(1L)));
        grpcClientSDK.subscribe(subscription)
                .as(StepVerifier::create)
                .expectNextCount(1L)
                .expectComplete()
                .verify(Duration.ofSeconds(2L));

        Timestamp consensusTimestamp = response1.getConsensusTimestamp();
        consensusServiceStub.getRequest()
                .setConsensusStartTime(consensusTimestamp.toBuilder().setNanos(consensusTimestamp.getNanos() + 1));
        consensusServiceStub.getRequest().setLimit(1L);
        consensusServiceStub.setResponses(Flux.just(response(2L)));
        grpcClientSDK.subscribe(subscription)
                .as(StepVerifier::create)
                .expectNextCount(1L)
                .expectComplete()
                .verify(Duration.ofSeconds(5L));
        assertThat(subscription)
                .returns(2L, GrpcSubscription::getCount)
                .returns(Map.of(), GrpcSubscription::getErrors);
    }

    @Test
    void missingPublishTimestamp() {
        consensusServiceStub.setResponses(Flux.just(ConsensusTopicResponse.newBuilder().build()));
        grpcClientSDK.subscribe(subscription)
                .as(StepVerifier::create)
                .expectNextCount(1L)
                .expectComplete()
                .verify(Duration.ofSeconds(2L));
    }

    @Test
    void outOfSequence() {
        consensusServiceStub.setResponses(Flux.just(response(1L), response(3L)));
        grpcClientSDK.subscribe(subscription)
                .as(StepVerifier::create)
                .expectNextCount(2L)
                .expectComplete()
                .verify(Duration.ofSeconds(2L));
    }

    @Test
    void error() {
        consensusServiceStub.setResponses(Flux.error(new StatusRuntimeException(Status.NOT_FOUND)));
        grpcClientSDK.subscribe(subscription)
                .as(StepVerifier::create)
                .expectError(StatusRuntimeException.class)
                .verify(Duration.ofSeconds(2L));
        assertThat(subscription)
                .returns(0L, GrpcSubscription::getCount)
                .extracting(GrpcSubscription::getErrors)
                .matches(ms -> ms.get(Status.NOT_FOUND.getCode().toString()) == 1);
    }

    @Test
    void noMessages() {
        consensusServiceStub.setResponses(Flux.empty());
        grpcClientSDK.subscribe(subscription)
                .as(StepVerifier::create)
                .expectComplete()
                .verify(Duration.ofSeconds(2L));
        assertThat(subscription)
                .returns(0L, GrpcSubscription::getCount)
                .returns(Map.of(), GrpcSubscription::getErrors)
                .extracting(GrpcSubscription::getStopwatch)
                .matches(s -> !s.isRunning());
    }

    private ConsensusTopicResponse response(Long sequenceNumber) {
        return ConsensusTopicResponse.newBuilder()
                .setConsensusTimestamp(Timestamp.newBuilder()
                        .setSeconds(START_TIME.plusSeconds(sequenceNumber).getEpochSecond())
                        .setNanos(START_TIME.getNano())
                        .build())
                .setSequenceNumber(sequenceNumber)
                .setMessage(ByteString.copyFrom(Utility.generateMessage(256)))
                .build();
    }

    private Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }

    @Data
    public class ConsensusServiceStub extends ConsensusServiceGrpc.ConsensusServiceImplBase {

        private Flux<ConsensusTopicResponse> responses = Flux.empty();
        private ConsensusTopicQuery.Builder request = ConsensusTopicQuery.newBuilder()
                .setConsensusEndTime(toTimestamp(properties.getEndTime()))
                .setConsensusStartTime(toTimestamp(properties.getStartTime()))
                .setLimit(properties.getLimit())
                .setTopicID(TopicID.newBuilder().setTopicNum(1000).build());

        @Override
        public void subscribeTopic(ConsensusTopicQuery consensusTopicQuery,
                                   StreamObserver<ConsensusTopicResponse> streamObserver) {
            log.debug("subscribeTopic: {}", consensusTopicQuery);
            assertThat(consensusTopicQuery).isEqualTo(request.build());
            responses.delayElements(Duration.ofMillis(200L))
                    .doOnComplete(streamObserver::onCompleted)
                    .doOnError(streamObserver::onError)
                    .doOnNext(streamObserver::onNext)
                    .doOnNext(t -> log.trace("Next: {}", t))
                    .subscribe();
        }
    }
}
