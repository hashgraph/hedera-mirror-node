package com.hedera.mirror.grpc.listener;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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
import java.time.Duration;
import java.util.Map;
import javax.inject.Named;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.hash.HashMapper;
import org.springframework.data.redis.stream.StreamReceiver;
import org.springframework.data.redis.stream.StreamReceiver.StreamReceiverOptions;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import com.hedera.mirror.grpc.domain.TopicMessage;
import com.hedera.mirror.grpc.domain.TopicMessageFilter;

@Log4j2
@Named
public class RedisTopicListener implements TopicListener {

    private static final String STREAM = "topic_message";
    private final StreamReceiver<String, ObjectRecord<String, TopicMessage>> receiver;

    public RedisTopicListener(ReactiveRedisConnectionFactory connectionFactory) {
        ObjectMapper objectMapper = new ObjectMapper();//.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
        StreamReceiverOptions<String, ObjectRecord<String, TopicMessage>> options = StreamReceiverOptions.builder()
                .pollTimeout(Duration.ofMillis(100))
                .batchSize(1000)
                //.hashValueSerializer(RedisSerializationContext.SerializationPair
                //.fromSerializer(new Jackson2JsonRedisSerializer<>(TopicMessage.class)))
                .objectMapper(new HashMapper<>() {
                    @Override
                    public Map<Object, Object> toHash(Object object) {
                        return objectMapper.convertValue(object, Map.class);
                    }

                    @Override
                    public Object fromHash(Map<Object, Object> hash) {
                        return objectMapper.convertValue(hash, TopicMessage.class);
                    }
                })
                .targetType(TopicMessage.class)
                .build();

        receiver = StreamReceiver.create(connectionFactory, options);
    }

    @Override
    public Flux<TopicMessage> listen(TopicMessageFilter filter) {
        return receiver.receive(StreamOffset
                .create(STREAM, ReadOffset
                        .from(RecordId.of(0, filter.getStartTime()
                                .getNano() / 1_000_000))))
                .publishOn(Schedulers.boundedElastic())
                .map(r -> r.getValue())
                //.doOnNext(s -> log.info("next: {}", s))
                //.map(s -> new TopicMessage())
                .filter(t -> filterMessage(t, filter))
                .name("notify")
                .metrics()
                .doOnError(t -> log.error("Error listening for messages", t))
                .doOnSubscribe(s -> log.info("Subscribing: {}", filter))
                .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1)));
    }

    private boolean filterMessage(TopicMessage message, TopicMessageFilter filter) {
        return message.getRealmNum() == filter.getRealmNum() &&
                message.getTopicNum() == filter.getTopicNum();// &&
        // message.getConsensusTimestamp() >= filter.getStartTimeLong();
    }
}
