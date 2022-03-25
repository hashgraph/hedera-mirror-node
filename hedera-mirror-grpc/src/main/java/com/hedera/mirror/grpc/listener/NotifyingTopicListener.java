package com.hedera.mirror.grpc.listener;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.pubsub.PgChannel;
import io.vertx.pgclient.pubsub.PgSubscriber;
import java.time.Duration;
import java.util.Objects;
import javax.inject.Named;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.util.retry.Retry;

import com.hedera.mirror.grpc.DbProperties;
import com.hedera.mirror.grpc.domain.TopicMessage;
import com.hedera.mirror.grpc.domain.TopicMessageFilter;

@Named
public class NotifyingTopicListener extends SharedTopicListener {

    final ObjectMapper objectMapper;
    private final DbProperties dbProperties;
    private final Mono<PgChannel> channel;
    private final Flux<TopicMessage> topicMessages;

    public NotifyingTopicListener(DbProperties dbProperties, ListenerProperties listenerProperties) {
        super(listenerProperties);
        this.dbProperties = dbProperties;
        objectMapper = new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        channel = Mono.defer(this::createChannel).cache();
        Duration interval = listenerProperties.getInterval();
        topicMessages = Flux.defer(() -> listen())
                .map(this::toTopicMessage)
                .filter(Objects::nonNull)
                .name("notify")
                .metrics()
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
        channel.subscribe(c -> c.handler(sink::tryEmitNext));

        log.info("Listening for messages");
        return sink.asFlux().doFinally(x -> unListen());
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

        PgSubscriber subscriber = PgSubscriber.subscriber(vertx, connectOptions)
                .reconnectPolicy(retries -> {
                    log.warn("Attempting reconnect");
                    return interval.toMillis() * Math.min(retries, 4);
                });

        // Connect asynchronously to avoid crashing the application on startup if the database is down
        vertx.setTimer(100L, v -> subscriber.connect(connectResult -> {
            if (connectResult.failed()) {
                throw new RuntimeException(connectResult.cause());
            }
            log.info("Connected to database");
        }));

        return Mono.just(subscriber.channel("topic_message"));
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
