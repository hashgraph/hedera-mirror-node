/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.grpc.listener;

import com.hedera.mirror.grpc.domain.TopicMessage;
import com.hedera.mirror.grpc.domain.TopicMessageFilter;
import com.hedera.mirror.grpc.repository.TopicMessageRepository;
import io.micrometer.observation.ObservationRegistry;
import jakarta.inject.Named;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import reactor.core.observability.micrometer.Micrometer;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.retry.Jitter;
import reactor.retry.Repeat;

@Named
@Log4j2
@RequiredArgsConstructor
public class PollingTopicListener implements TopicListener {

    private final ListenerProperties listenerProperties;
    private final ObservationRegistry observationRegistry;
    private final TopicMessageRepository topicMessageRepository;
    private final Scheduler scheduler =
            Schedulers.newParallel("poll", 4 * Runtime.getRuntime().availableProcessors(), true);

    @Override
    public Flux<TopicMessage> listen(TopicMessageFilter filter) {
        PollingContext context = new PollingContext(filter);
        Duration interval = listenerProperties.getInterval();

        return Flux.defer(() -> poll(context))
                .delaySubscription(interval, scheduler)
                .repeatWhen(Repeat.times(Long.MAX_VALUE)
                        .fixedBackoff(interval)
                        .jitter(Jitter.random(0.1))
                        .withBackoffScheduler(scheduler))
                .name(METRIC)
                .tag(METRIC_TAG, "poll")
                .tap(Micrometer.observation(observationRegistry))
                .doOnNext(context::onNext)
                .doOnSubscribe(s -> log.info("Starting to poll every {}ms: {}", interval.toMillis(), filter));
    }

    private Flux<TopicMessage> poll(PollingContext context) {
        TopicMessageFilter filter = context.getFilter();
        TopicMessage last = context.getLast();
        int limit = filter.hasLimit()
                ? (int) (filter.getLimit() - context.getCount().get())
                : Integer.MAX_VALUE;
        int pageSize = Math.min(limit, listenerProperties.getMaxPageSize());
        Instant startTime = last != null ? last.getConsensusTimestampInstant().plusNanos(1) : filter.getStartTime();

        TopicMessageFilter newFilter =
                filter.toBuilder().limit(pageSize).startTime(startTime).build();

        return Flux.fromStream(topicMessageRepository.findByFilter(newFilter));
    }

    @Data
    private class PollingContext {

        private final TopicMessageFilter filter;
        private final AtomicLong count = new AtomicLong(0L);
        private final AtomicReference<TopicMessage> last = new AtomicReference<>();

        void onNext(TopicMessage topicMessage) {
            last.set(topicMessage);
            count.incrementAndGet();
        }

        private TopicMessage getLast() {
            return last.get();
        }
    }
}
