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

package com.hedera.mirror.importer.parser.record.entity.redis;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.base.Stopwatch;
import com.hedera.mirror.common.domain.topic.StreamMessage;
import com.hedera.mirror.common.domain.topic.TopicMessage;
import com.hedera.mirror.importer.exception.ImporterException;
import com.hedera.mirror.importer.parser.record.entity.BatchEntityListener;
import com.hedera.mirror.importer.parser.record.entity.ConditionOnEntityRecordParser;
import com.hedera.mirror.importer.parser.record.entity.EntityBatchCleanupEvent;
import com.hedera.mirror.importer.parser.record.entity.EntityBatchSaveEvent;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;

@ConditionOnEntityRecordParser
@Log4j2
@Named
@Order(1)
@RequiredArgsConstructor
public class RedisEntityListener implements BatchEntityListener {

    private static final String TOPIC_FORMAT = "topic.%d";

    private final RedisProperties redisProperties;
    private final RedisOperations<String, StreamMessage> redisOperations;
    private final MeterRegistry meterRegistry;
    private final LoadingCache<Long, String> channelNames =
            Caffeine.newBuilder().maximumSize(1000L).build(this::getChannelName);

    private AtomicLong lastConsensusTimestamp;
    private Timer timer;
    private List<TopicMessage> topicMessages;
    private BlockingQueue<List<TopicMessage>> topicMessagesQueue;

    @PostConstruct
    void init() {
        lastConsensusTimestamp = new AtomicLong(0);
        timer = Timer.builder("hedera.mirror.importer.publish.duration")
                .description("The amount of time it took to publish the domain entity")
                .tag("entity", TopicMessage.class.getSimpleName())
                .tag("type", "redis")
                .register(meterRegistry);
        topicMessages = new ArrayList<>();
        topicMessagesQueue = new ArrayBlockingQueue<>(redisProperties.getQueueCapacity());

        Executor executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                while (true) {
                    publish(topicMessagesQueue.take());
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        });
    }

    @Override
    public boolean isEnabled() {
        return redisProperties.isEnabled();
    }

    @Override
    public void onTopicMessage(TopicMessage topicMessage) throws ImporterException {
        long consensusTimestamp = topicMessage.getConsensusTimestamp();
        if (consensusTimestamp <= lastConsensusTimestamp.get()) {
            return;
        }

        lastConsensusTimestamp.set(consensusTimestamp);
        topicMessages.add(topicMessage);
    }

    @Override
    @EventListener
    public void onSave(EntityBatchSaveEvent event) throws InterruptedException {
        if (!isEnabled() || topicMessages.isEmpty()) {
            return;
        }

        List<TopicMessage> latestMessageBatch = topicMessages;
        topicMessages = new ArrayList<>();
        if (!topicMessagesQueue.offer(latestMessageBatch)) {
            log.warn("topicMessagesQueue is full, will block until space is available");
            topicMessagesQueue.put(latestMessageBatch);
        }
    }

    @Override
    @EventListener
    public void onCleanup(EntityBatchCleanupEvent event) {
        log.debug("Finished clearing {} messages", topicMessages.size());
        topicMessages.clear();
    }

    private void publish(List<TopicMessage> messages) {
        try {
            Stopwatch stopwatch = Stopwatch.createStarted();
            timer.record(() -> redisOperations.executePipelined(callback(messages)));
            log.info("Finished notifying {} messages in {}", messages.size(), stopwatch);
        } catch (Exception e) {
            log.error("Unable to publish to redis", e);
        }
    }

    // Batch send using Redis pipelining
    private SessionCallback<Object> callback(List<TopicMessage> messages) {
        return new SessionCallback<>() {
            @Override
            public <K, V> Object execute(RedisOperations<K, V> operations) {
                for (TopicMessage topicMessage : messages) {
                    String channel = channelNames.get(topicMessage.getTopicId().getId());
                    redisOperations.convertAndSend(channel, topicMessage);
                }
                return null;
            }
        };
    }

    private String getChannelName(Long id) {
        return String.format(TOPIC_FORMAT, id);
    }
}
