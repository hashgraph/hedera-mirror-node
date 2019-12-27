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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.annotation.Resource;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.r2dbc.core.DatabaseClient;

import com.hedera.mirror.api.proto.ConsensusServiceGrpc;
import com.hedera.mirror.api.proto.ConsensusTopicQuery;
import com.hedera.mirror.api.proto.ConsensusTopicResponse;
import com.hedera.mirror.grpc.domain.DomainBuilder;

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

    @Resource
    private final DomainBuilder domainBuilder;
    private final DatabaseClient databasebClient;
    private final PostgresqlConnection connection;

    public ConsensusServiceReactiveClient(String host, int port, long topic, long realm, long startTime,
                                          long endTime, long lmt, DatabaseClient dbClient, PostgresqlConnection conn) {
        this(ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext(true), topic, realm, startTime, endTime, lmt, dbClient, conn);
    }

    public ConsensusServiceReactiveClient(ManagedChannelBuilder<?> channelBuilder, long topic, long realm,
                                          long startTime,
                                          long endTime, long lmt, DatabaseClient dbClient, PostgresqlConnection conn) {
        channel = channelBuilder.build();
        asyncStub = ConsensusServiceGrpc.newStub(channel);
        topicNum = topic;
        realmNum = realm;
        startTimeSecs = startTime;
        endTimeSecs = endTime;
        limit = lmt;

        databasebClient = dbClient;
        domainBuilder = new DomainBuilder(dbClient);
        connection = conn;
    }

    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    public String subscribeTopic() {
        log.info("Running Consensus Client subscribeTopic");
        ConsensusTopicQuery.Builder builder = ConsensusTopicQuery.newBuilder()
                .setLimit(limit)
                .setConsensusStartTime(Timestamp.newBuilder().setSeconds(startTimeSecs).build())
                .setTopicID(TopicID.newBuilder().setRealmNum(realmNum).setTopicNum(topicNum).build());

        if (endTimeSecs != 0) {
            builder.setConsensusEndTime(Timestamp.newBuilder().setSeconds(endTimeSecs).build());
        }

        ConsensusTopicQuery request = builder.build();

        StreamObserver<ConsensusTopicResponse> responseObserver = new StreamObserver<>() {
            final CountDownLatch finishLatch = new CountDownLatch(1);
            int topics = 0;

            @Override
            public void onNext(ConsensusTopicResponse response) {
                topics++;
                log.info("Observed ConsensusTopicResponse {}, Time: {}, SeqNum: {}, Message: {}", topics, response
                        .getConsensusTimestamp(), response.getSequenceNumber(), response.getMessage());

                int newTopicsMessageCount = 2;
                if (topics == 1) {
                    // insert some new messages
                    Instant endTime = Instant.now().plusSeconds(10);
                    for (int i = 0; i < newTopicsMessageCount; i++) {
                        Instant temp = endTime.plus(i, ChronoUnit.NANOS);
                        String instastring = "" + temp.getEpochSecond() + temp.getNano();
                        log.info("Emitting new topic message for topicNum {}, Time: {}, count: {}", topicNum,
                                instastring, i);

                        int finalI = i;
                        domainBuilder.topicMessage(t -> t.topicNum((int) topicNum)
                                .consensusTimestamp(endTime.minus(finalI, ChronoUnit.NANOS))).block();

//                        TopicMessage topicMessage = TopicMessage.builder()
//                                .consensusTimestamp(endTime.plus(i, ChronoUnit.NANOS))
//                                .realmNum(0)
//                                .message(new byte[] {0, 1, 2})
//                                .runningHash(new byte[] {3, 4, 5})
//                                .sequenceNumber(0)
//                                .topicNum((int) topicNum).build();

                        String topicMessageInsertSql = "insert into topic_message "
                                + " (consensus_timestamp, realm_num, topic_num, message, running_hash, sequence_number)"
                                + " values ({}, {}, {}, {}, {}, {})";

                        log.warn("Store TopicMessage {}, Time: {}, count: {}", topicNum, instastring, i);
//                        databasebClient
//                                .execute(String
//                                        .format(topicMessageInsertSql, instastring, 0, topicNum, new byte[] {0, 1, 2}
//                                                , new byte[] {3,
//                                                        4, 5}, 0))
////                                .insert()
////                                .into((Class<TopicMessage>) topicMessage.getClass())
////                                .using(topicMessage)
//                                .fetch()
//                                .first()
//                                .doOnNext(d -> log.info("Inserted: topicmessage"));

                        PostgresqlStatement statement = connection.createStatement(String
                                .format(topicMessageInsertSql, instastring, 0, topicNum, new byte[] {0, 1, 2}
                                        , new byte[] {3,
                                                4, 5}, 0));
                        statement.execute().blockLast();
                    }
//                    Flux<TopicMessage> generator = Flux.concat(
//                            domainBuilder.topicMessage(t -> t.topicNum((int) topicNum)
//                                    .consensusTimestamp(endTime.minusNanos(2))),
//                            domainBuilder.topicMessage(t -> t.topicNum((int) topicNum)
//                                    .consensusTimestamp(endTime.minusNanos(1))),
//                            domainBuilder.topicMessage(t -> t.topicNum((int) topicNum).consensusTimestamp(endTime))
//                    );
//
//                    generator.blockLast();
                }
            }

            @Override
            public void onError(Throwable t) {
                log.error("error in ConsensusTopicResponse StreamObserver", t);
            }

            @Override
            public void onCompleted() {
                finishLatch.countDown();
            }
        };

        try {
            asyncStub.subscribeTopic(request, responseObserver);

            responseObserver.onCompleted();
        } catch (Exception ex) {
            log.warn("RCP failed: {}", ex);
            throw ex;
        }

        log.info("Consensus service response observer", responseObserver.toString());
        return responseObserver.toString();
    }
}
