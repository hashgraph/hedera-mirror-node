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
import java.util.concurrent.TimeoutException;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.web.reactive.function.client.ClientResponse;
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

    @Retryable(value = {StatusRuntimeException.class}, exceptionExpression = "#{message.contains('NOT_FOUND')}")
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

    @Retryable(value = {StatusRuntimeException.class}, exceptionExpression = "#{message.contains('NOT_FOUND')}")
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

    public ClientResponse getAccount(String accountId) {
        log.debug("Verify account '{}' is returned by Mirror Node", accountId);
        // build /accounts?account.id=<accountId>
        return callRestEndpoint("/{endpoint}?{key}={accountId}", ACCOUNTS_ENDPOINT, ACCOUNTS_ID_QUERY, accountId);
    }

    public ClientResponse getAccountTransactions(String accountId, int lastCount) {
        log.debug("Verify account '{}' is returned by Mirror Node", accountId);
        // build /accounts/<accountId>?order=desc&limit=50
        return callRestEndpoint("/{endpoint}/{accountId}?order=desc&limit={limit}", ACCOUNTS_ENDPOINT, accountId,
                lastCount);
    }

    public MirrorBalancesResponse getAccountBalances(String accountId) {
        log.debug("Verify balance for account '{}' is returned by Mirror Node", accountId);
        // build /balances?account.id=<accountId>
        ClientResponse clientResponse = callRestEndpoint("/{endpoint}?{key}={accountId}", BALANCES_ENDPOINT,
                ACCOUNTS_ID_QUERY, accountId);
        return clientResponse.bodyToMono(MirrorBalancesResponse.class)
                .block();
    }

    public MirrorTransactionsResponse getTransactionInfoByTimestamp(String timestamp) {
        log.debug("Verify transaction with consensus timestamp '{}' is returned by Mirror Node", timestamp);
        // build /transactions/<timestamp>
        ClientResponse clientResponse = callRestEndpoint("/{endpoint}?timestamp={timestamp}", TRANSACTIONS_ENDPOINT,
                timestamp);
        return clientResponse.bodyToMono(MirrorTransactionsResponse.class)
                .block();
    }

    public MirrorTransactionsResponse getTransactions(String transactionId) {
        log.debug("Verify transaction '{}' is returned by Mirror Node", transactionId);
        // build /transactions/<transactionId>
        ClientResponse clientResponse = callRestEndpoint("/{endpoint}/{transactionId}", TRANSACTIONS_ENDPOINT,
                transactionId);
        return clientResponse.bodyToMono(MirrorTransactionsResponse.class)
                .block();
    }

    public MirrorTokenResponse getTokenInfo(String tokenId) {
        log.debug("Verify token '{}' is returned by Mirror Node", tokenId);
        // build /tokens/<tokenId>
        ClientResponse clientResponse = callRestEndpoint("/{endpoint}/{tokenId}", TOKENS_ENDPOINT, tokenId);
        return clientResponse.bodyToMono(MirrorTokenResponse.class)
                .block();
    }

    public ClientResponse getTokenBalances(String tokenId, String accountId) {
        log.debug("Verify token balance for token '{}' and account '{}' is returned by Mirror Node", tokenId,
                accountId);
        // build /tokens/<tokenId>/balances?account.id=<accountId>
        return callRestEndpoint("/{endpoint}/{tokenId}/{path}?{key}={accountId}", TOKENS_ENDPOINT, tokenId,
                BALANCES_ENDPOINT, ACCOUNTS_ID_QUERY, accountId);
    }

    public MirrorScheduleResponse getScheduleInfo(String scheduleId) {
        log.debug("Verify schedule '{}' is returned by Mirror Node", scheduleId);
        // build /schedules/<scheduleId>
        ClientResponse clientResponse = callRestEndpoint("/{endpoint}/{scheduleId}", SCHEDULES_ENDPOINT, scheduleId);
        return clientResponse.bodyToMono(MirrorScheduleResponse.class)
                .block();
    }

    public ClientResponse callRestEndpoint(String uri, Object... uriVariables) {
        ClientResponse response = webClient.get()
                .uri(uri, uriVariables)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .block();

        log.debug("Endpoint {} returned {}", uri, response.statusCode());

        return response;
    }

    public void unSubscribeFromTopic(SubscriptionHandle subscription) {
        subscription.unsubscribe();
        log.info("Unsubscribed from {}", subscription);
    }

    public void close() throws TimeoutException {
        log.debug("Closing Mirror Node client, waits up to 10 s for valid close");
        client.close();
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
