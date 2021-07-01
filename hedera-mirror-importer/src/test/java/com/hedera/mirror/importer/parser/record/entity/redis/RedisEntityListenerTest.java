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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.Uninterruptibles;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.StreamMessage;
import com.hedera.mirror.importer.domain.TopicMessage;
import com.hedera.mirror.importer.parser.record.entity.EntityBatchCleanupEvent;
import com.hedera.mirror.importer.parser.record.entity.EntityBatchSaveEvent;

@ExtendWith(MockitoExtension.class)
class RedisEntityListenerTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(2L);

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
        // given
        int publishCount = redisProperties.getQueueCapacity() + 2;
        Sinks.Many<Object> sink = Sinks.many().multicast().directBestEffort();
        Flux<Integer> publisher = Flux.range(1, publishCount).doOnNext(i -> {
            submitAndSave(topicMessage());
            entityListener.onCleanup(new EntityBatchCleanupEvent(this));
        });

        // when
        when(redisOperations.executePipelined(any(SessionCallback.class))).then((callback) -> {
            Uninterruptibles.sleepUninterruptibly(Duration.ofMillis(50L));
            sink.tryEmitNext(callback);
            return null;
        });

        //then
        StepVerifier redisVerifier = sink.asFlux()
                .subscribeOn(Schedulers.parallel())
                .as(StepVerifier::create)
                .expectNextCount(publishCount)
                .thenCancel()
                .verifyLater();

        publisher.publishOn(Schedulers.parallel())
                .as(StepVerifier::create)
                .expectNextCount(publishCount)
                .expectComplete()
                .verify(TIMEOUT);

        redisVerifier.verify(TIMEOUT);
        verify(redisOperations, timeout(TIMEOUT.toMillis() * 5).times(publishCount))
                .executePipelined(any(SessionCallback.class));
    }

    @Test
    void onDuplicateTopicMessages() throws InterruptedException {
        TopicMessage topicMessage1 = topicMessage();
        TopicMessage topicMessage2 = topicMessage();
        TopicMessage topicMessage3 = topicMessage();

        //submitAndSave two messages, verify publish logic called twice
        submitAndSave(topicMessage1);
        submitAndSave(topicMessage2);
        verify(redisOperations, timeout(TIMEOUT.toMillis()).times(2))
                .executePipelined(any(SessionCallback.class));

        //submitAndSave two duplicate messages, verify publish was not attempted
        Mockito.reset(redisOperations);
        submitAndSave(topicMessage1);
        submitAndSave(topicMessage2);
        verify(redisOperations, timeout(TIMEOUT.toMillis()).times(0))
                .executePipelined(any(SessionCallback.class));

        //submitAndSave third new unique message, verify publish called once.
        Mockito.reset(redisOperations);
        submitAndSave(topicMessage3);
        entityListener.onCleanup(new EntityBatchCleanupEvent(this));
        verify(redisOperations, timeout(TIMEOUT.toMillis()).times(1))
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

    private void submitAndSave(TopicMessage topicMessage) {
        try {
            entityListener.onTopicMessage(topicMessage);
            entityListener.onSave(new EntityBatchSaveEvent(this));
        } catch (Exception e) {
            Thread.currentThread().interrupt();
        }
    }
}
