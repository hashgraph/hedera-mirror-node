package com.hedera.mirror.grpc.jmeter.sampler;

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

import com.google.common.base.Stopwatch;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TopicID;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.SneakyThrows;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

import com.hedera.mirror.api.proto.ConsensusServiceGrpc;
import com.hedera.mirror.api.proto.ConsensusTopicQuery;
import com.hedera.mirror.api.proto.ConsensusTopicResponse;
import com.hedera.mirror.grpc.jmeter.props.MessageListener;

/**
 * A test client that will make requests of the Consensus service from the Consensus server
 */
@Log4j2
public class ConsensusServiceReactiveSampler {
    private final ManagedChannel channel;
    private final ConsensusServiceGrpc.ConsensusServiceStub asyncStub;
    private final ConsensusTopicQuery request;

    public ConsensusServiceReactiveSampler(String host, int port, ConsensusTopicQuery request) {
        this(ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext(true), request);
    }

    public ConsensusServiceReactiveSampler(ManagedChannelBuilder<?> channelBuilder, ConsensusTopicQuery request) {
        channel = channelBuilder.build();
        asyncStub = ConsensusServiceGrpc.newStub(channel);
        this.request = request;
    }

    public void shutdown() throws InterruptedException {
        log.info("Managed Channel shutdown called");
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    /**
     * Runs single load test thread by calling gRPC Consensus service subscribeTopic endpoint. Success is dependant on
     * StreamObserver observing the expected count for historic and incoming messages in the allotted time Returns
     * SamplerResult to client
     *
     * @param messageListener listener properties
     * @return Sampler result representing success and observed message counts
     */
    public SamplerResult subscribeTopic(MessageListener messageListener) throws InterruptedException {
        log.info("Running Consensus Client subscribeRequest : {}, messageListener : {}", request.toString()
                .replaceAll("\n", ""), messageListener);

        // configure StreamObserver
        Instant observerStart = Instant.parse("2020-01-01T00:00:00.00Z");
        int[] topics = {0};
        CountDownLatch historicMessagesLatch = new CountDownLatch(messageListener.getHistoricMessagesCount());
        CountDownLatch incomingMessagesLatch = new CountDownLatch(messageListener.getFutureMessagesCount());
        TopicID topicId = request.getTopicID();
        SamplerResult result = new SamplerResult(topicId.getTopicNum(), topicId.getRealmNum());
        boolean awaitHistoricMessages = historicMessagesLatch.getCount() > 0;
        boolean awaitNewMessages = incomingMessagesLatch.getCount() > 0;
        ConsensusTopicResponse[] lastresponse = {null};
        StreamObserver<ConsensusTopicResponse> responseObserver = new StreamObserver<>() {

            @Override
            public void onNext(ConsensusTopicResponse response) {
                topics[0]++;
                long responseSequenceNum = response.getSequenceNumber();
                Timestamp responseTimeStamp = response.getConsensusTimestamp();
                Instant respInstant = Instant
                        .ofEpochSecond(responseTimeStamp.getSeconds(), responseTimeStamp.getNanos());
                String messageType = observerStart.isBefore(respInstant) ? "Future" : "Historic";
                log.trace("Observed {} ConsensusTopicResponse {}, Time: {}, SeqNum: {}, Message: {}", messageType,
                        topics[0], responseTimeStamp, responseSequenceNum, response
                                .getMessage());

                if (lastresponse[0] != null) {
                    Instant lastRespInstant = Instant
                            .ofEpochSecond(lastresponse[0].getConsensusTimestamp().getSeconds(), lastresponse[0]
                                    .getConsensusTimestamp().getNanos());
                    if (lastresponse[0].getSequenceNumber() > responseSequenceNum || lastRespInstant
                            .isAfter(respInstant)) {
                        log.error("Last response has a consensus timestamp or sequence num greater than current " +
                                "response. Last: {}, current: {}", lastresponse[0], response);
                    }
                }
                lastresponse[0] = response;

                if (messageType.equalsIgnoreCase("Future")) {
                    if (awaitNewMessages) {
                        // decrement latch count
                        incomingMessagesLatch.countDown();
                    }

                    result.incomingMessageCount.incrementAndGet();
                } else {
                    if (awaitHistoricMessages) {
                        // decrement latch count
                        historicMessagesLatch.countDown();
                    }

                    result.historicalMessageCount.incrementAndGet();
                }
            }

            @SneakyThrows
            @Override
            public void onError(Throwable t) {
                log.error("Error in ConsensusTopicResponse StreamObserver", t);
                throw t;
            }

            @Override
            public void onCompleted() {
                log.info("Running responseObserver onCompleted(). Observed {} historic and {} incoming messages",
                        result.historicalMessageCount, result.incomingMessageCount);
            }
        };

        Stopwatch messageStopwatch = Stopwatch.createStarted();
        try {
            asyncStub.subscribeTopic(request, responseObserver);

            // await some new messages
            if (!historicMessagesLatch.await(messageListener.getMessagesLatchWaitSeconds(), TimeUnit.SECONDS)) {
                log.error("Historic messages latch count is {}, did not reach zero", historicMessagesLatch.getCount());
                result.success = false;
            }
            log.info("{} Historic messages obtained in {}", result.historicalMessageCount, messageStopwatch);

            if (historicMessagesLatch.getCount() == 0 && !incomingMessagesLatch
                    .await(messageListener.getMessagesLatchWaitSeconds(), TimeUnit.SECONDS)) {
                log.error("incomingMessagesLatch count is {}, did not reach zero", incomingMessagesLatch.getCount());
                result.success = false;
            }
        } catch (Exception ex) {
            log.warn(String.format("RCP failed"), ex);
            throw ex;
        } finally {
            log.info("{} total messages obtained in {}", result.getTotalMessageCount(), messageStopwatch);

            responseObserver.onCompleted();
            shutdown();

            log.info("Consensus service response observer: {}", result);
            return result;
        }
    }

    @ToString
    public class SamplerResult {
        private final long topicNum;
        private final long realmId;
        private final AtomicInteger historicalMessageCount;
        private final AtomicInteger incomingMessageCount;
        public boolean success;

        public SamplerResult(long topicnm, long realnm) {
            topicNum = topicnm;
            realmId = realnm;
            historicalMessageCount = new AtomicInteger(0);
            incomingMessageCount = new AtomicInteger(0);
            success = true;
        }

        public int getTotalMessageCount() {
            return historicalMessageCount.get() + incomingMessageCount.get();
        }
    }
}
