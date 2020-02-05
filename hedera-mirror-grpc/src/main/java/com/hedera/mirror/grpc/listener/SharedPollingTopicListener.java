package com.hedera.mirror.grpc.listener;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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

import java.time.Duration;
import java.time.Instant;
import javax.inject.Named;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import com.hedera.mirror.grpc.converter.InstantToLongConverter;
import com.hedera.mirror.grpc.domain.TopicMessage;
import com.hedera.mirror.grpc.domain.TopicMessageFilter;
import com.hedera.mirror.grpc.repository.TopicMessageRepository;

@Named
@Log4j2
public class SharedPollingTopicListener implements TopicListener {

    private final ListenerProperties listenerProperties;
    private final TopicMessageRepository topicMessageRepository;
    private final InstantToLongConverter instantToLongConverter;
    private final Flux<TopicMessage> poller;

    public SharedPollingTopicListener(ListenerProperties listenerProperties,
                                      TopicMessageRepository topicMessageRepository,
                                      InstantToLongConverter instantToLongConverter) {
        this.listenerProperties = listenerProperties;
        this.topicMessageRepository = topicMessageRepository;
        this.instantToLongConverter = instantToLongConverter;

        Duration frequency = listenerProperties.getPollingFrequency();
        PollingContext context = new PollingContext();
        Scheduler scheduler = Schedulers.newBoundedElastic(listenerProperties
                .getPoolSize(), Schedulers.DEFAULT_BOUNDED_ELASTIC_QUEUESIZE, "shared-poll");

        poller = Flux.interval(frequency, scheduler)
                .filter(i -> !context.isRunning()) // Discard polling requests while querying
                .concatMap(i -> poll(context))
                .name("shared-poll")
                .metrics()
                .doOnNext(context::onNext)
                .doOnSubscribe(s -> log.info("Starting to poll every {}ms", frequency.toMillis()))
                .doOnComplete(() -> log.info("Completed polling"))
                .doOnCancel(() -> log.info("Cancelled polling"))
                .share();
    }

    @Override
    public Flux<TopicMessage> listen(TopicMessageFilter filter) {
        return poller.filter(t -> filterMessage(t, filter))
                .doOnSubscribe(s -> log.info("Listening for messages: {}", filter));
    }

    private Flux<TopicMessage> poll(PollingContext context) {
        Instant instant = context.getLastConsensusTimestamp();
        Long consensusTimestamp = instantToLongConverter.convert(instant);
        log.debug("Querying for messages after: {}", instant);

        return topicMessageRepository.findByConsensusTimestampGreaterThan(consensusTimestamp)
                .doOnSubscribe(s -> context.setRunning(true))
                .doOnCancel(() -> context.setRunning(false))
                .doOnComplete(() -> context.setRunning(false));
    }

    private boolean filterMessage(TopicMessage message, TopicMessageFilter filter) {
        return filter.getRealmNum() == message.getRealmNum() &&
                filter.getTopicNum() == message.getTopicNum() &&
                !filter.getStartTime().isAfter(message.getConsensusTimestamp());
    }

    @Data
    private class PollingContext {

        private volatile Instant lastConsensusTimestamp = Instant.now();
        private volatile boolean running = false;

        void onNext(TopicMessage topicMessage) {
            lastConsensusTimestamp = topicMessage.getConsensusTimestamp();
            log.trace("Next message: {}", topicMessage);
        }
    }
}
