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
import io.grpc.netty.shaded.io.netty.handler.timeout.ReadTimeoutException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.inject.Named;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.PrematureCloseException;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import com.hedera.hashgraph.sdk.SubscriptionHandle;
import com.hedera.hashgraph.sdk.TopicMessageQuery;
import com.hedera.mirror.test.e2e.acceptance.config.RestPollingProperties;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorNftResponse;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorScheduleResponse;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorTokenResponse;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorTransactionsResponse;

@Log4j2
@Named
public class MirrorNodeClient extends AbstractNetworkClient {

    private final WebClient webClient;
    private final RetryBackoffSpec retrySpec;

    public MirrorNodeClient(SDKClient sdkClient, WebClient webClient, RetryTemplate retryTemplate) {
        super(sdkClient, retryTemplate);
        this.webClient = webClient;
        RestPollingProperties restPollingProperties = this.sdkClient.getAcceptanceTestProperties()
                .getRestPollingProperties();
        retrySpec = Retry
                .backoff(restPollingProperties.getMaxAttempts(), restPollingProperties.getMinBackoff())
                .maxBackoff(restPollingProperties.getMaxBackoff())
                .filter(this::shouldRetryRestCall)
                .doBeforeRetry(r -> log.warn("Retry attempt #{} after failure: {}",
                        r.totalRetries() + 1, r.failure().getMessage()));
        log.info("Creating Mirror Node client for {}", sdkClient.getMirrorNodeAddress());
    }

    public SubscriptionResponse subscribeToTopic(TopicMessageQuery topicMessageQuery) throws Throwable {
        log.debug("Subscribing to topic.");
        SubscriptionResponse subscriptionResponse = new SubscriptionResponse();
        SubscriptionHandle subscription = topicMessageQuery
                .subscribe(client, subscriptionResponse::handleConsensusTopicResponse);

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

    public MirrorTokenResponse getTokenInfo(String tokenId) {
        log.debug("Verify token '{}' is returned by Mirror Node", tokenId);
        return callRestEndpoint("/tokens/{tokenId}", MirrorTokenResponse.class, tokenId);
    }

    public MirrorNftResponse getNftInfo(String tokenId, long serialNumber) {
        log.debug("Verify nft '{}' for token '{}' is returned by Mirror Node", serialNumber, tokenId);
        return callRestEndpoint("/tokens/{tokenId}/nfts/{serialNumber}", MirrorNftResponse.class, tokenId,
                serialNumber);
    }

    public MirrorScheduleResponse getScheduleInfo(String scheduleId) {
        log.debug("Verify schedule '{}' is returned by Mirror Node", scheduleId);
        return callRestEndpoint("/schedules/{scheduleId}", MirrorScheduleResponse.class, scheduleId);
    }

    public <T> T callRestEndpoint(String uri, Class<T> classType, Object... uriVariables) {
        T response = webClient.get()
                .uri(uri, uriVariables)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(classType)
                .retryWhen(retrySpec)
                .doOnNext(x -> log.debug("Endpoint call successfully returned a 200"))
                .doOnError(x -> log.error("Endpoint failed, returning: {}", x.getMessage()))
                .block();

        return response;
    }

    public void unSubscribeFromTopic(SubscriptionHandle subscription) {
        subscription.unsubscribe();
        log.info("Unsubscribed from {}", subscription);
    }

    protected boolean shouldRetryRestCall(Throwable t) {
        return t instanceof PrematureCloseException ||
                t instanceof ReadTimeoutException ||
                t instanceof TimeoutException ||
                (t instanceof WebClientResponseException &&
                        ((WebClientResponseException) t).getStatusCode() == HttpStatus.NOT_FOUND);
    }
}
