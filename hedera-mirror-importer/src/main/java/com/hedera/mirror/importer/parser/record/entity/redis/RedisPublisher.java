/*
 * Copyright (C) 2020-2025 Hedera Hashgraph, LLC
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
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.importer.parser.record.entity.BatchPublisher;
import com.hedera.mirror.importer.parser.record.entity.ConditionOnEntityRecordParser;
import com.hedera.mirror.importer.parser.record.entity.ParserContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.inject.Named;
import java.util.Collection;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import lombok.CustomLog;
import lombok.SneakyThrows;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;

@ConditionOnEntityRecordParser
@CustomLog
@Named
@Order(0) // Triggering the async publishing before other operations can reduce latency
public class RedisPublisher implements BatchPublisher {

    private static final String TOPIC_FORMAT = "topic.%d";

    private final LoadingCache<Long, String> channelNames;
    private final ParserContext parserContext;
    private final RedisProperties redisProperties;
    private final RedisOperations<String, StreamMessage> redisOperations;
    private final Timer timer;
    private final BlockingQueue<Collection<TopicMessage>> topicMessagesQueue;

    RedisPublisher(
            RedisProperties redisProperties,
            RedisOperations<String, StreamMessage> redisOperations,
            MeterRegistry meterRegistry,
            ParserContext parserContext) {
        this.channelNames = Caffeine.newBuilder().maximumSize(1000L).build(this::getChannelName);
        this.parserContext = parserContext;
        this.redisOperations = redisOperations;
        this.redisProperties = redisProperties;
        this.timer = PUBLISH_TIMER.tag("type", "redis").register(meterRegistry);
        this.topicMessagesQueue = new ArrayBlockingQueue<>(redisProperties.getQueueCapacity());

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
    @SneakyThrows
    public void onEnd(RecordFile recordFile) {
        if (!redisProperties.isEnabled()) {
            return;
        }

        var topicMessages = parserContext.get(TopicMessage.class);

        if (!topicMessages.isEmpty() && !topicMessagesQueue.offer(topicMessages)) {
            log.warn("topicMessagesQueue is full, will block until space is available");
            topicMessagesQueue.put(topicMessages);
        }
    }

    private void publish(Collection<TopicMessage> messages) {
        try {
            Stopwatch stopwatch = Stopwatch.createStarted();
            timer.record(() -> redisOperations.executePipelined(callback(messages)));
            log.info("Finished notifying {} messages in {}", messages.size(), stopwatch);
        } catch (Exception e) {
            log.error("Unable to publish to redis", e);
        }
    }

    // Batch send using Redis pipelining
    private SessionCallback<Object> callback(Collection<TopicMessage> messages) {
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
