package com.hedera.mirror.hcse2e.util;

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
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.tuple.Pair;
import org.spongycastle.util.encoders.Hex;

import com.hedera.hashgraph.sdk.consensus.ConsensusClient;
import com.hedera.hashgraph.sdk.consensus.ConsensusTopicId;

@Log4j2
public class MirrorNodeClient {
    private final ConsensusClient consensusClient;

    public MirrorNodeClient() {
        String mirrorNodeAddress = Dotenv.load().get("MIRROR_NODE_ADDRESS");
        log.debug("Creating Mirror Node client for {}", mirrorNodeAddress);
        consensusClient = new ConsensusClient(Objects.requireNonNull(mirrorNodeAddress))
//                .setErrorHandler(err -> log.error("Error instantiating ConsensusClient : {}", err))
        ;
    }

    public ConsensusClient.Subscription subscribeToTopic(long topicId, Instant startTime) {
        return subscribeToTopic(new ConsensusTopicId(0, 0, topicId), startTime);
    }

    public ConsensusClient.Subscription subscribeToTopic(ConsensusTopicId topicId, Instant startTime) {
        log.debug("Subscribing to topicId : {} with startTime : {}", topicId, startTime);
        ConsensusClient.Subscription subscription = consensusClient
                .subscribe(topicId, startTime, message -> {
                    log.info("Received message: " + message.getMessageString()
                            + " consensus timestamp: " + message.consensusTimestamp
                            + " topic sequence number: " + message.sequenceNumber
                            + " topic running hash: " + Hex.toHexString(message.runningHash));
                });

        return subscription;
    }

    public Pair<ConsensusClient.Subscription, Boolean> subscribeToTopicAndRetrieveMessages(long topicId,
                                                                                           Instant startTime,
                                                                                           int numMessages,
                                                                                           int latency) throws InterruptedException {
        return subscribeToTopicAndRetrieveMessages(new ConsensusTopicId(0, 0, topicId), startTime, numMessages,
                latency);
    }

    public Pair<ConsensusClient.Subscription, Boolean> subscribeToTopicAndRetrieveMessages(ConsensusTopicId topicId,
                                                                                           Instant startTime,
                                                                                           int numMessages,
                                                                                           int latency) throws InterruptedException {
        log.debug("Subscribe to topic: {} from {}, expecting {} within {} seconds", topicId, startTime, numMessages,
                latency);
        CountDownLatch messageLatch = new CountDownLatch(numMessages);
        ConsensusClient.Subscription subscription = consensusClient
                .subscribe(topicId, startTime, message -> {
                    messageLatch.countDown();
                    log.info("Received message: {}, consensus timestamp: {}, topic sequence number: {}, topic running" +
                                    " hash: {}", message.getMessageString(), message.consensusTimestamp,
                            message.sequenceNumber,
                            Hex.toHexString(message.runningHash));
                });

        boolean validLatency = true;
        if (latency > 0) {
            if (!messageLatch.await(latency, TimeUnit.SECONDS)) {
                log.error("{} messages were expected within {} seconds. {} messages left", numMessages, latency,
                        messageLatch
                                .getCount());
            }
        } else {
            if (!messageLatch.await(1, TimeUnit.MINUTES)) {
                log.error("{} messages were expected within default of 1 min. {} messages left", numMessages,
                        messageLatch
                                .getCount());
            }
        }

        return Pair.of(subscription, validLatency);
    }

    public void unSubscribeFromTopic(ConsensusClient.Subscription subscription) {
        subscription.unsubscribe();
        log.info("Unsubscribed from {}", subscription.topicId);
    }

    public void close() throws InterruptedException {
        consensusClient.close();
    }
}
