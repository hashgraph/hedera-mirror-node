package com.hedera.mirror.grpc.listener;

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

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Named;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.ReactiveSubscription.Message;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.data.redis.listener.Topic;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.RedisSerializer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import com.hedera.mirror.grpc.domain.TopicMessage;
import com.hedera.mirror.grpc.domain.TopicMessageFilter;

@Lazy
@Log4j2
@Named
public class RedisTopicListener extends SharedTopicListener {

    private final Mono<ReactiveRedisMessageListenerContainer> container;
    private final SerializationPair<String> channelSerializer;
    private final SerializationPair<TopicMessage> messageSerializer;
    private final Map<String, Flux<TopicMessage>> topicMessages; // Topic name to active subscription

    public RedisTopicListener(ListenerProperties listenerProperties,
                              ReactiveRedisConnectionFactory connectionFactory,
                              RedisSerializer<?> redisSerializer) {
        super(listenerProperties);
        this.channelSerializer = SerializationPair.fromSerializer(RedisSerializer.string());
        this.messageSerializer = (SerializationPair<TopicMessage>) SerializationPair.fromSerializer(redisSerializer);
        this.topicMessages = new ConcurrentHashMap<>();

        // Workaround Spring DATAREDIS-1208 by lazily starting connection once with retry
        Duration interval = listenerProperties.getInterval();
        this.container = Mono.defer(() -> Mono.just(new ReactiveRedisMessageListenerContainer(connectionFactory)))
                .doOnError(t -> log.error("Error connecting to Redis: ", t))
                .doOnSubscribe(s -> log.info("Attempting to connect to Redis"))
                .doOnSuccess(c -> log.info("Connected to Redis"))
                .retryWhen(Retry.backoff(Long.MAX_VALUE, interval).maxBackoff(interval.multipliedBy(8)))
                .cache();
    }

    @Override
    protected Flux<TopicMessage> getSharedListener(TopicMessageFilter filter) {
        Topic topic = getTopic(filter);
        return topicMessages.computeIfAbsent(topic.getTopic(), key -> subscribe(topic));
    }

    private Topic getTopic(TopicMessageFilter filter) {
        return ChannelTopic.of(String.format("topic.%d", filter.getTopicId()));
    }

    private Flux<TopicMessage> subscribe(Topic topic) {
        Duration interval = listenerProperties.getInterval();

        return container.flatMapMany(r -> r.receive(Arrays.asList(topic), channelSerializer, messageSerializer))
                .map(Message::getMessage)
                .name("redis")
                .metrics()
                .doOnCancel(() -> unsubscribe(topic))
                .doOnComplete(() -> unsubscribe(topic))
                .doOnError(t -> log.error("Error listening for messages", t))
                .doOnSubscribe(s -> log.info("Creating shared subscription to {}", topic))
                .retryWhen(Retry.backoff(Long.MAX_VALUE, interval).maxBackoff(interval.multipliedBy(4L)))
                .share();
    }

    private void unsubscribe(Topic topic) {
        topicMessages.remove(topic.getTopic());
        log.info("Unsubscribing from {}", topic);
    }
}
