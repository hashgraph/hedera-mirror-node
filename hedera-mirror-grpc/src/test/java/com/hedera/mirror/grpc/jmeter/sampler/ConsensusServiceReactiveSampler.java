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

import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TopicID;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

import com.hedera.mirror.api.proto.ConsensusServiceGrpc;
import com.hedera.mirror.api.proto.ConsensusTopicQuery;
import com.hedera.mirror.api.proto.ConsensusTopicResponse;
import com.hedera.mirror.grpc.converter.InstantToLongConverter;

/**
 * A test client that will make requests of the Consensus service from the Consensus server
 */
@Log4j2
public class ConsensusServiceReactiveSampler {
    private final ManagedChannel channel;
    private final ConsensusServiceGrpc.ConsensusServiceStub asyncStub;
    private final long topicNum;
    private final long realmNum;
    private final long startTimeSecs;
    private final long endTimeSecs;
    private final long limit;

    private final InstantToLongConverter itlc = new InstantToLongConverter();

    private final int threadNum;

    public ConsensusServiceReactiveSampler(String host, int port, long topic, long realm, long startTime,
                                           long endTime, long lmt, int thrdNum) {
        this(ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext(true), topic, realm, startTime, endTime, lmt, thrdNum);
    }

    public ConsensusServiceReactiveSampler(ManagedChannelBuilder<?> channelBuilder, long topic, long realm,
                                           long startTime,
                                           long endTime, long lmt, int thrdNum) {
        channel = channelBuilder.build();
        asyncStub = ConsensusServiceGrpc.newStub(channel);
        topicNum = topic;
        realmNum = realm;
        startTimeSecs = startTime;
        endTimeSecs = endTime;
        limit = lmt;
        threadNum = thrdNum;
    }

    public void shutdown() throws InterruptedException {
        log.info("THRD {} : Managed Channel shutdown called", threadNum);
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    public SamplerResult subscribeTopic(int historicMessagesCount, int futureMessagesCount, Instant observerStart) throws InterruptedException {
        log.info("THRD {} : Running Consensus Client subscribeTopic topicNum : {}, startTimeSecs : {}, endTimeSecs : " +
                        "{},limit :" +
                        " {}, historicMessagesCount : {}, futureMessagesCount : {}, observerStart : {}",
                threadNum, topicNum, startTimeSecs, endTimeSecs, limit, futureMessagesCount, observerStart);

        ConsensusTopicQuery.Builder builder = ConsensusTopicQuery.newBuilder()
                .setLimit(limit)
                .setConsensusStartTime(Timestamp.newBuilder().setSeconds(startTimeSecs).build())
                .setTopicID(TopicID.newBuilder().setRealmNum(realmNum).setTopicNum(topicNum).build());

        if (endTimeSecs != 0) {
            builder.setConsensusEndTime(Timestamp.newBuilder().setSeconds(endTimeSecs).build());
        }

        ConsensusTopicQuery request = builder.build();

        // configure StreamObserver
        int[] topics = {0};
        CountDownLatch historicMessagesLatch = new CountDownLatch(historicMessagesCount);
        CountDownLatch incomingMessagesLatch = new CountDownLatch(futureMessagesCount);
        SamplerResult result = new SamplerResult(topicNum, realmNum);
        boolean awaitHistoricMessages = historicMessagesCount > 0;
        boolean awaitNewMessages = futureMessagesCount > 0;

        StreamObserver<ConsensusTopicResponse> responseObserver = new StreamObserver<>() {

            @Override
            public void onNext(ConsensusTopicResponse response) {
                topics[0]++;
                long responseSequenceNum = response.getSequenceNumber();
                Timestamp responseTimeStamp = response.getConsensusTimestamp();
                Instant respInstant = Instant
                        .ofEpochSecond(responseTimeStamp.getSeconds(), responseTimeStamp.getNanos());
                String messageType = observerStart.isBefore(respInstant) ? "Future" : "Historic";
                log.info("THRD {} : Observed {} ConsensusTopicResponse {}, Time: {}, SeqNum: {}, Message: {}",
                        threadNum, messageType,
                        topics[0], responseTimeStamp, responseSequenceNum, response
                                .getMessage());

                if (messageType.equalsIgnoreCase("Future")) {
                    if (awaitNewMessages) {
                        // decrement latch count
                        incomingMessagesLatch.countDown();
                    }

                    result.incomingMessageCount++;
                } else {
                    if (awaitHistoricMessages) {
                        // decrement latch count
                        historicMessagesLatch.countDown();
                    }

                    result.historicalMessageCount++;
                }
            }

            @Override
            public void onError(Throwable t) {
                log.error(String.format("THRD {} : Error in ConsensusTopicResponse StreamObserver", threadNum), t);
            }

            @Override
            public void onCompleted() {
                log.info("THRD {} : Running responseObserver onCompleted()", threadNum);
            }
        };

        boolean success = true;
        try {
            asyncStub.subscribeTopic(request, responseObserver);

            // await some new messages
            if (!historicMessagesLatch.await(1, TimeUnit.MINUTES)) {
                // latch count wasn't zero
                log.error("THRD {} : historicMessagesLatch count is {}, did not reach zero", threadNum,
                        historicMessagesLatch
                                .getCount());
                result.success = false;
            }

            if (!incomingMessagesLatch.await(1, TimeUnit.MINUTES)) {
                // latch count wasn't zero
                log.error("THRD {} : incomingMessagesLatch count is {}, did not reach zero", threadNum,
                        incomingMessagesLatch
                                .getCount());
                result.success = false;
            }
        } catch (Exception ex) {
            log.warn(String.format("THRD {} : RCP failed", threadNum), ex);
            throw ex;
        } finally {
            responseObserver.onCompleted();
        }

        log.info("THRD {} : Consensus service response observer: {}", threadNum, result);
        return result;
    }

    @ToString
    public class SamplerResult {
        private final long topicNum;
        private final long realmId;
        public boolean success;
        private int historicalMessageCount;
        private int incomingMessageCount;

        public SamplerResult(long topicnm, long realnm) {
            topicNum = topicnm;
            realmId = realnm;
            historicalMessageCount = 0;
            incomingMessageCount = 0;
            success = true;
        }
    }
}
