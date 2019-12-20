package com.hedera.mirror.grpc.sampler.client;

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
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import com.hedera.mirror.api.proto.ConsensusServiceGrpc;
import com.hedera.mirror.api.proto.ConsensusTopicQuery;
import com.hedera.mirror.api.proto.ConsensusTopicResponse;

/**
 * A test client that will make requests of the Consensus service from the Consensus server
 */
@Log4j2
@RequiredArgsConstructor
public class ConsensusServiceClient {

    private final ManagedChannel channel;
    private final ConsensusServiceGrpc.ConsensusServiceBlockingStub blockingStub;
    private final ConsensusServiceGrpc.ConsensusServiceStub asyncStub;
    private final long topicNum;
    private final long realmNum;
    private final long startTimeSecs;
    private final long endTimeSecs;
    private final long limit;

    public ConsensusServiceClient(String host, int port, long topic, long realm, long startTime,
                                  long endTime, long lmt) {
        this(ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext(true), topic, realm, startTime, endTime, lmt);
    }

    public ConsensusServiceClient(ManagedChannelBuilder<?> channelBuilder, long topic, long realm, long startTime,
                                  long endTime, long lmt) {
        channel = channelBuilder.build();
        blockingStub = ConsensusServiceGrpc.newBlockingStub(channel);
        asyncStub = ConsensusServiceGrpc.newStub(channel);
        topicNum = topic;
        realmNum = realm;
        startTimeSecs = startTime;
        endTimeSecs = endTime;
        limit = lmt;
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
        ConsensusTopicResponse response = null;

        try {
            response = blockingStub.subscribeTopic(request).next();
        } catch (Exception ex) {
            log.warn("RCP failed: {}", ex);
            throw ex;
        }

        log.info("Consensus service response has a next item {}", response);
        return response.toString();
    }
}
