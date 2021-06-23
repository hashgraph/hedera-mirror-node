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
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;

import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.StreamMessage;
import com.hedera.mirror.importer.domain.TopicMessage;
import com.hedera.mirror.importer.parser.record.entity.EntityBatchCleanupEvent;
import com.hedera.mirror.importer.parser.record.entity.EntityBatchSaveEvent;

@ExtendWith(MockitoExtension.class)
class RedisEntityListenerTest {

    private static final long TIMEOUT_MILLIS = 2000L;

    @Mock
    private RedisOperations<String, StreamMessage> redisOperations;

    private RedisEntityListener entityListener;

    private long consensusTimestamp = 1;

    private RedisProperties redisProperties;

    @BeforeEach
    void setup() {
        redisProperties = new RedisProperties();
        entityListener = new RedisEntityListener(new MirrorProperties(), redisProperties, redisOperations,
                new SimpleMeterRegistry());
        entityListener.init();
    }

    @Test
    void onSlowPublish() {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger saveCount = new AtomicInteger(0);

        // given
        List<TopicMessage> messages = new ArrayList<>();
        for (int i = 0; i < redisProperties.getQueueCapacity() + 2; i++) {
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
        //Thread is blocked because queue is full, and publisher is blocked on first message.
        verify(redisOperations, timeout(TIMEOUT_MILLIS * 5).times(1))
                .executePipelined(any(SessionCallback.class));
        assertThat(saveCount.get()).isEqualTo(redisProperties.getQueueCapacity() + 1);

        latch.countDown();
        //All messages should be queued and published
        verify(redisOperations, timeout(TIMEOUT_MILLIS * 5).times(redisProperties.getQueueCapacity() + 2))
                .executePipelined(any(SessionCallback.class));
        assertThat(saveCount.get()).isEqualTo(redisProperties.getQueueCapacity() + 2);
    }

    @Test
    void onDuplicateTopicMessages() throws InterruptedException {
        TopicMessage topicMessage1 = topicMessage();
        TopicMessage topicMessage2 = topicMessage();
        TopicMessage topicMessage3 = topicMessage();

        //submitAndSave two messages, verify publish logic called twice
        submitAndSave(topicMessage1);
        submitAndSave(topicMessage2);
        verify(redisOperations, timeout(TIMEOUT_MILLIS).times(2))
                .executePipelined(any(SessionCallback.class));

        //submitAndSave two duplicate messages, verify publish was not attempted
        Mockito.reset(redisOperations);
        submitAndSave(topicMessage1);
        submitAndSave(topicMessage2);
        verify(redisOperations, timeout(TIMEOUT_MILLIS).times(0))
                .executePipelined(any(SessionCallback.class));

        //submitAndSave third new unique message, verify publish called once.
        Mockito.reset(redisOperations);
        submitAndSave(topicMessage3);
        entityListener.onCleanup(new EntityBatchCleanupEvent(this));
        verify(redisOperations, timeout(TIMEOUT_MILLIS).times(1))
                .executePipelined(any(SessionCallback.class));
    }

    protected TopicMessage topicMessage() {
        TopicMessage topicMessage = new TopicMessage();
        topicMessage.setChunkNum(1);
        topicMessage.setChunkTotal(2);
        topicMessage.setConsensusTimestamp(consensusTimestamp++);
        topicMessage.setMessage("test message".getBytes());
        topicMessage.setPayerAccountId(EntityId.of("0.1.1000", EntityTypeEnum.ACCOUNT));
        topicMessage.setRealmNum(0);
        topicMessage.setRunningHash("running hash".getBytes());
        topicMessage.setRunningHashVersion(2);
        topicMessage.setSequenceNumber(1);
        topicMessage.setTopicNum(1001);
        topicMessage.setValidStartTimestamp(4L);
        return topicMessage;
    }

    private void submitAndSave(TopicMessage topicMessage) throws InterruptedException {
        entityListener.onTopicMessage(topicMessage);
        entityListener.onSave(new EntityBatchSaveEvent(this));
    }
}
