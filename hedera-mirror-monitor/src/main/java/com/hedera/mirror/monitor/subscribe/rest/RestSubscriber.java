package com.hedera.mirror.monitor.subscribe.rest;

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

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import com.hedera.hashgraph.sdk.TransactionId;
import com.hedera.mirror.monitor.MonitorProperties;
import com.hedera.mirror.monitor.publish.PublishResponse;
import com.hedera.mirror.monitor.subscribe.MirrorSubscriber;
import com.hedera.mirror.monitor.subscribe.SubscribeProperties;
import com.hedera.mirror.monitor.subscribe.SubscribeResponse;
import com.hedera.mirror.monitor.subscribe.SubscriptionStatus;
import com.hedera.mirror.monitor.subscribe.rest.response.MirrorTransaction;

@Log4j2
@Named
@RequiredArgsConstructor
class RestSubscriber implements MirrorSubscriber {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SubscribeProperties subscribeProperties;
    private final Flux<RestSubscription> subscriptions = Flux.defer(this::createSubscriptions).cache();
    private final WebClient webClient;

    @Autowired
    RestSubscriber(MonitorProperties monitorProperties, SubscribeProperties subscribeProperties,
                   WebClient.Builder webClientBuilder) {
        this.subscribeProperties = subscribeProperties;

        String url = monitorProperties.getMirrorNode().getRest().getBaseUrl();
        webClient = webClientBuilder.baseUrl(url)
                .defaultHeaders(h -> h.setAccept(List.of(MediaType.APPLICATION_JSON)))
                .build();
        log.info("Connecting to mirror node {}", url);
    }

    @Override
    public void onPublish(PublishResponse response) {
        subscriptions.doOnNext(s -> log.info("onPublish: {}", s))
                .filter(s -> s.getStatus() != SubscriptionStatus.COMPLETED)
                .filter(s -> RANDOM.nextDouble() < s.getProperties().getSamplePercent())
                .map(RestSubscription::getSink)
                .subscribe(s -> log.info("Result: {}", s.tryEmitNext(response)));
    }

    @Override
    public Flux<SubscribeResponse> subscribe() {
        return subscriptions.flatMap(this::clientSubscribe);
    }

    @Override
    public Flux<RestSubscription> getSubscriptions() {
        return subscriptions;
    }

    private Flux<RestSubscription> createSubscriptions() {
        Collection<RestSubscription> subscriptionList = new ArrayList<>();

        for (RestSubscriberProperties properties : subscribeProperties.getRest()) {
            if (subscribeProperties.isEnabled() && properties.isEnabled()) {
                for (int i = 1; i <= properties.getSubscribers(); ++i) {
                    subscriptionList.add(new RestSubscription(i, properties));
                }
            }
        }

        return Flux.fromIterable(subscriptionList);
    }

    private Flux<SubscribeResponse> clientSubscribe(RestSubscription subscription) {
        RestSubscriberProperties properties = subscription.getProperties();

        RetryBackoffSpec retrySpec = Retry
                .backoff(properties.getRetry().getMaxAttempts(), properties.getRetry().getMinBackoff())
                .maxBackoff(properties.getRetry().getMaxBackoff())
                .filter(this::shouldRetry)
                .doBeforeRetry(r -> log.debug("Retry attempt #{} after failure: {}",
                        r.totalRetries() + 1, r.failure().getMessage()));

        return subscription.getSink()
                .asFlux()
                .publishOn(Schedulers.parallel())
                .doFinally(s -> subscription.onComplete())
                .doOnNext(publishResponse -> log.trace("Querying REST API: {}", publishResponse))
                .flatMap(publishResponse -> webClient.get()
                        .uri("/transactions/{transactionId}", toString(publishResponse.getTransactionId()))
                        .retrieve()
                        .bodyToMono(MirrorTransaction.class)
                        .name("rest")
                        .metrics()
                        .timeout(properties.getTimeout())
                        .retryWhen(retrySpec)
                        .onErrorContinue((t, o) -> subscription.onError(t))
                        .doOnNext(subscription::onNext)
                        .map(transaction -> toResponse(subscription, publishResponse, transaction)))
                .limitRequest(properties.getLimit())
                .take(properties.getDuration());
    }

    private SubscribeResponse toResponse(RestSubscription subscription, PublishResponse publishResponse,
                                         MirrorTransaction transaction) {
        Instant receivedTimestamp = Instant.now();

        return SubscribeResponse.builder()
                .consensusTimestamp(transaction.getConsensusTimestamp())
                .publishedTimestamp(publishResponse.getRequest().getTimestamp())
                .receivedTimestamp(receivedTimestamp)
                .subscription(subscription)
                .build();
    }

    protected boolean shouldRetry(Throwable t) {
        return t instanceof WebClientResponseException &&
                ((WebClientResponseException) t).getStatusCode() == HttpStatus.NOT_FOUND;
    }

    private String toString(TransactionId tid) {
        return tid.accountId + "-" + tid.validStart.getEpochSecond() + "-" + tid.validStart.getNano();
    }
}
