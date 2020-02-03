package com.hedera.mirror.test.e2e.acceptance.client;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import lombok.extern.log4j.Log4j2;

import com.hedera.hashgraph.sdk.mirror.MirrorClient;
import com.hedera.hashgraph.sdk.mirror.MirrorConsensusTopicQuery;
import com.hedera.hashgraph.sdk.mirror.MirrorConsensusTopicResponse;
import com.hedera.hashgraph.sdk.mirror.MirrorSubscriptionHandle;
import com.hedera.mirror.test.e2e.acceptance.config.AcceptanceTestProperties;

@Log4j2
public class MirrorNodeClient {
    private final MirrorClient mirrorClient;
    private final int messageTimeout;

    public MirrorNodeClient(AcceptanceTestProperties acceptanceTestProperties) {
        String mirrorNodeAddress = acceptanceTestProperties.getMirrorNodeAddress();
        log.debug("Creating Mirror Node client for {}", mirrorNodeAddress);
        mirrorClient = new MirrorClient(Objects.requireNonNull(mirrorNodeAddress));
        messageTimeout = acceptanceTestProperties.getMessageTimeout();
    }

    public SubscriptionResponse subscribeToTopic(MirrorConsensusTopicQuery mirrorConsensusTopicQuery) throws Throwable {
        log.debug("Subscribing to topic");
        SubscriptionResponse subscriptionResponse = new SubscriptionResponse();
        MirrorSubscriptionHandle subscription = mirrorConsensusTopicQuery
                .subscribe(mirrorClient,
                        subscriptionResponse::handleMirrorConsensusTopicResponse,
                        subscriptionResponse::handleThrowable);

        subscriptionResponse.setSubscription(subscription);

        // allow time for connection to be made and error to be caught
        Thread.sleep(5000, 0);

        return subscriptionResponse;
    }

    public SubscriptionResponse subscribeToTopicAndRetrieveMessages(MirrorConsensusTopicQuery mirrorConsensusTopicQuery,
                                                                    int numMessages,
                                                                    int latency) throws Exception {
        latency = latency <= 0 ? messageTimeout : latency;
        log.debug("Subscribing to topic, expecting {} within {} seconds", numMessages, latency);

        CountDownLatch messageLatch = new CountDownLatch(numMessages);
        SubscriptionResponse subscriptionResponse = new SubscriptionResponse();
        Stopwatch stopwatch = Stopwatch.createStarted();
        List<MirrorConsensusTopicResponse> messages = new ArrayList<>();

        MirrorSubscriptionHandle subscription = mirrorConsensusTopicQuery
                .subscribe(mirrorClient, resp -> {
                            // add expected messages only to messages list
                            if (messages.size() < numMessages) {
                                messages.add(resp);
                            }
                            messageLatch.countDown();
                        },
                        subscriptionResponse::handleThrowable);

        subscriptionResponse.setSubscription(subscription);

        if (!messageLatch.await(latency, TimeUnit.SECONDS)) {
            stopwatch.stop();
            log.error("{} messages were expected within {} s. {} not yet received after {}", numMessages, latency,
                    messageLatch.getCount(), stopwatch);
        } else {
            stopwatch.stop();
            log.info("Success, received {} out of {} messages received in {}.", numMessages - messageLatch
                    .getCount(), numMessages, stopwatch);
        }

        subscriptionResponse.setElapsedTime(stopwatch);
        subscriptionResponse.setMessages(messages);
        return subscriptionResponse;
    }

    public void unSubscribeFromTopic(MirrorSubscriptionHandle subscription) {
        subscription.unsubscribe();
        log.info("Unsubscribed from {}", subscription);
    }

    public void close() throws InterruptedException {
        log.debug("Closing Mirror Node client, waits up to 10 s for valid close");
        mirrorClient.close(10, TimeUnit.SECONDS);
    }
}
