package com.hedera.mirror.grpc.jmeter.sampler;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2020 Hedera Hashgraph, LLC
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
import com.hederahashgraph.api.proto.java.TopicID;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import lombok.Data;
import lombok.extern.log4j.Log4j2;

import com.hedera.hashgraph.sdk.consensus.ConsensusTopicId;
import com.hedera.hashgraph.sdk.mirror.MirrorClient;
import com.hedera.hashgraph.sdk.mirror.MirrorConsensusTopicQuery;
import com.hedera.hashgraph.sdk.mirror.MirrorConsensusTopicResponse;
import com.hedera.hashgraph.sdk.mirror.MirrorSubscriptionHandle;
import com.hedera.mirror.api.proto.ConsensusTopicQuery;
import com.hedera.mirror.grpc.jmeter.props.MessageListener;
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
        SamplerResult result = new SamplerResult(topicId.getRealmNum(), topicId
                .getTopicNum());

        SubscriptionResponse subscriptionResponse = new SubscriptionResponse();
        MirrorSubscriptionHandle subscription = null;
        List<MirrorConsensusTopicResponse> messages = new ArrayList<>();

        try {
            subscription = mirrorConsensusTopicQuery
                    .subscribe(mirrorClient,
                            resp -> {
                                result.onNext(resp);
                                if (result.isHistorical()) {
                                    historicMessagesLatch.countDown();
                                } else {
                                    incomingMessagesLatch.countDown();
                                }
                            },
                            subscriptionResponse::handleThrowable);

            subscriptionResponse.setSubscription(subscription);

            // await some new messages
            if (!historicMessagesLatch.await(messageListener.getMessagesLatchWaitSeconds(), TimeUnit.SECONDS)) {
                log.error("Historic messages latch count is {}, did not reach zero", historicMessagesLatch.getCount());
                result.setSuccess(false);
            }

            log.info("{} Historic messages obtained in {} ({}/s)", result.getHistoricalMessageCount(), result
                    .getStopwatch(), result.getMessageRate());

            if (historicMessagesLatch.getCount() == 0 && !incomingMessagesLatch
                    .await(messageListener.getMessagesLatchWaitSeconds(), TimeUnit.SECONDS)) {
                log.error("incomingMessagesLatch count is {}, did not reach zero", incomingMessagesLatch.getCount());
                result.setSuccess(false);
            }

            log.info("Observed {} historic and {} incoming messages in {} ({}/s): {}", result
                    .getHistoricalMessageCount(), result.getIncomingMessageCount(), result.getStopwatch(), result
                    .getMessageRate(), result.isSuccess() ? "success" : "failed");
        } catch (Exception ex) {
            log.error("Error subscribing to topic", ex);
            throw ex;
        } finally {
            if (subscription != null) {
                subscription.unsubscribe();
                log.info("Unsubscribed from {}", subscription);
            }

            HCSSamplerResult.HCSSamplerResultBuilder builder = HCSSamplerResult.builder()
                    .historicalMessageCount(result.historicalMessageCount)
                    .incomingMessageCount(result.incomingMessageCount)
                    .realmNum(result.realmNum)
                    .success(result.success)
                    .topicNum(result.topicNum);

            if (result.last != null) {
                builder.lastConcensusTimestamp(result.last.consensusTimestamp)
                        .lastSequenceNumber(result.last.sequenceNumber);
            }

            return builder.build();
        }
    }

    @Data
    public class SamplerResult {

        private final long realmNum;
        private final long topicNum;
        private final Stopwatch stopwatch = Stopwatch.createStarted();

        private long historicalMessageCount = 0L;
        private long incomingMessageCount = 0L;
        private boolean success = true;
        private MirrorConsensusTopicResponse last;
        private boolean historical = true;

        public long getTotalMessageCount() {
            return historicalMessageCount + incomingMessageCount;
        }

        public long getMessageRate() {
            long seconds = stopwatch.elapsed(TimeUnit.SECONDS);
            return seconds > 0 ? getTotalMessageCount() / seconds : 0;
        }

        public void onNext(MirrorConsensusTopicResponse response) {
            log.trace("Observed {}", response);

            if (last != null) {
                if (response.sequenceNumber != last.sequenceNumber + 1) {
                    throw new IllegalArgumentException("Out of order message sequence. Expected " + (last
                            .sequenceNumber + 1) + " got " + response.sequenceNumber);
                }

                if (!response.consensusTimestamp.isAfter(last.consensusTimestamp)) {
                    throw new IllegalArgumentException("Out of order message timestamp. Expected " + response.consensusTimestamp +
                            " to be after " + last.consensusTimestamp);
                }
            }

            if (response.consensusTimestamp.isBefore(TopicMessageGeneratorSampler.INCOMING_START)) {
                ++historicalMessageCount;
            } else {
                historical = false;
                ++incomingMessageCount;
            }

            last = response;
        }
    }
}
