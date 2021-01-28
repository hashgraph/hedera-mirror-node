package com.hedera.mirror.grpc.jmeter.sampler;

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

import com.google.protobuf.TextFormat;
import com.hederahashgraph.api.proto.java.TopicID;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;

import com.hedera.mirror.api.proto.ConsensusServiceGrpc;
import com.hedera.mirror.api.proto.ConsensusTopicQuery;
import com.hedera.mirror.api.proto.ConsensusTopicResponse;
import com.hedera.mirror.grpc.jmeter.props.MessageListener;
import com.hedera.mirror.grpc.jmeter.sampler.result.HCSDirectStubSamplerResult;
import com.hedera.mirror.grpc.jmeter.sampler.result.HCSSamplerResult;

/**
 * A test client that will make requests of the Consensus service from the Consensus server
 */
@Log4j2
public class HCSDirectStubTopicSampler implements HCSTopicSampler {

    private final ManagedChannel channel;
    private final ConsensusServiceGrpc.ConsensusServiceStub asyncStub;
    private final ConsensusTopicQuery request;
    private boolean canShutdownChannel = true;

    public HCSDirectStubTopicSampler(String host, int port, ConsensusTopicQuery request) {
        this(ManagedChannelBuilder.forAddress(host, port).usePlaintext(), request);
    }

    public HCSDirectStubTopicSampler(ManagedChannelBuilder<?> channelBuilder, ConsensusTopicQuery request) {
        this(channelBuilder.build(), request);
    }

    public HCSDirectStubTopicSampler(ManagedChannel channel, ConsensusTopicQuery request) {
        // prevent channel shutdown in shared case
        canShutdownChannel = false;
        this.channel = channel;
        asyncStub = ConsensusServiceGrpc.newStub(channel);
        this.request = request;
    }

    /**
     * Runs single load test thread by calling gRPC Consensus service subscribeTopic endpoint. Success is dependant on
     * StreamObserver observing the expected count for historic and incoming messages in the allotted time Returns
     * SamplerResult to client
     *
     * @param messageListener listener properties
     * @return Sampler result representing success and observed message counts
     */
    @Override
    public HCSSamplerResult subscribeTopic(MessageListener messageListener) throws InterruptedException {
        log.info("Subscribing to topic with {}, {}", () -> TextFormat.shortDebugString(request), () -> messageListener);

        CountDownLatch historicMessagesLatch = new CountDownLatch(messageListener.getHistoricMessagesCount());
        CountDownLatch incomingMessagesLatch = new CountDownLatch(messageListener.getFutureMessagesCount());
        TopicID topicId = request.getTopicID();
        HCSDirectStubSamplerResult result = HCSDirectStubSamplerResult
                .builder()
                .realmNum(topicId.getRealmNum())
                .topicNum(topicId.getTopicNum())
                .success(true)
                .build();
        StreamObserver<ConsensusTopicResponse> responseObserver = new StreamObserver<>() {

            @Override
            public void onNext(ConsensusTopicResponse response) {
                result.onNext(response, Instant.now());

                if (result.isHistorical()) {
                    historicMessagesLatch.countDown();
                } else {
                    incomingMessagesLatch.countDown();
                }
            }

            @SneakyThrows
            @Override
            public void onError(Throwable t) {
                throw t;
            }

            @Override
            public void onCompleted() {
                result.onComplete();
            }
        };
        ScheduledExecutorService scheduler = null;

        try {
            asyncStub.subscribeTopic(request, responseObserver);
            scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.scheduleAtFixedRate(() -> {
                result.printProgress();
            }, 0, messageListener.getStatusPrintIntervalMinutes(), TimeUnit.MINUTES);

            // await some new messages
            if (!historicMessagesLatch.await(messageListener.getMessagesLatchWaitSeconds(), TimeUnit.SECONDS)) {
                log.error("Historic messages latch count is {}, did not reach zero", historicMessagesLatch.getCount());
                result.setSuccess(false);
            }

            if (historicMessagesLatch.getCount() == 0 && !incomingMessagesLatch
                    .await(messageListener.getMessagesLatchWaitSeconds(), TimeUnit.SECONDS)) {
                log.error("incomingMessagesLatch count is {}, did not reach zero", incomingMessagesLatch.getCount());
                result.setSuccess(false);
            }
        } catch (Exception ex) {
            log.error("Error subscribing to topic", ex);
            throw ex;
        } finally {
            responseObserver.onCompleted();

            if (canShutdownChannel) {
                shutdown();
            }

            scheduler.shutdownNow();

            return result;
        }
    }

    public void shutdown() throws InterruptedException {
        log.debug("Stopping sampler");
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
}
