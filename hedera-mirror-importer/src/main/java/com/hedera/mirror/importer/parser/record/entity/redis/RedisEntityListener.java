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

import com.google.common.base.Stopwatch;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import javax.annotation.PostConstruct;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SynchronousSink;
import reactor.core.scheduler.Schedulers;

import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.domain.StreamMessage;
import com.hedera.mirror.importer.domain.TopicMessage;
import com.hedera.mirror.importer.exception.ImporterException;
import com.hedera.mirror.importer.parser.record.entity.BatchEntityListener;
import com.hedera.mirror.importer.parser.record.entity.ConditionOnEntityRecordParser;
import com.hedera.mirror.importer.parser.record.entity.EntityBatchCleanupEvent;
import com.hedera.mirror.importer.parser.record.entity.EntityBatchSaveEvent;

@ConditionOnEntityRecordParser
@Log4j2
@Named
@Order(3)
@RequiredArgsConstructor
public class RedisEntityListener implements BatchEntityListener {

    // hardcode it now to avoid spring validation performance penalty incurred on RedisProperties.isEnabled() if it were
    // added to RedisProperties with validation annotations
    public static final int TASK_QUEUE_SIZE = 8;

    private final MirrorProperties mirrorProperties;
    private final RedisProperties redisProperties;
    private final RedisOperations<String, StreamMessage> redisOperations;
    private final MeterRegistry meterRegistry;

    private long lastConsensusTimestamp;
    private Disposable subscription;
    private Timer timer;
    private List<TopicMessage> topicMessages;
    private BlockingQueue<List<TopicMessage>> topicMessagesQueue;
    private String topicPrefix;

    @PostConstruct
    void init() {
        lastConsensusTimestamp = 0;
        subscription = null;
        timer = Timer.builder("hedera.mirror.importer.publish.duration")
                .description("The amount of time it took to publish the domain entity")
                .tag("entity", TopicMessage.class.getSimpleName())
                .tag("type", "redis")
                .register(meterRegistry);
        topicMessages = new ArrayList<>();
        topicMessagesQueue = new ArrayBlockingQueue<>(TASK_QUEUE_SIZE);
        topicPrefix = "topic." + mirrorProperties.getShard() + "."; // Cache to avoid reflection penalty
    }

    @Override
    public boolean isEnabled() {
        return redisProperties.isEnabled();
    }

    @Override
    public void onTopicMessage(TopicMessage topicMessage) throws ImporterException {
        long consensusTimestamp = topicMessage.getConsensusTimestamp();
        if (consensusTimestamp <= lastConsensusTimestamp) {
            return;
        }

        lastConsensusTimestamp = consensusTimestamp;
        topicMessages.add(topicMessage);
    }

    @Override
    @EventListener
    public void onSave(EntityBatchSaveEvent event) throws InterruptedException {
        if (!isEnabled() || topicMessages.isEmpty()) {
            return;
        }

        // defer flux, subscription, and scheduler creation
        if (subscription == null) {
            // create a flux from the generator to pull then pump the topic messages list, immediately subscribe to the
            // flux to publish the topic messages list to redis service. With subscribeOn the new single thread
            // scheduler, the whole flux pipeline runs in a single thread.
            subscription = Flux.generate(this::generate)
                    .doOnSubscribe(s -> log.info("Starting redis publish flow"))
                    .doFinally(s -> log.info("Redis publish flow stopped with {} signal", s))
                    .doOnError(t -> log.error("Unexpected error during redis publish flow", t))
                    .subscribeOn(Schedulers.newSingle("redis-publish"))
                    .subscribe(this::publish);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> topicMessagesQueue.offer(new ArrayList<>())));
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

    private void generate(SynchronousSink<List<TopicMessage>> sink) {
        try {
            List<TopicMessage> messages = topicMessagesQueue.take();
            if (messages.isEmpty()) {
                sink.complete();
            } else {
                sink.next(messages);
            }
        } catch (Exception e) {
            log.error("Unable to retrieve topicMessages from the blocking queue", e);
            sink.error(new RuntimeException("Unexpected error when generating messages for the flow", e));
        }
    }

    private void publish(List<TopicMessage> messages) {
        try {
            Stopwatch stopwatch = Stopwatch.createStarted();
            timer.record(() -> redisOperations.executePipelined(callback(messages)));
            log.info("Finished notifying {} messages in {}", messages.size(), stopwatch);
            messages.clear();
        } catch (Exception e) {
            log.error("Unable to publish to redis", e);
        }
    }

    // Batch send using Redis pipelining
    private SessionCallback<Object> callback(List<TopicMessage> messages) {
        return new SessionCallback<>() {
            @Override
            public Object execute(RedisOperations operations) {
                for (TopicMessage topicMessage : messages) {
                    String channel = topicPrefix + topicMessage.getRealmNum() + "." + topicMessage.getTopicNum();
                    redisOperations.convertAndSend(channel, topicMessage);
                }
                return null;
            }
        };
    }
}
