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

import com.google.common.base.Stopwatch;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Named;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.reactivestreams.Subscription;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.retry.Repeat;

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
    private final Scheduler scheduler;

    private Flux<TopicMessage> poller = Flux.empty();
    private Disposable pollerDisposable;

    public SharedPollingTopicListener(ListenerProperties listenerProperties,
                                      TopicMessageRepository topicMessageRepository,
                                      InstantToLongConverter instantToLongConverter) {
        this.listenerProperties = listenerProperties;
        this.topicMessageRepository = topicMessageRepository;
        this.instantToLongConverter = instantToLongConverter;
        scheduler = Schedulers.newSingle("shared-poll", true);
    }

    /*
     * Subscribe on startup and ignore messages to start backfilling the buffer. This method is also used by tests to
     * reset the buffer between runs. Outside these two use cases this method should not be used.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        if (pollerDisposable != null) {
            pollerDisposable.dispose();
        }

        if (!listenerProperties.isEnabled()) {
            return;
        }

        PollingContext context = new PollingContext();
        poller = Flux.defer(() -> poll(context))
                .repeatWhen(Repeat.times(Long.MAX_VALUE)
                        .fixedBackoff(listenerProperties.getPollingFrequency())
                        .withBackoffScheduler(scheduler))
                .name("shared-poll")
                .metrics()
                .doOnCancel(() -> log.info("Cancelled polling"))
                .doOnComplete(() -> log.info("Completed polling"))
                .doOnNext(context::onNext)
                .doOnSubscribe(context::onStart)
                .replay(listenerProperties.getBufferSize())
                .autoConnect(1, disposable -> pollerDisposable = disposable);

        poller.subscribe(t -> {}, e -> {});
    }

    @Override
    public Flux<TopicMessage> listen(TopicMessageFilter filter) {
        return poller.filter(t -> filterMessage(t, filter))
                .doOnSubscribe(s -> log.info("Subscribing: {}", filter));
    }

    private Flux<TopicMessage> poll(PollingContext context) {
        Instant instant = context.getLastConsensusTimestamp();
        Long consensusTimestamp = instantToLongConverter.convert(instant);
        Pageable pageable = PageRequest.of(0, listenerProperties.getMaxPageSize());

        return Flux.defer(() -> Flux.fromIterable(topicMessageRepository.findLatest(consensusTimestamp, pageable)))
                .name("findLatest")
                .metrics()
                .doOnCancel(context::onPollEnd)
                .doOnComplete(context::onPollEnd)
                .doOnSubscribe(context::onPollStart);
    }

    private boolean filterMessage(TopicMessage message, TopicMessageFilter filter) {
        return filter.getRealmNum() == message.getRealmNum() &&
                filter.getTopicNum() == message.getTopicNum() &&
                !filter.getStartTime().isAfter(message.getConsensusTimestampInstant());
    }

    @Data
    private class PollingContext {

        private final AtomicLong count = new AtomicLong(0L);
        private final Stopwatch stopwatch = Stopwatch.createUnstarted();
        private volatile Instant lastConsensusTimestamp = Instant.now().minus(listenerProperties.getBufferInitial());

        void onNext(TopicMessage topicMessage) {
            count.incrementAndGet();
            lastConsensusTimestamp = topicMessage.getConsensusTimestampInstant();
            log.trace("Next message: {}", topicMessage);
        }

        void onPollEnd() {
            var elapsed = stopwatch.elapsed(TimeUnit.MILLISECONDS);
            var rate = elapsed > 0 ? (int) (1000.0 * count.get() / elapsed) : 0;
            log.debug("Finished querying with {} messages in {} ({}/s)", count, stopwatch, rate);
        }

        void onPollStart(Subscription subscription) {
            count.set(0L);
            stopwatch.reset().start();
            log.debug("Querying for messages after timestamp {}", lastConsensusTimestamp);
        }

        // Backfill the buffer on startup
        void onStart(Subscription subscription) {
            lastConsensusTimestamp = Instant.now().minus(listenerProperties.getBufferInitial());
            log.info("Starting to poll every {}ms", listenerProperties.getPollingFrequency().toMillis());
        }
    }
}
