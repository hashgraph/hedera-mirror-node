package com.hedera.mirror.test.e2e.acceptance.util;

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

import io.github.cdimascio.dotenv.Dotenv;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import lombok.extern.log4j.Log4j2;

import com.hedera.hashgraph.sdk.consensus.ConsensusTopicId;
import com.hedera.hashgraph.sdk.mirror.MirrorClient;
import com.hedera.hashgraph.sdk.mirror.MirrorConsensusTopicQuery;
import com.hedera.hashgraph.sdk.mirror.MirrorConsensusTopicResponse;
import com.hedera.hashgraph.sdk.mirror.MirrorSubscriptionHandle;

@Log4j2
public class MirrorNodeClient {
    private final MirrorClient mirrorClient;

    public MirrorNodeClient() {
        String mirrorNodeAddress = Dotenv.load().get("MIRROR_NODE_ADDRESS");
        log.debug("Creating Mirror Node client for {}", mirrorNodeAddress);
        mirrorClient = new MirrorClient(Objects.requireNonNull(mirrorNodeAddress));
    }

    public MirrorSubscriptionHandle subscribeToTopic(ConsensusTopicId topicId, Instant startTime) {
        log.debug("Subscribing to topicId : {} with startTime : {}", topicId, startTime);
        return new MirrorConsensusTopicQuery()
                .setTopicId(topicId)
                .subscribe(mirrorClient, resp -> {
                            String messageAsString = new String(resp.message, StandardCharsets.UTF_8);
                            log.info("Received message: " + messageAsString
                                    + " consensus timestamp: " + resp.consensusTimestamp
                                    + " topic sequence number: " + resp.sequenceNumber);
                        },
                        // On gRPC error, print the stack trace
                        Throwable::printStackTrace);
    }

    public MirrorSubscriptionHandle subscribeToTopicAndRetrieveMessages(ConsensusTopicId topicId,
                                                                        Instant startTime,
                                                                        int numMessages,
                                                                        int latency) throws Exception {
        log.debug("Subscribe to topic: {} from {}, expecting {} within {} seconds", topicId, startTime, numMessages,
                latency);
        CountDownLatch messageLatch = new CountDownLatch(numMessages);
        List<MirrorConsensusTopicResponse> messages = new ArrayList<>();
        MirrorSubscriptionHandle subscription = new MirrorConsensusTopicQuery()
                .setTopicId(topicId)
                .subscribe(mirrorClient, resp -> {
                            messages.add(resp);
                            String messageAsString = new String(resp.message, StandardCharsets.UTF_8);
                            log.info("Received message: " + messageAsString
                                    + " consensus timestamp: " + resp.consensusTimestamp
                                    + " topic sequence number: " + resp.sequenceNumber);
                            messageLatch.countDown();
                        },
                        // On gRPC error, print the stack trace
                        Throwable::printStackTrace);

        latency = latency <= 0 ? 60 : latency;
        if (!messageLatch.await(latency, TimeUnit.SECONDS)) {
            log.error("{} messages were expected within {} seconds. {} not yet received", numMessages, latency,
                    messageLatch.getCount());

            for (MirrorConsensusTopicResponse m : messages) {
                String messageAsString = new String(m.message, StandardCharsets.UTF_8);
                log.info("Received message: {}, consensus timestamp: {}, topic sequence number: {}",
                        messageAsString, m.consensusTimestamp, m.sequenceNumber);
            }

            throw new Exception("Mirror client failed to retrieve all messages within " + latency + " s");
        }

        log.info("Success, received {} out of {} messages received.", numMessages - messageLatch
                .getCount(), numMessages);
        return subscription;
    }

    public void unSubscribeFromTopic(MirrorSubscriptionHandle subscription) {
        subscription.unsubscribe();
        log.info("Unsubscribed from {}", subscription);
    }

    public void close() throws InterruptedException {
        mirrorClient.close();
    }
}
