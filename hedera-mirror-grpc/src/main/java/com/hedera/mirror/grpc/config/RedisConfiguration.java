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

package com.hedera.mirror.grpc.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hedera.mirror.grpc.domain.TopicMessage;
import org.msgpack.jackson.dataformat.MessagePackFactory;
import org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@AutoConfigureBefore(RedisAutoConfiguration.class)
@AutoConfigureAfter({MetricsAutoConfiguration.class, CompositeMeterRegistryAutoConfiguration.class})
@Configuration
class RedisConfiguration {

    @Bean
    RedisSerializer<TopicMessage> redisSerializer() {
        return new Jackson2JsonRedisSerializer<>(new ObjectMapper(new MessagePackFactory()), TopicMessage.class);
    }

    @Bean
    ReactiveRedisOperations<String, TopicMessage> reactiveRedisOperations(
            ReactiveRedisConnectionFactory connectionFactory) {
        var serializationContext = RedisSerializationContext.<String, TopicMessage>newSerializationContext()
                .key(StringRedisSerializer.UTF_8)
                .value(redisSerializer())
                .hashKey(StringRedisSerializer.UTF_8)
                .hashValue(redisSerializer())
                .build();
        return new ReactiveRedisTemplate<>(connectionFactory, serializationContext);
    }
}
