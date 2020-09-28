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
import javax.annotation.PostConstruct;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;

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

    private final MirrorProperties mirrorProperties;
    private final RedisProperties redisProperties;
    private final RedisOperations<String, StreamMessage> redisOperations;
    private final List<TopicMessage> topicMessages = new ArrayList<>();
    private final MeterRegistry meterRegistry;

    private Timer timer;
    private String topicPrefix;

    @PostConstruct
    void init() {
        timer = Timer.builder("hedera.mirror.importer.publish.duration")
                .description("The amount of time it took to publish the domain entity")
                .tag("entity", TopicMessage.class.getSimpleName())
                .tag("type", "redis")
                .register(meterRegistry);
        topicPrefix = "topic." + mirrorProperties.getShard() + "."; // Cache to avoid reflection penalty
    }

    @Override
    public boolean isEnabled() {
        return redisProperties.isEnabled();
    }

    @Override
    public void onTopicMessage(TopicMessage topicMessage) throws ImporterException {
        topicMessages.add(topicMessage);
    }

    @Override
    @EventListener
    public void onSave(EntityBatchSaveEvent event) {
        try {
            if (isEnabled()) {
                Stopwatch stopwatch = Stopwatch.createStarted();
                timer.record(() -> redisOperations.executePipelined(callback()));
                log.info("Finished notifying {} messages in {}", topicMessages.size(), stopwatch);
            }
        } catch (Exception e) {
            log.error("Unable to publish to redis", e);
        }
    }

    @Override
    @EventListener
    public void onCleanup(EntityBatchCleanupEvent event) {
        log.debug("Finished clearing {} messages", topicMessages.size());
        topicMessages.clear();
    }

    // Batch send using Redis pipelining
    private <K, V> SessionCallback<Object> callback() {
        return new SessionCallback<>() {
            @Override
            public <K, V> Object execute(RedisOperations<K, V> operations) throws DataAccessException {
                for (TopicMessage topicMessage : topicMessages) {
                    String channel = topicPrefix + topicMessage.getRealmNum() + "." + topicMessage.getTopicNum();
                    redisOperations.convertAndSend(channel, topicMessage);
                }
                return null;
            }
        };
    }
}
