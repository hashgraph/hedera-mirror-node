/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.parser.record.entity.redis;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.topic.TopicMessage;
import com.hedera.mirror.importer.parser.record.entity.BatchEntityListenerTest;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import reactor.core.publisher.Flux;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisEntityListenerIntegrationTest extends BatchEntityListenerTest {

    private final ReactiveRedisOperations<String, TopicMessage> redisOperations;

    @Autowired
    public RedisEntityListenerIntegrationTest(
            RedisEntityListener entityListener,
            RedisProperties properties,
            ReactiveRedisConnectionFactory reactiveRedisConnectionFactory,
            RedisSerializer<?> redisSerializer) {
        super(entityListener, properties);
        var serializationContext = RedisSerializationContext.<String, TopicMessage>newSerializationContext(
                        redisSerializer)
                .build();
        this.redisOperations = new ReactiveRedisTemplate<>(reactiveRedisConnectionFactory, serializationContext);
    }

    @Override
    protected Flux<TopicMessage> subscribe(EntityId topicId) {
        return redisOperations.listenToChannel("topic." + topicId.getId()).map(m -> m.getMessage());
    }
}
