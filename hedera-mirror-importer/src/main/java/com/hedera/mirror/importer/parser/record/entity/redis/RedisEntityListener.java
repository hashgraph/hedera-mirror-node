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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.base.Charsets;
import java.util.Base64;
import javax.inject.Named;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.hash.Jackson2HashMapper;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import com.hedera.mirror.importer.domain.TopicMessage;
import com.hedera.mirror.importer.exception.ImporterException;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;

@Log4j2
@Named
public class RedisEntityListener implements EntityListener {

    private static final String CHANNEL = "topic_message";

    private final StreamOperations<String, String, Object> streamOperations;

    public RedisEntityListener(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> redisTemplate = new RedisTemplate();
        redisTemplate.setConnectionFactory(connectionFactory);
        redisTemplate.setHashKeySerializer(RedisSerializer.string());
        redisTemplate.setHashValueSerializer(new RedisSerializer<>() {
            @Override
            public byte[] serialize(Object object) throws SerializationException {
                String str = null;
                if (object instanceof byte[]) {
                    str = Base64.getEncoder().encodeToString((byte[]) object);
                } else if (object instanceof Number) {
                    Number number = (Number) object;
                    str = number.toString();
                } else if (object instanceof String) {
                    str = (String) object;
                } else {
                    throw new UnsupportedOperationException("");
                }
                return str.getBytes(Charsets.UTF_8);
            }

            @Override
            public String deserialize(byte[] bytes) throws SerializationException {
                return new String(bytes, Charsets.UTF_8);
            }
        });
        redisTemplate.setKeySerializer(RedisSerializer.string());
        redisTemplate.afterPropertiesSet();
        ObjectMapper objectMapper = JsonMapper.builder()
                .configure(JsonWriteFeature.WRITE_NUMBERS_AS_STRINGS, true)
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                .build();
        streamOperations = redisTemplate.opsForStream(new Jackson2HashMapper(objectMapper, false));
    }

    @Override
    public void onTopicMessage(TopicMessage topicMessage) throws ImporterException {
        streamOperations.add(StreamRecords.objectBacked(topicMessage).withStreamKey(CHANNEL));
    }
}
