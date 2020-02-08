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
import com.google.protobuf.TextFormat;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TopicID;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;

import com.hedera.mirror.api.proto.ConsensusServiceGrpc;
import com.hedera.mirror.api.proto.ConsensusTopicQuery;
import com.hedera.mirror.api.proto.ConsensusTopicResponse;
import com.hedera.mirror.grpc.jmeter.props.MessageListener;

/**
 * A test client that will make requests of the Consensus service from the Consensus server
 */
@Log4j2
public class HCSTopicSampler {

    private final ManagedChannel channel;
    private final ConsensusServiceGrpc.ConsensusServiceStub asyncStub;
    private final ConsensusTopicQuery request;

    public HCSTopicSampler(String host, int port, ConsensusTopicQuery request) {
        this(ManagedChannelBuilder.forAddress(host, port).usePlaintext(true), request);
    }

    public HCSTopicSampler(ManagedChannelBuilder<?> channelBuilder, ConsensusTopicQuery request) {
        channel = channelBuilder.build();
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
    public SamplerResult subscribeTopic(MessageListener messageListener) throws InterruptedException {
        log.info("Subscribing to topic with {}, {}", () -> TextFormat.shortDebugString(request), () -> messageListener);

        CountDownLatch historicMessagesLatch = new CountDownLatch(messageListener.getHistoricMessagesCount());
        CountDownLatch incomingMessagesLatch = new CountDownLatch(messageListener.getFutureMessagesCount());
        TopicID topicId = request.getTopicID();
        SamplerResult result = new SamplerResult(topicId.getRealmNum(), topicId.getTopicNum());
        StreamObserver<ConsensusTopicResponse> responseObserver = new StreamObserver<>() {

            @Override
            public void onNext(ConsensusTopicResponse response) {
                result.onNext(response);

                if (result.isHistorical()) {
                    historicMessagesLatch.countDown();
                } else {
                    incomingMessagesLatch.countDown();
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
                log.info("Observed {} historic and {} incoming messages in {} ({}/s): {}", result
                        .getHistoricalMessageCount(), result.getIncomingMessageCount(), result.getStopwatch(), result
                        .getMessageRate(), result.isSuccess() ? "success" : "failed");
            }
        };

        try {
            asyncStub.subscribeTopic(request, responseObserver);

            // await some new messages
            if (!historicMessagesLatch.await(messageListener.getMessagesLatchWaitSeconds(), TimeUnit.SECONDS)) {
                log.error("Historic messages latch count is {}, did not reach zero", historicMessagesLatch.getCount());
                result.success = false;
            }

            log.info("{} Historic messages obtained in {} ({}/s)", result.getTotalMessageCount(), result
                    .getStopwatch(), result.getMessageRate());

            if (historicMessagesLatch.getCount() == 0 && !incomingMessagesLatch
                    .await(messageListener.getMessagesLatchWaitSeconds(), TimeUnit.SECONDS)) {
                log.error("incomingMessagesLatch count is {}, did not reach zero", incomingMessagesLatch.getCount());
                result.success = false;
            }
        } catch (Exception ex) {
            log.error("Error subscribing to topic", ex);
            throw ex;
        } finally {
            responseObserver.onCompleted();
            shutdown();
            return result;
        }
    }

    public void shutdown() throws InterruptedException {
        log.debug("Stopping sampler");
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    private Instant toInstant(Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }

    @Data
    public class SamplerResult {

        private final long realmNum;
        private final long topicNum;
        private final Stopwatch stopwatch = Stopwatch.createStarted();

        private long historicalMessageCount = 0L;
        private long incomingMessageCount = 0L;
        private boolean success = true;
        private ConsensusTopicResponse last;
        private boolean historical = true;

        public long getTotalMessageCount() {
            return historicalMessageCount + incomingMessageCount;
        }

        public long getMessageRate() {
            long seconds = stopwatch.elapsed(TimeUnit.SECONDS);
            return seconds > 0 ? getTotalMessageCount() / seconds : 0;
        }

        public void onNext(ConsensusTopicResponse response) {
            log.trace("Observed {}", () -> TextFormat.shortDebugString(response));
            Instant currentTime = toInstant(response.getConsensusTimestamp());

            if (last != null) {
                if (response.getSequenceNumber() != last.getSequenceNumber() + 1) {
                    throw new IllegalArgumentException("Out of order message sequence. Expected " + (last
                            .getSequenceNumber() + 1) + " got " + response.getSequenceNumber());
                }

                Instant lastTime = toInstant(last.getConsensusTimestamp());
                if (!currentTime.isAfter(lastTime)) {
                    throw new IllegalArgumentException("Out of order message timestamp. Expected " + currentTime +
                            " to be after " + lastTime);
                }
            }

            if (currentTime.isBefore(TopicMessageGeneratorSampler.INCOMING_START)) {
                ++historicalMessageCount;
            } else {
                historical = false;
                ++incomingMessageCount;
            }

            last = response;
        }
    }
}
