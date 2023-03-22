package com.hedera.mirror.monitor.subscribe.grpc;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import static io.grpc.Status.Code.INVALID_ARGUMENT;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.ArrayList;
import java.util.Collection;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

import com.hedera.mirror.monitor.ScenarioProperties;
import com.hedera.mirror.monitor.expression.ExpressionConverter;
import com.hedera.mirror.monitor.subscribe.MirrorSubscriber;
import com.hedera.mirror.monitor.subscribe.SubscribeProperties;
import com.hedera.mirror.monitor.subscribe.SubscribeResponse;

@Log4j2
@Named
@RequiredArgsConstructor
@SuppressWarnings("unchecked")
class GrpcSubscriber implements MirrorSubscriber {

    private final ExpressionConverter expressionConverter;
    private final GrpcClient grpcClient;
    private final SubscribeProperties subscribeProperties;
    private final Flux<GrpcSubscription> subscriptions = Flux.defer(this::createSubscriptions).cache();

    @Override
    public Flux<SubscribeResponse> subscribe() {
        return subscriptions.flatMap(this::clientSubscribe);
    }

    @Override
    public Flux<GrpcSubscription> getSubscriptions() {
        return subscriptions;
    }

    private Flux<GrpcSubscription> createSubscriptions() {
        Collection<GrpcSubscription> subscriptionList = new ArrayList<>();

        for (GrpcSubscriberProperties properties : subscribeProperties.getGrpc().values()) {
            if (subscribeProperties.isEnabled() && properties.isEnabled()) {
                String topicId = expressionConverter.convert(properties.getTopicId());
                properties.setTopicId(topicId);

                for (int i = 1; i <= properties.getSubscribers(); ++i) {
                    subscriptionList.add(new GrpcSubscription(i, properties));
                }
            }
        }

        return Flux.fromIterable(subscriptionList);
    }

    private Flux<SubscribeResponse> clientSubscribe(GrpcSubscription subscription) {
        GrpcSubscriberProperties subscriberProperties = subscription.getProperties();
        ScenarioProperties.RetryProperties retry = subscriberProperties.getRetry();

        return Flux.defer(() -> grpcClient.subscribe(subscription))
                .retryWhen(Retry.backoff(retry.getMaxAttempts(), retry.getMinBackoff())
                        .maxBackoff(retry.getMaxBackoff())
                        .filter(this::shouldRetry)
                        .doBeforeRetry(r -> log.warn("Retry attempt #{} after failure: {}",
                                r.totalRetries() + 1, StringUtils.substringBefore(r.failure().getMessage(), "\n"))))
                .doOnError(t -> log.error("Error subscribing {}: ", subscription, t))
                .doOnSubscribe(s -> log.info("Starting subscriber {}: {}", subscription, subscriberProperties));
    }

    // Don't retry client errors
    private boolean shouldRetry(Throwable t) {
        return getStatusCode(t) != INVALID_ARGUMENT;
    }

    private Status.Code getStatusCode(Throwable t) {
        if (t instanceof StatusRuntimeException statusRuntimeException) {
            return statusRuntimeException.getStatus().getCode();
        }
        return Status.Code.UNKNOWN;
    }
}
