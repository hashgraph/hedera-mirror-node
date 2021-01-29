package com.hedera.mirror.importer.parser.record.entity.redis;/*
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;

import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.domain.StreamMessage;
import com.hedera.mirror.importer.domain.TopicMessage;
import com.hedera.mirror.importer.parser.record.entity.EntityBatchCleanupEvent;
import com.hedera.mirror.importer.parser.record.entity.EntityBatchSaveEvent;

@ExtendWith(MockitoExtension.class)
class RedisEntityListenerMockitoTest {

    @Mock
    private RedisOperations<String, StreamMessage> redisOperations;

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    private RedisEntityListener entityListener;

    private long consensusTimestamp = 1;

    @BeforeEach
    void setup() {
        entityListener = new RedisEntityListener(new MirrorProperties(),
                new RedisProperties(), redisOperations, meterRegistry);
        entityListener.init();
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

        // when
        when(redisOperations.executePipelined(any(SessionCallback.class))).then((callback) -> {
            latch.await();
            return null;
        });

        // drive entityListener to handle topic messages in a different thread cause it will block
        new Thread(() -> {
            try {
                for (TopicMessage message : messages) {
                    submitAndSave(message);
                    entityListener.onCleanup(new EntityBatchCleanupEvent(this));
                    saveCount.getAndIncrement();
                }
            } catch (InterruptedException e) {
                // ignore
            }
        }).start();

        //then
        //Wait for separate thread to catch up.
        //Thread is blocked because queue is full, and publisher is blocked on first message.
        verify(redisOperations, timeout(500).times(1))
                .executePipelined(any(SessionCallback.class));
        assertThat(saveCount.get()).isEqualTo(RedisEntityListener.TASK_QUEUE_SIZE + 1);

        latch.countDown();
        //All messages should be queued and published
        verify(redisOperations, timeout(500).times(RedisEntityListener.TASK_QUEUE_SIZE + 2))
                .executePipelined(any(SessionCallback.class));
        assertThat(saveCount.get()).isEqualTo(RedisEntityListener.TASK_QUEUE_SIZE + 2);
    }

    @Test
    void onDuplicateTopicMessages() throws InterruptedException {
        TopicMessage topicMessage1 = topicMessage();
        TopicMessage topicMessage2 = topicMessage();
        TopicMessage topicMessage3 = topicMessage();

        submitAndSave(topicMessage1);
        submitAndSave(topicMessage2);
        verify(redisOperations, timeout(1000).times(2))
                .executePipelined(any(SessionCallback.class));
        submitAndSave(topicMessage1);
        submitAndSave(topicMessage2);
        verify(redisOperations, timeout(1000).times(2))
                .executePipelined(any(SessionCallback.class));
        submitAndSave(topicMessage3);
        entityListener.onCleanup(new EntityBatchCleanupEvent(this));
        verify(redisOperations, timeout(1000).times(3))
                .executePipelined(any(SessionCallback.class));
    }

    private TopicMessage topicMessage() {
        TopicMessage topicMessage = new TopicMessage();
        topicMessage.setConsensusTimestamp(consensusTimestamp++);
        return topicMessage;
    }

    private void submitAndSave(TopicMessage topicMessage) throws InterruptedException {
        entityListener.onTopicMessage(topicMessage);
        entityListener.onSave(new EntityBatchSaveEvent(this));
    }
}
