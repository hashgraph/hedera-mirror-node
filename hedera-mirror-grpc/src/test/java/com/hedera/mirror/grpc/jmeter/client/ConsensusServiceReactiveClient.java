package com.hedera.mirror.grpc.jmeter.client;

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
import io.r2dbc.postgresql.api.PostgresqlConnection;
import io.r2dbc.postgresql.api.PostgresqlStatement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import lombok.extern.log4j.Log4j2;

import com.hedera.mirror.api.proto.ConsensusServiceGrpc;
import com.hedera.mirror.api.proto.ConsensusTopicQuery;
import com.hedera.mirror.api.proto.ConsensusTopicResponse;
import com.hedera.mirror.grpc.converter.InstantToLongConverter;

/**
 * A test client that will make requests of the Consensus service from the Consensus server
 */
@Log4j2
public class ConsensusServiceReactiveClient {
    private final ManagedChannel channel;
    private final ConsensusServiceGrpc.ConsensusServiceStub asyncStub;
    private final long topicNum;
    private final long realmNum;
    private final long startTimeSecs;
    private final long endTimeSecs;
    private final long limit;

    private final PostgresqlConnection connection;

    private final InstantToLongConverter itlc = new InstantToLongConverter();

    public ConsensusServiceReactiveClient(String host, int port, long topic, long realm, long startTime,
                                          long endTime, long lmt, PostgresqlConnection conn) {
        this(ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext(true), topic, realm, startTime, endTime, lmt, conn);
    }

    public ConsensusServiceReactiveClient(ManagedChannelBuilder<?> channelBuilder, long topic, long realm,
                                          long startTime,
                                          long endTime, long lmt, PostgresqlConnection conn) {
        channel = channelBuilder.build();
        asyncStub = ConsensusServiceGrpc.newStub(channel);
        topicNum = topic;
        realmNum = realm;
        startTimeSecs = startTime;
        endTimeSecs = endTime;
        limit = lmt;

        connection = conn;
    }

    public void shutdown() throws InterruptedException {
        log.info("Client shutdown called");
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    public String subscribeTopic(int newTopicsMessageCount) {
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
        StreamObserver<ConsensusTopicResponse> responseObserver = new StreamObserver<>() {

            @Override
            public void onNext(ConsensusTopicResponse response) {
                topics[0]++;
                Instant respInstant = Instant
                        .ofEpochSecond(response.getConsensusTimestamp().getSeconds(), response.getConsensusTimestamp()
                                .getNanos());
                String messageType = observerStart.isBefore(respInstant) ? "Future" : "Historic";
                log.info("Observed {} ConsensusTopicResponse {}, Time: {}, SeqNum: {}, Message: {}", messageType,
                        topics[0], response.getConsensusTimestamp(), response.getSequenceNumber(), response
                                .getMessage());
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

        try {
            asyncStub.subscribeTopic(request, responseObserver);

            // insert some new messages
            EmitFutureMessages(newTopicsMessageCount, topicNum, observerStart);

            responseObserver.onCompleted();
        } catch (Exception ex) {
            log.warn("RCP failed: {}", ex);
            throw ex;
        }

        String response = String.format("%d topics encountered", topics[0]);
        log.info("Consensus service response observer: {}", response);
        return response;
    }

    private void EmitFutureMessages(int newTopicsMessageCount, long tpcnm, Instant instantref) {
        // insert some new messages
        int instantnano = instantref.getNano();
        for (int i = 0; i < newTopicsMessageCount; i++) {
            Instant temp = instantref.plus(i, ChronoUnit.SECONDS);
            Long instalong = itlc.convert(temp);
            log.info("Emitting new topic message for topicNum {}, Time: {}, count: {}", tpcnm,
                    instalong, i);

            String topicMessageInsertSql = "insert into topic_message"
                    + " (consensus_timestamp, realm_num, topic_num, message, running_hash, sequence_number)"
                    + " values ($1, $2, $3, $4, $5, $6)";

            PostgresqlStatement statement = connection.createStatement(topicMessageInsertSql)
                    .bind("$1", instalong)
                    .bind("$2", 0)
                    .bind("$3", tpcnm)
                    .bind("$4", new byte[] {22, 33, 44})
                    .bind("$5", new byte[] {55, 66, 77})
                    .bind("$6", i + instantnano);
            statement.execute().blockLast();

            log.info("Stored TopicMessage {}, Time: {}, count: {}", tpcnm,
                    instalong, i);
        }
    }
}
