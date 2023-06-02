/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.mirror.monitor.subscribe.rest;

import com.google.common.collect.Iterables;
import com.hedera.hashgraph.sdk.TransactionId;
import com.hedera.mirror.monitor.publish.PublishResponse;
import com.hedera.mirror.monitor.subscribe.MirrorSubscriber;
import com.hedera.mirror.monitor.subscribe.SubscribeProperties;
import com.hedera.mirror.monitor.subscribe.SubscribeResponse;
import com.hedera.mirror.rest.model.TransactionByIdResponse;
import jakarta.inject.Named;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

@Log4j2
@Named
@RequiredArgsConstructor
@SuppressWarnings("unchecked")
class RestSubscriber implements MirrorSubscriber {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RestApiClient restApiClient;
    private final SubscribeProperties subscribeProperties;
    private final Flux<RestSubscription> subscriptions =
            Flux.defer(this::createSubscriptions).cache();

    @Override
    public void onPublish(PublishResponse response) {
        subscriptions
                .filter(s -> shouldSample(s, response))
                .map(RestSubscription::getSink)
                .subscribe(s -> s.tryEmitNext(response));
    }

    private boolean shouldSample(RestSubscription subscription, PublishResponse response) {
        if (!subscription.isRunning()) {
            return false;
        }

        RestSubscriberProperties properties = subscription.getProperties();
        Set<String> publishers = properties.getPublishers();

        if (!publishers.isEmpty()
                && !publishers.contains(response.getRequest().getScenario().getName())) {
            return false;
        }

        return RANDOM.nextDouble() < properties.getSamplePercent();
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

        for (RestSubscriberProperties properties : subscribeProperties.getRest().values()) {
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

        RetryBackoffSpec retrySpec = Retry.backoff(
                        properties.getRetry().getMaxAttempts(),
                        properties.getRetry().getMinBackoff())
                .maxBackoff(properties.getRetry().getMaxBackoff())
                .filter(this::shouldRetry)
                .doBeforeRetry(r -> log.debug(
                        "Retry attempt #{} after failure: {}",
                        r.totalRetries() + 1,
                        r.failure().getMessage()));

        return subscription
                .getSink()
                .asFlux()
                .publishOn(Schedulers.parallel())
                .doFinally(s -> subscription.onComplete())
                .doOnNext(publishResponse -> log.trace("Querying REST API: {}", publishResponse))
                .flatMap(publishResponse -> restApiClient
                        .retrieve(
                                TransactionByIdResponse.class,
                                "/transactions/{transactionId}",
                                toString(publishResponse.getTransactionId()))
                        .timeout(properties.getTimeout())
                        .retryWhen(retrySpec)
                        .doOnError(t -> subscription.onError(t))
                        .onErrorResume(e -> Mono.empty())
                        .doOnNext(subscription::onNext)
                        .map(transaction -> toResponse(subscription, publishResponse, transaction)))
                .take(properties.getLimit(), true)
                .take(properties.getDuration());
    }

    private SubscribeResponse toResponse(
            RestSubscription subscription, PublishResponse publishResponse, TransactionByIdResponse response) {
        Instant receivedTimestamp = Instant.now();
        var transaction = Iterables.getFirst(response.getTransactions(), null);
        Instant consensusTimestamp = null;

        if (transaction != null) {
            var timestamp = transaction.getConsensusTimestamp();
            var parts = StringUtils.split(timestamp, '.');
            if (parts != null && parts.length == 2) {
                consensusTimestamp = Instant.ofEpochSecond(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
            }
        }

        return SubscribeResponse.builder()
                .consensusTimestamp(consensusTimestamp)
                .publishedTimestamp(publishResponse.getRequest().getTimestamp())
                .receivedTimestamp(receivedTimestamp)
                .scenario(subscription)
                .build();
    }

    protected boolean shouldRetry(Throwable t) {
        return t instanceof WebClientResponseException webClientResponseException
                && webClientResponseException.getStatusCode() == HttpStatus.NOT_FOUND;
    }

    private String toString(TransactionId tid) {
        return tid.accountId + "-" + tid.validStart.getEpochSecond() + "-" + tid.validStart.getNano();
    }
}
