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

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.topic.StreamMessage;
import com.hedera.mirror.common.domain.topic.TopicMessage;
import com.hedera.mirror.importer.parser.record.RecordStreamFileListener;
import com.hedera.mirror.importer.parser.record.entity.BatchPublisherTest;
import com.hedera.mirror.importer.parser.record.entity.ParserContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import reactor.core.publisher.Flux;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisPublisherIntegrationTest extends BatchPublisherTest {

    private final ReactiveRedisOperations<String, StreamMessage> redisOperations;
    private final List<RecordStreamFileListener> streamFileListeners;

    public RedisPublisherIntegrationTest(
            RedisPublisher redisPublisher,
            ParserContext parserContext,
            RedisProperties properties,
            ReactiveRedisOperations<String, StreamMessage> redisOperations,
            List<RecordStreamFileListener> streamFileListeners) {
        super(redisPublisher, parserContext, properties);
        this.redisOperations = redisOperations;
        this.streamFileListeners = streamFileListeners;
    }

    @Override
    protected Flux<TopicMessage> subscribe(EntityId topicId) {
        return redisOperations.listenToChannel("topic." + topicId.getId()).map(m -> (TopicMessage) m.getMessage());
    }

    @Test
    void publishesFirst() {
        assertThat(streamFileListeners).first().isEqualTo(batchPublisher);
    }
}
