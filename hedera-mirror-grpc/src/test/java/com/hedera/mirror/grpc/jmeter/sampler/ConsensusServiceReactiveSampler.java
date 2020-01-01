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
import com.hedera.mirror.grpc.jmeter.ConnectionHandler;

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

    private final ConnectionHandler connectionHandler;

    private final InstantToLongConverter itlc = new InstantToLongConverter();

    public ConsensusServiceReactiveSampler(String host, int port, long topic, long realm, long startTime,
                                           long endTime, long lmt, ConnectionHandler connHandl) {
        this(ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext(true), topic, realm, startTime, endTime, lmt, connHandl);
    }

    public ConsensusServiceReactiveSampler(ManagedChannelBuilder<?> channelBuilder, long topic, long realm,
                                           long startTime,
                                           long endTime, long lmt, ConnectionHandler connHandl) {
        channel = channelBuilder.build();
        asyncStub = ConsensusServiceGrpc.newStub(channel);
        topicNum = topic;
        realmNum = realm;
        startTimeSecs = startTime;
        endTimeSecs = endTime;
        limit = lmt;

        connectionHandler = connHandl;
    }

    public void shutdown() throws InterruptedException {
        log.info("Managed Channel shutdown called");
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    public String subscribeTopic(int newTopicsMessageCount) throws InterruptedException {
        log.info("Running Consensus Client subscribeTopic topicNum : {}, startTimeSecs : {}, endTimeSecs : {},limit :" +
                        " {}",
                topicNum, startTimeSecs, endTimeSecs, limit);
        Instant observerStart = Instant.now();

        ConsensusTopicQuery.Builder builder = ConsensusTopicQuery.newBuilder()
                .setLimit(100)
                .setConsensusStartTime(Timestamp.newBuilder().setSeconds(startTimeSecs).build())
                .setTopicID(TopicID.newBuilder().setRealmNum(realmNum).setTopicNum(topicNum).build());

        if (endTimeSecs != 0) {
            builder.setConsensusEndTime(Timestamp.newBuilder().setSeconds(endTimeSecs).build());
        }

        ConsensusTopicQuery request = builder.build();

        // configure StreamObserver
        int[] topics = {0};
        CountDownLatch incomingMessagesLatch = new CountDownLatch(newTopicsMessageCount);
        ClientResult result = new ClientResult(topicNum, realmNum);
        long sequenceStart = newTopicsMessageCount == 0 ? -1 : connectionHandler
                .getNextAvailableSequenceNumber(topicNum);

        StreamObserver<ConsensusTopicResponse> responseObserver = new StreamObserver<>() {

            @Override
            public void onNext(ConsensusTopicResponse response) {
                topics[0]++;
                long responseSequenceNum = response.getSequenceNumber();
                Timestamp responseTimeStamp = response.getConsensusTimestamp();
                Instant respInstant = Instant
                        .ofEpochSecond(responseTimeStamp.getSeconds(), responseTimeStamp.getNanos());
                String messageType = observerStart.isBefore(respInstant) ? "Future" : "Historic";
                log.info("Observed {} ConsensusTopicResponse {}, Time: {}, SeqNum: {}, Message: {}", messageType,
                        topics[0], responseTimeStamp, responseSequenceNum, response
                                .getMessage());

                if (messageType.equalsIgnoreCase("Future")) {
//                    if (responseSequenceNum == sequenceStart + result.incomingMessageCount) {
//                        log.info("Matching message , decrement latch count, currently : {}", incomingMessagesLatch
//                        .getCount());
//                        incomingMessagesLatch.countDown();
//                        log.info("Latch count now : {}", incomingMessagesLatch.getCount());
//                    }

                    result.incomingMessageCount++;
                } else {
                    result.historicalMessageCount++;
                }
            }

            @Override
            public void onError(Throwable t) {
                log.error("Error in ConsensusTopicResponse StreamObserver", t);
            }

            @Override
            public void onCompleted() {
                log.info("Running responseObserver onCompleted()");
            }
        };

        boolean success = true;
        try {
            asyncStub.subscribeTopic(request, responseObserver);

            // insert some new messages
            EmitFutureMessages(newTopicsMessageCount, topicNum, observerStart, incomingMessagesLatch, sequenceStart);
//            if (!incomingMessagesLatch.await(1, TimeUnit.MINUTES)) {
//                // latch count wasn't zero
//                log.error("Latch count did not reach zero");
//                result.success = false;
//            }

            responseObserver.onCompleted();
        } catch (Exception ex) {
            log.warn("RCP failed: {}", ex);
            throw ex;
        }

        log.info("Consensus service response observer: {}", result);
        return result.toString();
    }

    private void EmitFutureMessages(int newTopicsMessageCount, long tpcnm, Instant instantref, CountDownLatch latch,
                                    long seqStart) {
        connectionHandler.InsertTopicMessage(newTopicsMessageCount, tpcnm, instantref, latch, seqStart);
    }

    @ToString
    private class ClientResult {
        private final long topicNum;
        private final long realmId;
        private final boolean success;
        private int historicalMessageCount;
        private int incomingMessageCount;

        public ClientResult(long topicnm, long realnm) {
            topicNum = topicnm;
            realmId = realnm;
            historicalMessageCount = 0;
            incomingMessageCount = 0;
            success = true;
        }
    }
}
