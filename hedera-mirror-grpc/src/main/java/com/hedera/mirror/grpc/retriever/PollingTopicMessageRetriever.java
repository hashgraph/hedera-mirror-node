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

package com.hedera.mirror.grpc.retriever;

import com.google.common.base.Stopwatch;
import com.hedera.mirror.grpc.domain.TopicMessage;
import com.hedera.mirror.grpc.domain.TopicMessageFilter;
import com.hedera.mirror.grpc.repository.TopicMessageRepository;
import io.micrometer.observation.ObservationRegistry;
import jakarta.inject.Named;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import reactor.core.observability.micrometer.Micrometer;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.retry.Jitter;
import reactor.retry.Repeat;
import reactor.util.retry.Retry;

@Named
@Log4j2
public class PollingTopicMessageRetriever implements TopicMessageRetriever {

    private final ObservationRegistry observationRegistry;
    private final RetrieverProperties retrieverProperties;
    private final TopicMessageRepository topicMessageRepository;
    private final Scheduler scheduler;

    public PollingTopicMessageRetriever(
            ObservationRegistry observationRegistry,
            RetrieverProperties retrieverProperties,
            TopicMessageRepository topicMessageRepository) {
        this.observationRegistry = observationRegistry;
        this.retrieverProperties = retrieverProperties;
        this.topicMessageRepository = topicMessageRepository;
        int threadCount =
                retrieverProperties.getThreadMultiplier() * Runtime.getRuntime().availableProcessors();
        scheduler = Schedulers.newParallel("retriever", threadCount, true);
    }

    @Override
    public Flux<TopicMessage> retrieve(TopicMessageFilter filter, boolean throttled) {
        if (!retrieverProperties.isEnabled()) {
            return Flux.empty();
        }

        PollingContext context = new PollingContext(filter, throttled);
        return Flux.defer(() -> poll(context))
                .repeatWhen(Repeat.create(r -> !context.isComplete(), context.getNumRepeats())
                        .fixedBackoff(context.getFrequency())
                        .jitter(Jitter.random(0.1))
                        .withBackoffScheduler(scheduler))
                .name(METRIC)
                .tap(Micrometer.observation(observationRegistry))
                .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1)))
                .timeout(retrieverProperties.getTimeout(), scheduler)
                .doOnCancel(context::onComplete)
                .doOnComplete(context::onComplete)
                .doOnNext(context::onNext);
    }

    private Flux<TopicMessage> poll(PollingContext context) {
        TopicMessageFilter filter = context.getFilter();
        TopicMessage last = context.getLast();
        int limit = filter.hasLimit()
                ? (int) (filter.getLimit() - context.getTotal().get())
                : Integer.MAX_VALUE;
        int pageSize = Math.min(limit, context.getMaxPageSize());
        Instant startTime = last != null ? last.getConsensusTimestampInstant().plusNanos(1) : filter.getStartTime();
        context.getPageSize().set(0L);

        TopicMessageFilter newFilter =
                filter.toBuilder().limit(pageSize).startTime(startTime).build();

        log.debug("Executing query: {}", newFilter);
        return Flux.fromStream(topicMessageRepository.findByFilter(newFilter));
    }

    @Data
    private class PollingContext {

        private final TopicMessageFilter filter;
        private final boolean throttled;
        private final Duration frequency;
        private final AtomicReference<TopicMessage> last = new AtomicReference<>();
        private final int maxPageSize;
        private final long numRepeats;
        private final AtomicLong pageSize = new AtomicLong(0L);
        private final Stopwatch stopwatch = Stopwatch.createStarted();
        private final AtomicLong total = new AtomicLong(0L);

        private PollingContext(TopicMessageFilter filter, boolean throttled) {
            this.filter = filter;
            this.throttled = throttled;

            if (throttled) {
                numRepeats = Long.MAX_VALUE;
                frequency = retrieverProperties.getPollingFrequency();
                maxPageSize = retrieverProperties.getMaxPageSize();
            } else {
                RetrieverProperties.UnthrottledProperties unthrottled = retrieverProperties.getUnthrottled();
                numRepeats = unthrottled.getMaxPolls();
                frequency = unthrottled.getPollingFrequency();
                maxPageSize = unthrottled.getMaxPageSize();
            }
        }

        private TopicMessage getLast() {
            return last.get();
        }

        /**
         * Checks if this publisher is complete by comparing if the number of results in the last page was less than the
         * page size or if the limit has reached if it's set. This avoids the extra query if we were to just check if
         * last page was empty.
         *
         * @return whether all historic messages have been returned
         */
        boolean isComplete() {
            boolean limitHit = filter.hasLimit() && filter.getLimit() == total.get();

            if (throttled) {
                return pageSize.get() < retrieverProperties.getMaxPageSize() || limitHit;
            }

            return limitHit;
        }

        void onNext(TopicMessage topicMessage) {
            last.set(topicMessage);
            total.incrementAndGet();
            pageSize.incrementAndGet();
        }

        void onComplete() {
            var elapsed = stopwatch.elapsed(TimeUnit.MILLISECONDS);
            var rate = elapsed > 0 ? (int) (1000.0 * total.get() / elapsed) : 0;
            log.info(
                    "[{}] Finished retrieving {} messages in {} ({}/s)",
                    filter.getSubscriberId(),
                    total,
                    stopwatch,
                    rate);
        }
    }
}
