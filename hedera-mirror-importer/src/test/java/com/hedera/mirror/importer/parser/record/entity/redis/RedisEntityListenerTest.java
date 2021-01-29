package com.hedera.mirror.importer.parser.record.entity.redis;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.serializer.RedisSerializer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.UnicastProcessor;
import reactor.test.StepVerifier;

import com.hedera.mirror.importer.domain.StreamMessage;
import com.hedera.mirror.importer.domain.TopicMessage;
import com.hedera.mirror.importer.parser.record.entity.BatchEntityListenerTest;
import com.hedera.mirror.importer.parser.record.entity.EntityBatchCleanupEvent;
import com.hedera.mirror.importer.parser.record.entity.EntityBatchSaveEvent;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisEntityListenerTest extends BatchEntityListenerTest {

    private final RedisOperations<String, StreamMessage> redisOperations;

    @Autowired
    public RedisEntityListenerTest(RedisEntityListener entityListener, RedisProperties properties,
                                   RedisOperations<String, StreamMessage> redisOperations) {
        super(entityListener, properties);
        this.redisOperations = redisOperations;
    }

    @Override
    protected Flux<TopicMessage> subscribe(long topicNum) {
        UnicastProcessor<TopicMessage> processor = UnicastProcessor.create();
        RedisSerializer stringSerializer = ((RedisTemplate<String, ?>) redisOperations).getStringSerializer();
        RedisSerializer<TopicMessage> serializer = (RedisSerializer<TopicMessage>) redisOperations.getValueSerializer();

        RedisCallback<TopicMessage> redisCallback = connection -> {
            byte[] channel = stringSerializer.serialize("topic.0.0." + topicNum);
            connection.subscribe((message, pattern) -> {
                processor.onNext(serializer.deserialize(message.getBody()));
            }, channel);
            return null;
        };

        redisOperations.execute(redisCallback);
        return processor;
    }

    @Test
    void onDuplicateTopicMessages() throws InterruptedException {
        // given
        TopicMessage topicMessage1 = topicMessage();
        TopicMessage topicMessage2 = topicMessage();
        TopicMessage topicMessage3 = topicMessage();
        Flux<TopicMessage> topicMessages = subscribe(topicMessage1.getTopicNum());

        // when
        entityListener.onTopicMessage(topicMessage1);
        entityListener.onTopicMessage(topicMessage2);
        entityListener.onTopicMessage(topicMessage1); // duplicate
        entityListener.onTopicMessage(topicMessage2); // duplicate
        entityListener.onTopicMessage(topicMessage3);
        entityListener.onSave(new EntityBatchSaveEvent(this));
        entityListener.onCleanup(new EntityBatchCleanupEvent(this));

        // then
        topicMessages.as(StepVerifier::create)
                .expectNext(topicMessage1, topicMessage2, topicMessage3)
                .thenCancel()
                .verify(Duration.ofMillis(1000));
    }

    @Test
    void onSlowPublish() {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger saveCount = new AtomicInteger(0);

        // given
        List<TopicMessage> messages = new ArrayList<>();
        for (int i = 0; i < RedisEntityListener.TASK_QUEUE_SIZE + 2; i++) {
            messages.add(topicMessage());
        }
        Flux<TopicMessage> topicMessageFlux = subscribe(messages.get(0).getTopicNum());

        // when
//        doAnswer((Answer<Void>) invocation -> {
//            // block publish until latch count reaches 0
//            latch.await();
//            invocation.callRealMethod();
//            return null;
//        }).
        when(redisOperations.executePipelined(any(SessionCallback.class))).then((x) -> {
            latch.await();
//            return x.callRealMethod();
            return null;
        });

        // drive entityListener to handle topic messages in a different thread cause it will block
        new Thread(() -> {
            try {
                for (TopicMessage message : messages) {
                    entityListener.onTopicMessage(message);
                    entityListener.onSave(new EntityBatchSaveEvent(this));
                    entityListener.onCleanup(new EntityBatchCleanupEvent(this));
                    saveCount.getAndIncrement();
                }
            } catch (InterruptedException e) {
                // ignore
            }
        }).start();

        // then
        topicMessageFlux
                .as(StepVerifier::create)
                .thenAwait(Duration.ofMillis(500))
                .then(() -> assertThat(saveCount.get()).isEqualTo(RedisEntityListener.TASK_QUEUE_SIZE + 1))
                .expectNextCount(0)
                .then(latch::countDown)
                .expectNextSequence(messages)
                .thenCancel()
                .verify(Duration.ofMillis(1000));

        // reset the stub
        doCallRealMethod().when(redisOperations).convertAndSend(anyString(), any(Object.class));
    }

    @TestConfiguration
    static class ContextConfiguration {

        @Bean
        @Primary
        public RedisOperations<String, StreamMessage> redisOpsSpy(RedisOperations<String, StreamMessage> redisOperations) {
            return Mockito.spy(redisOperations);
        }
    }
}
