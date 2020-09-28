package com.hedera.mirror.importer.parser.record.entity.redis;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import reactor.core.publisher.DirectProcessor;
import reactor.core.publisher.Flux;

import com.hedera.mirror.importer.domain.StreamMessage;
import com.hedera.mirror.importer.domain.TopicMessage;
import com.hedera.mirror.importer.parser.record.entity.BatchEntityListenerTest;

public class RedisEntityListenerTest extends BatchEntityListenerTest {

    private final RedisOperations<String, StreamMessage> redisOperations;

    @Autowired
    public RedisEntityListenerTest(RedisEntityListener entityListener, RedisProperties properties,
                                   RedisOperations<String, StreamMessage> redisOperations) {
        super(entityListener, properties);
        this.redisOperations = redisOperations;
    }

    @Override
    protected Flux<TopicMessage> subscribe(long topicNum) {
        DirectProcessor<TopicMessage> directProcessor = DirectProcessor.create();
        RedisSerializer stringSerializer = ((RedisTemplate<String, ?>) redisOperations).getStringSerializer();
        RedisSerializer<TopicMessage> serializer = (RedisSerializer<TopicMessage>) redisOperations.getValueSerializer();

        RedisCallback<TopicMessage> redisCallback = connection -> {
            byte[] channel = stringSerializer.serialize("topic.0.0." + topicNum);
            connection.subscribe((message, pattern) -> directProcessor
                    .onNext(serializer.deserialize(message.getBody())), channel);
            return null;
        };

        redisOperations.execute(redisCallback);
        return directProcessor;
    }
}
