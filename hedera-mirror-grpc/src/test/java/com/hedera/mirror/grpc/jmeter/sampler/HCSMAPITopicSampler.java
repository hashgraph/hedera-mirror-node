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
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.log4j.Log4j2;

import com.hedera.hashgraph.sdk.consensus.ConsensusTopicId;
import com.hedera.hashgraph.sdk.mirror.MirrorClient;
import com.hedera.hashgraph.sdk.mirror.MirrorConsensusTopicQuery;
import com.hedera.hashgraph.sdk.mirror.MirrorSubscriptionHandle;
import com.hedera.mirror.api.proto.ConsensusTopicQuery;
import com.hedera.mirror.grpc.jmeter.props.MessageListener;
import com.hedera.mirror.grpc.jmeter.sampler.result.HCSMAPISamplerResult;
import com.hedera.mirror.grpc.jmeter.sampler.result.HCSSamplerResult;
import com.hedera.mirror.grpc.util.ProtoUtil;

@Log4j2
public class HCSMAPITopicSampler implements HCSTopicSampler {

    private final ConsensusTopicQuery request;
    private final MirrorClient mirrorClient;
    private final MirrorConsensusTopicQuery mirrorConsensusTopicQuery;

    public HCSMAPITopicSampler(ConsensusTopicQuery request, String mirrorNodeAddress) {
        this.request = request;
        mirrorClient = new MirrorClient(Objects.requireNonNull(mirrorNodeAddress));
        log.info("Setup MirrorClient at {}} for request {}", TextFormat.shortDebugString(request), mirrorNodeAddress);

        TopicID topicID = request.getTopicID();
        mirrorConsensusTopicQuery = new MirrorConsensusTopicQuery()
                .setTopicId(new ConsensusTopicId(topicID.getShardNum(), topicID.getRealmNum(), topicID.getTopicNum()))
                .setStartTime(ProtoUtil.fromTimestamp(request.getConsensusStartTime()));

        if (request.hasConsensusEndTime()) {
            mirrorConsensusTopicQuery.setEndTime(ProtoUtil.fromTimestamp(request.getConsensusEndTime()));
        }

        if (request.getLimit() > 0) {
            mirrorConsensusTopicQuery.setLimit(request.getLimit());
        }
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
    public HCSSamplerResult subscribeTopic(MessageListener messageListener) {
        log.info("Subscribing to topic using MAPI with {}, {}", () -> TextFormat
                .shortDebugString(request), () -> messageListener);

        CountDownLatch historicMessagesLatch = new CountDownLatch(messageListener.getHistoricMessagesCount());
        CountDownLatch incomingMessagesLatch = new CountDownLatch(messageListener.getFutureMessagesCount());
        TopicID topicId = request.getTopicID();
        HCSMAPISamplerResult result = HCSMAPISamplerResult
                .builder()
                .realmNum(topicId.getRealmNum())
                .topicNum(topicId.getTopicNum())
                .success(true)
                .build();
        MirrorSubscriptionHandle subscription = null;
        ScheduledExecutorService scheduler = null;

        try {
            subscription = mirrorConsensusTopicQuery
                    .subscribe(mirrorClient,
                            resp -> {
                                result.onNext(resp, Instant.now());
                                if (result.isHistorical()) {
                                    historicMessagesLatch.countDown();
                                } else {
                                    incomingMessagesLatch.countDown();
                                }
                            },
                            result::onError);

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

            result.onComplete();
        } catch (Exception ex) {
            log.error("Error subscribing to topic", ex);
            throw ex;
        } finally {
            if (subscription != null) {
                subscription.unsubscribe();
                log.info("Unsubscribed from {}", subscription);
            }

            scheduler.shutdownNow();

            return result;
        }
    }
}
