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

import javax.annotation.Resource;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import reactor.core.publisher.Flux;

import com.hedera.mirror.grpc.domain.StreamMessage;
import com.hedera.mirror.grpc.domain.TopicMessage;

@Log4j2
public class RedisTopicListenerTest extends AbstractSharedTopicListenerTest {

    @Resource
    private ReactiveRedisOperations<String, StreamMessage> redisOperations;

    @Override
    protected ListenerProperties.ListenerType getType() {
        return ListenerProperties.ListenerType.REDIS;
    }

    @Override
    protected void publish(Flux<TopicMessage> publisher) {
        publisher.concatMap(t -> redisOperations.convertAndSend(getTopic(t), t)).blockLast();
    }

    private String getTopic(TopicMessage topicMessage) {
        return "topic.0." + topicMessage.getRealmNum() + "." + topicMessage.getTopicNum();
    }
}
