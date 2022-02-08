package com.hedera.mirror.importer.parser.record.entity.redis;

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

import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import com.hedera.mirror.common.domain.topic.StreamMessage;
import com.hedera.mirror.common.domain.topic.TopicMessage;
import com.hedera.mirror.importer.parser.record.entity.BatchEntityListenerTest;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisEntityListenerIntegrationTest extends BatchEntityListenerTest {

    private final RedisOperations<String, StreamMessage> redisOperations;

    @Autowired
    public RedisEntityListenerIntegrationTest(RedisEntityListener entityListener, RedisProperties properties,
                                              RedisOperations<String, StreamMessage> redisOperations) {
        super(entityListener, properties);
        this.redisOperations = redisOperations;
    }

    @Override
    protected Flux<TopicMessage> subscribe(long topicId) {
        Sinks.Many<TopicMessage> sink = Sinks.many().unicast().onBackpressureBuffer();
        RedisSerializer stringSerializer = ((RedisTemplate<String, ?>) redisOperations).getStringSerializer();
        RedisSerializer<TopicMessage> serializer = (RedisSerializer<TopicMessage>) redisOperations.getValueSerializer();

        RedisCallback<TopicMessage> redisCallback = connection -> {
            byte[] channel = stringSerializer.serialize("topic." + topicId);
            connection.subscribe((message, pattern) -> sink.emitNext(serializer.deserialize(message.getBody()),
                    Sinks.EmitFailureHandler.FAIL_FAST), channel);
            return null;
        };

        redisOperations.execute(redisCallback);
        return sink.asFlux();
    }
}
