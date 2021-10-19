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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Flux;

import com.hedera.mirror.grpc.domain.TopicMessage;
import com.hedera.mirror.grpc.domain.TopicMessageFilter;
import com.hedera.mirror.grpc.listener.ListenerProperties.ListenerType;

@Named
@Log4j2
@Primary
@RequiredArgsConstructor
public class CompositeTopicListener implements TopicListener {

    private final ListenerProperties listenerProperties;
    private final NotifyingTopicListener notifyingTopicListener;
    private final PollingTopicListener pollingTopicListener;
    private final RedisTopicListener redisTopicListener;
    private final SharedPollingTopicListener sharedPollingTopicListener;
    private final MeterRegistry meterRegistry;
    private Timer consensusToPublishTimer;

    @PostConstruct
    public void registerMetrics() {
        consensusToPublishTimer = Timer.builder("hedera.mirror.publish.latency")
                .description("The difference in ms between the time consensus was achieved and the mirror node " +
                        "published the entity")
                .tag("type", TopicMessage.class.getSimpleName())
                .register(meterRegistry);
    }

    @Override
    public Flux<TopicMessage> listen(TopicMessageFilter filter) {
        if (!listenerProperties.isEnabled()) {
            return Flux.empty();
        }

        return getTopicListener().listen(filter)
                .filter(t -> filterMessage(t, filter))
                .doOnNext(this::recordMetric);
    }

    private TopicListener getTopicListener() {
        ListenerType type = listenerProperties.getType();

        switch (type) {
            case NOTIFY:
                return notifyingTopicListener;
            case POLL:
                return pollingTopicListener;
            case REDIS:
                return redisTopicListener;
            case SHARED_POLL:
                return sharedPollingTopicListener;
            default:
                throw new UnsupportedOperationException("Unknown listener type: " + type);
        }
    }

    private boolean filterMessage(TopicMessage message, TopicMessageFilter filter) {
        return message.getTopicId() == filter.getTopicId() &&
                message.getConsensusTimestamp() >= filter.getStartTimeLong();
    }

    private void recordMetric(TopicMessage topicMessage) {
        long latency = System.currentTimeMillis() - topicMessage.getConsensusTimestampInstant().toEpochMilli();
        consensusToPublishTimer.record(latency, TimeUnit.MILLISECONDS);
    }
}
