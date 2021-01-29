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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
import org.mockito.stubbing.Answer;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;

import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.domain.StreamMessage;
import com.hedera.mirror.importer.domain.TopicMessage;
import com.hedera.mirror.importer.parser.record.entity.EntityBatchCleanupEvent;
import com.hedera.mirror.importer.parser.record.entity.EntityBatchSaveEvent;

@ExtendWith(MockitoExtension.class)
public class RedisEntityListenerMockitoTest {

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
    void onSlowPublish() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger saveCount = new AtomicInteger(0);

        // given
        List<TopicMessage> messages = new ArrayList<>();
        for (int i = 0; i < RedisEntityListener.TASK_QUEUE_SIZE + 2; i++) {
            messages.add(topicMessage());
        }

        // when
        doAnswer((Answer<Void>) invocation -> {
            // block publish until latch count reaches 0
            latch.await();
            return null;
        }).when(redisOperations).executePipelined(any(SessionCallback.class));

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

        //Wait for separate thread to catch up.
        Thread.sleep(500);
        //Thread should be blocked at message 10, with the first publish still waiting.
        assertThat(saveCount.get()).isEqualTo(RedisEntityListener.TASK_QUEUE_SIZE + 1);
        verify(redisOperations, times(1))
                .executePipelined(any(SessionCallback.class));

        latch.countDown();
        Thread.sleep(500);
        //All messages should be queued and published
        assertThat(saveCount.get()).isEqualTo(RedisEntityListener.TASK_QUEUE_SIZE + 2);
        verify(redisOperations, times(RedisEntityListener.TASK_QUEUE_SIZE + 2))
                .executePipelined(any(SessionCallback.class));
    }

    private TopicMessage topicMessage() {
        TopicMessage topicMessage = new TopicMessage();
        topicMessage.setConsensusTimestamp(consensusTimestamp++);
        return topicMessage;
    }
}
