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

package com.hedera.mirror.grpc.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.hedera.mirror.grpc.DbProperties;
import com.hedera.mirror.grpc.domain.TopicMessage;
import com.hedera.mirror.grpc.domain.TopicMessageFilter;
import io.micrometer.observation.ObservationRegistry;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.pubsub.PgChannel;
import io.vertx.pgclient.pubsub.PgSubscriber;
import jakarta.inject.Named;
import java.time.Duration;
import java.util.Objects;
import reactor.core.observability.micrometer.Micrometer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.util.retry.Retry;

@Named
public class NotifyingTopicListener extends SharedTopicListener {

    final ObjectMapper objectMapper;
    private final Mono<PgChannel> channel;
    private final DbProperties dbProperties;
    private final Flux<TopicMessage> topicMessages;

    public NotifyingTopicListener(
            DbProperties dbProperties, ListenerProperties listenerProperties, ObservationRegistry observationRegistry) {
        super(listenerProperties);
        this.dbProperties = dbProperties;
        objectMapper = new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        channel = Mono.defer(this::createChannel).cache();
        Duration interval = listenerProperties.getInterval();
        topicMessages = Flux.defer(this::listen)
                .map(this::toTopicMessage)
                .filter(Objects::nonNull)
                .name(METRIC)
                .tag(METRIC_TAG, "notify")
                .tap(Micrometer.observation(observationRegistry))
                .doOnError(t -> log.error("Error listening for messages", t))
                .retryWhen(Retry.backoff(Long.MAX_VALUE, interval).maxBackoff(interval.multipliedBy(4L)))
                .share();
    }

    @Override
    protected Flux<TopicMessage> getSharedListener(TopicMessageFilter filter) {
        return topicMessages;
    }

    private Flux<String> listen() {
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        return channel.doOnNext(c -> c.handler(sink::tryEmitNext))
                .doOnNext(c -> log.info("Listening for messages"))
                .flatMapMany(c -> sink.asFlux())
                .doFinally(s -> unListen());
    }

    private void unListen() {
        channel.subscribe(c -> c.handler(null));
        log.info("Stopped listening for messages");
    }

    private Mono<PgChannel> createChannel() {
        PgConnectOptions connectOptions = new PgConnectOptions()
                .setDatabase(dbProperties.getName())
                .setHost(dbProperties.getHost())
                .setPassword(dbProperties.getPassword())
                .setPort(dbProperties.getPort())
                .setSslMode(dbProperties.getSslMode())
                .setUser(dbProperties.getUsername());

        Duration interval = listenerProperties.getInterval();
        VertxOptions vertxOptions = new VertxOptions();
        vertxOptions.getFileSystemOptions().setFileCachingEnabled(false);
        vertxOptions.getFileSystemOptions().setClassPathResolvingEnabled(false);
        Vertx vertx = Vertx.vertx(vertxOptions);

        PgSubscriber subscriber = PgSubscriber.subscriber(vertx, connectOptions).reconnectPolicy(retries -> {
            log.warn("Attempting reconnect");
            return interval.toMillis() * Math.min(retries, 4);
        });

        return Mono.fromCompletionStage(subscriber.connect().toCompletionStage())
                .doOnSuccess(v -> log.info("Connected to database"))
                .thenReturn(subscriber.channel("topic_message"));
    }

    private TopicMessage toTopicMessage(String payload) {
        try {
            return objectMapper.readValue(payload, TopicMessage.class);
        } catch (Exception ex) {
            // Discard invalid messages. No need to propagate error and cause a reconnect.
            log.error("Error parsing message {}", payload, ex);
            return null;
        }
    }
}
