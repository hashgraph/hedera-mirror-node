/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.monitor.subscribe.grpc;

import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.SubscriptionHandle;
import com.hedera.hashgraph.sdk.TopicMessage;
import com.hedera.hashgraph.sdk.TopicMessageQuery;
import com.hedera.mirror.monitor.MonitorProperties;
import com.hedera.mirror.monitor.subscribe.SubscribeProperties;
import com.hedera.mirror.monitor.subscribe.SubscribeResponse;
import com.hedera.mirror.monitor.util.Utility;
import jakarta.inject.Named;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.extern.log4j.Log4j2;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

@Log4j2
@Named
class GrpcClientSDK implements GrpcClient {

    private final Flux<Client> clients;
    private final MonitorProperties monitorProperties;
    private final SecureRandom secureRandom;
    private final SubscribeProperties subscribeProperties;

    GrpcClientSDK(MonitorProperties monitorProperties, SubscribeProperties subscribeProperties) {
        this.monitorProperties = monitorProperties;
        this.secureRandom = new SecureRandom();
        this.subscribeProperties = subscribeProperties;
        clients = Flux.range(0, subscribeProperties.getClients())
                .flatMap(i -> Flux.defer(this::client))
                .cache();

        String endpoint = monitorProperties.getMirrorNode().getGrpc().getEndpoint();
        log.info("Connecting {} clients to {}", subscribeProperties.getClients(), endpoint);
    }

    @Override
    public Flux<SubscribeResponse> subscribe(GrpcSubscription subscription) {
        int clientIndex = secureRandom.nextInt(subscribeProperties.getClients());
        log.info("Starting '{}' scenario to client {}", subscription, clientIndex);
        return clients.elementAt(clientIndex).flatMapMany(client -> subscribeToClient(client, subscription));
    }

    private Flux<SubscribeResponse> subscribeToClient(Client client, GrpcSubscription subscription) {
        Sinks.Many<TopicMessage> sink = Sinks.many().multicast().directBestEffort();

        TopicMessageQuery topicMessageQuery = subscription.getTopicMessageQuery();
        topicMessageQuery.setCompletionHandler(sink::tryEmitComplete);
        topicMessageQuery.setErrorHandler((throwable, topicMessage) -> sink.tryEmitError(throwable));
        topicMessageQuery.setMaxAttempts(0); // Disable since we use our own retry logic to capture errors
        SubscriptionHandle subscriptionHandle = topicMessageQuery.subscribe(client, sink::tryEmitNext);

        return sink.asFlux()
                .publishOn(Schedulers.parallel())
                .doFinally(s -> subscriptionHandle.unsubscribe())
                .doOnComplete(subscription::onComplete)
                .doOnError(subscription::onError)
                .doOnNext(subscription::onNext)
                .map(t -> toResponse(subscription, t));
    }

    private SubscribeResponse toResponse(GrpcSubscription subscription, TopicMessage topicMessage) {
        Instant receivedTimestamp = Instant.now();
        Instant publishedTimestamp = Utility.getTimestamp(topicMessage.contents);

        if (publishedTimestamp == null) {
            log.warn(
                    "{} Invalid published timestamp for message with consensus timestamp {}",
                    subscription,
                    topicMessage.consensusTimestamp);
        }

        return SubscribeResponse.builder()
                .consensusTimestamp(topicMessage.consensusTimestamp)
                .publishedTimestamp(publishedTimestamp)
                .receivedTimestamp(receivedTimestamp)
                .scenario(subscription)
                .build();
    }

    @Override
    public void close() {
        log.warn("Closing {} clients", subscribeProperties.getClients());
        clients.subscribe(client -> {
            try {
                client.close();
            } catch (Exception e) {
                // Ignore
            }
        });
    }

    private Mono<Client> client() {
        String endpoint = monitorProperties.getMirrorNode().getGrpc().getEndpoint();

        try {
            Client client = Client.forNetwork(Map.of());
            client.setMirrorNetwork(List.of(endpoint));
            return Mono.just(client);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Unable to initialize SDK client to " + endpoint);
        }
    }
}
