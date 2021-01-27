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

import com.google.common.base.Stopwatch;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import javax.inject.Named;
import lombok.Data;
import org.reactivestreams.Subscription;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.retry.Repeat;
import reactor.util.retry.Retry;

import com.hedera.mirror.grpc.converter.InstantToLongConverter;
import com.hedera.mirror.grpc.domain.TopicMessage;
import com.hedera.mirror.grpc.domain.TopicMessageFilter;
import com.hedera.mirror.grpc.repository.TopicMessageRepository;

@Named
public class SharedPollingTopicListener extends SharedTopicListener {

    private final TopicMessageRepository topicMessageRepository;
    private final InstantToLongConverter instantToLongConverter;
    private final Flux<TopicMessage> topicMessages;

    public SharedPollingTopicListener(ListenerProperties listenerProperties,
                                      TopicMessageRepository topicMessageRepository,
                                      InstantToLongConverter instantToLongConverter) {
        super(listenerProperties);
        this.topicMessageRepository = topicMessageRepository;
        this.instantToLongConverter = instantToLongConverter;

        Scheduler scheduler = Schedulers.newSingle("shared-poll", true);
        Duration frequency = listenerProperties.getFrequency();
        PollingContext context = new PollingContext();

        topicMessages = Flux.defer(() -> poll(context).subscribeOn(scheduler))
                .repeatWhen(Repeat.times(Long.MAX_VALUE)
                        .fixedBackoff(frequency)
                        .withBackoffScheduler(scheduler))
                .name("shared-poll")
                .metrics()
                .doOnCancel(() -> log.info("Cancelled polling"))
                .doOnError(t -> log.error("Error polling the database", t))
                .doOnSubscribe(context::onStart)
                .retryWhen(Retry.backoff(Long.MAX_VALUE, frequency).maxBackoff(frequency.multipliedBy(4L)))
                .share();
    }

    @Override
    protected Flux<TopicMessage> getSharedListener(TopicMessageFilter filter) {
        return topicMessages;
    }

    private Flux<TopicMessage> poll(PollingContext context) {
        if (!listenerProperties.isEnabled()) {
            return Flux.empty();
        }

        Pageable pageable = PageRequest.of(0, listenerProperties.getMaxPageSize());
        return Flux.fromIterable(topicMessageRepository.findLatest(context.getLastConsensusTimestamp(), pageable))
                .name("findLatest")
                .metrics()
                .doOnNext(context::onNext)
                .doOnCancel(context::onPollEnd)
                .doOnComplete(context::onPollEnd)
                .doOnSubscribe(context::onPollStart);
    }

    @Data
    private class PollingContext {

        private long count = 0;
        private final Stopwatch stopwatch = Stopwatch.createUnstarted();
        private volatile Long lastConsensusTimestamp;

        void onNext(TopicMessage topicMessage) {
            count++;
            lastConsensusTimestamp = topicMessage.getConsensusTimestamp();
            if (log.isTraceEnabled()) {
                log.trace("Next message: {}", topicMessage);
            }
        }

        void onPollEnd() {
            var elapsed = stopwatch.elapsed(TimeUnit.MILLISECONDS);
            var rate = elapsed > 0 ? (int) (1000.0 * count / elapsed) : 0;
            log.info("Finished querying with {} messages in {} ({}/s)", count, stopwatch, rate);
            count = 0;
        }

        void onPollStart(Subscription subscription) {
            count = 0;
            stopwatch.reset().start();
            log.debug("Querying for messages after timestamp {}", lastConsensusTimestamp);
        }

        void onStart(Subscription subscription) {
            lastConsensusTimestamp = instantToLongConverter.convert(Instant.now());
            log.info("Starting to poll every {}ms", listenerProperties.getFrequency().toMillis());
        }
    }
}
