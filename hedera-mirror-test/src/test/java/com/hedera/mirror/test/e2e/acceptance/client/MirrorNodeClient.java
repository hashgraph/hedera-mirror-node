package com.hedera.mirror.test.e2e.acceptance.client;

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

import com.google.common.base.Stopwatch;
import io.grpc.StatusRuntimeException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Recover;
import org.springframework.web.reactive.function.client.WebClient;

import com.hedera.hashgraph.sdk.SubscriptionHandle;
import com.hedera.hashgraph.sdk.TopicMessageQuery;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorBalancesResponse;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorScheduleResponse;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorTokenResponse;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorTransactionsResponse;

@Log4j2
public class MirrorNodeClient extends AbstractNetworkClient {

    @Autowired
    private WebClient webClient;

    // REST ENDPOINTS
    private static final String ACCOUNTS_ENDPOINT = "accounts";
    private static final String BALANCES_ENDPOINT = "balances";
    private static final String SCHEDULES_ENDPOINT = "schedules";
    private static final String TOKENS_ENDPOINT = "tokens";
    private static final String TOPICS_ENDPOINT = "topics";
    private static final String TRANSACTIONS_ENDPOINT = "transactions";

    // FILTER QUERIES
    private static final String ACCOUNTS_ID_QUERY = "account.id";

    public MirrorNodeClient(SDKClient sdkClient) {
        super(sdkClient);
        String mirrorNodeAddress = sdkClient.getMirrorNodeAddress();
        log.debug("Creating Mirror Node client for {}", mirrorNodeAddress);
    }

    public SubscriptionResponse subscribeToTopic(TopicMessageQuery topicMessageQuery) throws Throwable {
        log.debug("Subscribing to topic.");
        SubscriptionResponse subscriptionResponse = new SubscriptionResponse();
        SubscriptionHandle subscription = topicMessageQuery
                .subscribe(client,
                        subscriptionResponse::handleConsensusTopicResponse);

        subscriptionResponse.setSubscription(subscription);

        // allow time for connection to be made and error to be caught
        Thread.sleep(5000, 0);

        return subscriptionResponse;
    }

    public SubscriptionResponse subscribeToTopicAndRetrieveMessages(TopicMessageQuery topicMessageQuery,
                                                                    int numMessages,
                                                                    long latency) throws Throwable {
        latency = latency <= 0 ? sdkClient.getMessageTimeoutSeconds() : latency;
        log.debug("Subscribing to topic, expecting {} within {} seconds.", numMessages, latency);

        CountDownLatch messageLatch = new CountDownLatch(numMessages);
        SubscriptionResponse subscriptionResponse = new SubscriptionResponse();
        Stopwatch stopwatch = Stopwatch.createStarted();

        SubscriptionHandle subscription = topicMessageQuery
                .subscribe(client, resp -> {
                    // add expected messages only to messages list
                    if (subscriptionResponse.getMirrorHCSResponses().size() < numMessages) {
                        subscriptionResponse.handleConsensusTopicResponse(resp);
                    }
                    messageLatch.countDown();
                });

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

        if (subscriptionResponse.errorEncountered()) {
            throw subscriptionResponse.getResponseError();
        }

        return subscriptionResponse;
    }

    public MirrorBalancesResponse getAccountBalances(String accountId) {
        log.debug("Verify balance for account '{}' is returned by Mirror Node", accountId);
        // build /balances?account.id=<accountId>
        return (MirrorBalancesResponse) callRestEndpoint("/{endpoint}?{key}={accountId}",
                MirrorBalancesResponse.class, BALANCES_ENDPOINT,
                ACCOUNTS_ID_QUERY, accountId);
    }

    public MirrorTransactionsResponse getTransactionInfoByTimestamp(String timestamp) {
        log.debug("Verify transaction with consensus timestamp '{}' is returned by Mirror Node", timestamp);
        // build /transactions/<timestamp>
        return (MirrorTransactionsResponse) callRestEndpoint("/{endpoint}?timestamp={timestamp}",
                MirrorTransactionsResponse.class, TRANSACTIONS_ENDPOINT,
                timestamp);
    }

    public MirrorTransactionsResponse getTransactions(String transactionId) {
        log.debug("Verify transaction '{}' is returned by Mirror Node", transactionId);
        // build /transactions/<transactionId>
        return (MirrorTransactionsResponse) callRestEndpoint("/{endpoint}/{transactionId}",
                MirrorTransactionsResponse.class, TRANSACTIONS_ENDPOINT,
                transactionId);
    }

    public MirrorTokenResponse getTokenInfo(String tokenId) {
        log.debug("Verify token '{}' is returned by Mirror Node", tokenId);
        // build /tokens/<tokenId>
        return (MirrorTokenResponse) callRestEndpoint("/{endpoint}/{tokenId}", MirrorTokenResponse.class,
                TOKENS_ENDPOINT, tokenId);
    }

    public MirrorScheduleResponse getScheduleInfo(String scheduleId) {
        log.debug("Verify schedule '{}' is returned by Mirror Node", scheduleId);
        // build /schedules/<scheduleId>
        return (MirrorScheduleResponse) callRestEndpoint("/{endpoint}/{scheduleId}", MirrorScheduleResponse.class,
                SCHEDULES_ENDPOINT,
                scheduleId);
    }

    public <T> T callRestEndpoint(String uri, Class<T> classType, Object... uriVariables) {
        T response = webClient.get()
                .uri(uri, uriVariables)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(classType)
                .doOnNext(x -> log.debug("Endpoint call successfully returned a 200"))
                .doOnError(x -> log.debug("Endpoint failed, returning: {}", x.getMessage()))
                .block();

        return response;
    }

    public void unSubscribeFromTopic(SubscriptionHandle subscription) {
        subscription.unsubscribe();
        log.info("Unsubscribed from {}", subscription);
    }

    /**
     * Recover method of subscribeToTopic retry logic. Method parameters of retry method must match this method after
     * exception parameter
     *
     * @param t
     * @param topicMessageQuery
     * @throws InterruptedException
     */
    @Recover
    public void recover(StatusRuntimeException t, TopicMessageQuery topicMessageQuery) throws InterruptedException {
        log.error("Subscription w retry failure: {}", t.getMessage());
        throw t;
    }

    /**
     * Recover method of subscribeToTopicAndRetrieveMessages retry logic. Method parameters of retry method must match
     * this method after exception parameter
     *
     * @param t
     * @param topicMessageQuery
     * @param numMessages
     * @param latency
     * @throws InterruptedException
     */
    @Recover
    public void recover(StatusRuntimeException t, TopicMessageQuery topicMessageQuery,
                        int numMessages,
                        long latency) throws InterruptedException {
        log.error("Subscription w retry failure: {}", t.getMessage());
        throw t;
    }
}
