package com.hedera.mirror.test.e2e.acceptance.client;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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
import com.google.common.base.Throwables;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.inject.Named;

import com.hedera.mirror.test.e2e.acceptance.config.AcceptanceTestProperties;
import com.hedera.mirror.test.e2e.acceptance.props.MirrorNetworkNode;
import com.hedera.mirror.test.e2e.acceptance.props.MirrorNetworkNodes;

import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.MediaType;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import com.hedera.hashgraph.sdk.SubscriptionHandle;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.hashgraph.sdk.TopicMessageQuery;
import com.hedera.mirror.test.e2e.acceptance.config.RestPollingProperties;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorContractResponse;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorContractResultResponse;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorContractResultsResponse;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorCryptoAllowanceResponse;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorNftResponse;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorNftTransactionsResponse;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorScheduleResponse;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorTokenResponse;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorTransactionsResponse;

@Log4j2
@Named
public class MirrorNodeClient {

    private final AcceptanceTestProperties acceptanceTestProperties;
    private final WebClient webClient;
    private final RetryBackoffSpec retrySpec;

    public MirrorNodeClient(AcceptanceTestProperties acceptanceTestProperties,
                            WebClient webClient) {
        this.acceptanceTestProperties = acceptanceTestProperties;
        this.webClient = webClient;
        var properties = acceptanceTestProperties.getRestPollingProperties();
        retrySpec = Retry.backoff(properties.getMaxAttempts(), properties.getMinBackoff())
                .maxBackoff(properties.getMaxBackoff())
                .filter(this::shouldRetryRestCall);
    }

    public SubscriptionResponse subscribeToTopic(SDKClient sdkClient, TopicMessageQuery topicMessageQuery) throws Throwable {
        log.debug("Subscribing to topic.");
        SubscriptionResponse subscriptionResponse = new SubscriptionResponse();
        SubscriptionHandle subscription = topicMessageQuery
                .subscribe(sdkClient.getClient(), subscriptionResponse::handleConsensusTopicResponse);

        subscriptionResponse.setSubscription(subscription);

        // allow time for connection to be made and error to be caught
        Thread.sleep(5000, 0);

        return subscriptionResponse;
    }

    public SubscriptionResponse subscribeToTopicAndRetrieveMessages(SDKClient sdkClient,
                                                                    TopicMessageQuery topicMessageQuery,
                                                                    int numMessages,
                                                                    long latency) throws Throwable {
        latency = latency <= 0 ? acceptanceTestProperties.getMessageTimeout().toSeconds() : latency;
        log.debug("Subscribing to topic, expecting {} within {} seconds.", numMessages, latency);

        CountDownLatch messageLatch = new CountDownLatch(numMessages);
        SubscriptionResponse subscriptionResponse = new SubscriptionResponse();
        Stopwatch stopwatch = Stopwatch.createStarted();

        SubscriptionHandle subscription = topicMessageQuery
                .subscribe(sdkClient.getClient(), resp -> {
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

    public MirrorCryptoAllowanceResponse getAccountCryptoAllowance(String accountId) {
        log.debug("Verify account '{}''s crypto allowance is returned by Mirror Node", accountId);
        return callRestEndpoint("/accounts/{accountId}/allowances/crypto",
                MirrorCryptoAllowanceResponse.class, accountId);
    }

    public MirrorCryptoAllowanceResponse getAccountCryptoAllowanceBySpender(String accountId, String spenderId) {
        log.debug("Verify account '{}''s crypto allowance for {} is returned by Mirror Node", accountId, spenderId);
        return callRestEndpoint("/accounts/{accountId}/allowances/crypto?spender.id={spenderId}",
                MirrorCryptoAllowanceResponse.class, accountId, spenderId);
    }

    public MirrorContractResponse getContractInfo(String contractId) {
        log.debug("Verify contract '{}' is returned by Mirror Node", contractId);
        return callRestEndpoint("/contracts/{contractId}", MirrorContractResponse.class, contractId);
    }

    public MirrorContractResultsResponse getContractResultsById(String contractId) {
        log.debug("Verify contract results '{}' is returned by Mirror Node", contractId);
        return callRestEndpoint("/contracts/{contractId}/results", MirrorContractResultsResponse.class, contractId);
    }

    public MirrorContractResultResponse getContractResultByTransactionId(String transactionId) {
        log.debug("Verify contract result '{}' is returned by Mirror Node", transactionId);
        return callRestEndpoint("/contracts/results/{transactionId}", MirrorContractResultResponse.class,
                transactionId);
    }

    public List<MirrorNetworkNode> getNetworkNodes() {
        List<MirrorNetworkNode> nodes = new ArrayList<>();
        String next = "/network/nodes?limit=25";

        do {
            var response = callRestEndpoint(next, MirrorNetworkNodes.class);
            nodes.addAll(response.getNodes());
            next = response.getLinks() != null ? response.getLinks().getNext() : null;
        } while (next != null);

        return nodes;
    }

    public MirrorNftResponse getNftInfo(String tokenId, long serialNumber) {
        log.debug("Verify serial number '{}' for token '{}' is returned by Mirror Node", serialNumber, tokenId);
        return callRestEndpoint("/tokens/{tokenId}/nfts/{serialNumber}", MirrorNftResponse.class, tokenId,
                serialNumber);
    }

    public MirrorNftTransactionsResponse getNftTransactions(TokenId tokenId, Long serialNumber) {
        log.debug("Get list of transactions for token '{}' and serial number '{}' from Mirror Node", tokenId,
                serialNumber);
        return callRestEndpoint("/tokens/{tokenId}/nfts/{serialNumber}/transactions",
                MirrorNftTransactionsResponse.class, tokenId, serialNumber);
    }

    public MirrorScheduleResponse getScheduleInfo(String scheduleId) {
        log.debug("Verify schedule '{}' is returned by Mirror Node", scheduleId);
        return callRestEndpoint("/schedules/{scheduleId}", MirrorScheduleResponse.class, scheduleId);
    }

    public MirrorTokenResponse getTokenInfo(String tokenId) {
        log.debug("Verify token '{}' is returned by Mirror Node", tokenId);
        return callRestEndpoint("/tokens/{tokenId}", MirrorTokenResponse.class, tokenId);
    }

    public MirrorTransactionsResponse getTransactionInfoByTimestamp(String timestamp) {
        log.debug("Verify transaction with consensus timestamp '{}' is returned by Mirror Node", timestamp);
        return callRestEndpoint("/transactions?timestamp={timestamp}",
                MirrorTransactionsResponse.class, timestamp);
    }

    public MirrorTransactionsResponse getTransactions(String transactionId) {
        log.debug("Verify transaction '{}' is returned by Mirror Node", transactionId);
        return callRestEndpoint("/transactions/{transactionId}",
                MirrorTransactionsResponse.class, transactionId);
    }

    public void unSubscribeFromTopic(SubscriptionHandle subscription) {
        subscription.unsubscribe();
        log.info("Unsubscribed from {}", subscription);
    }

    protected boolean shouldRetryRestCall(Throwable t) {
        return acceptanceTestProperties.getRestPollingProperties().getRetryableExceptions()
                .stream()
                .anyMatch(ex -> ex.isInstance(t) || ex.isInstance(Throwables.getRootCause(t)));
    }

    private <T> T callRestEndpoint(String uri, Class<T> classType, Object... uriVariables) {
        return webClient.get()
                .uri(uri.replace("/api/v1", ""), uriVariables)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(classType)
                .retryWhen(retrySpec)
                .doOnError(x -> log.error("Endpoint failed, returning: {}", x.getMessage()))
                .block();
    }
}
