package com.hedera.mirror.grpc.service;

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
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.PostConstruct;
import javax.inject.Named;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.retry.Repeat;

import com.hedera.mirror.grpc.GrpcProperties;
import com.hedera.mirror.grpc.domain.Entity;
import com.hedera.mirror.grpc.domain.EntityType;
import com.hedera.mirror.grpc.domain.TopicMessage;
import com.hedera.mirror.grpc.domain.TopicMessageFilter;
import com.hedera.mirror.grpc.exception.TopicNotFoundException;
import com.hedera.mirror.grpc.listener.TopicListener;
import com.hedera.mirror.grpc.repository.EntityRepository;
import com.hedera.mirror.grpc.retriever.TopicMessageRetriever;

@Named
@Log4j2
@RequiredArgsConstructor
@Validated
public class TopicMessageServiceImpl implements TopicMessageService {

    private final GrpcProperties grpcProperties;
    private final TopicListener topicListener;
    private final EntityRepository entityRepository;
    private final TopicMessageRetriever topicMessageRetriever;
    private final MeterRegistry meterRegistry;
    private final AtomicLong subscriberCount = new AtomicLong(0L);

    @PostConstruct
    void init() {
        Gauge.builder("hedera.mirror.subscribers", () -> subscriberCount)
                .description("The number of active subscribers")
                .tag("type", TopicMessage.class.getSimpleName())
                .register(meterRegistry);
    }

    @Override
    public Flux<TopicMessage> subscribeTopic(TopicMessageFilter filter) {
        log.info("Subscribing to topic: {}", filter);
        TopicContext topicContext = new TopicContext(filter);

        return topicExists(filter).thenMany(topicMessageRetriever.retrieve(filter)
                .concatWith(Flux.defer(() -> incomingMessages(topicContext))) // Defer creation until query complete
                .filter(t -> t.compareTo(topicContext.getLastTopicMessage()) > 0) // Ignore duplicates
                .takeWhile(t -> filter.getEndTime() == null || t.getConsensusTimestampInstant()
                        .isBefore(filter.getEndTime()))
                .as(t -> filter.hasLimit() ? t.limitRequest(filter.getLimit()) : t)
                .doOnNext(topicContext::onNext)
                .doOnSubscribe(s -> subscriberCount.incrementAndGet())
                .doFinally(s -> subscriberCount.decrementAndGet())
                .doFinally(topicContext::finished));
    }

    private Mono<?> topicExists(TopicMessageFilter filter) {
        return Mono.justOrEmpty(entityRepository
                .findByCompositeKey(grpcProperties.getShard(), filter.getRealmNum(), filter.getTopicNum()))
                .switchIfEmpty(grpcProperties.isCheckTopicExists() ? Mono.error(new TopicNotFoundException()) :
                        Mono.just(Entity.builder().entityTypeId(EntityType.TOPIC).build()))
                .filter(e -> e.getEntityTypeId() == EntityType.TOPIC)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Not a valid topic")));
    }

    private Flux<TopicMessage> incomingMessages(TopicContext topicContext) {
        if (topicContext.isComplete()) {
            return Flux.empty();
        }

        TopicMessageFilter filter = topicContext.getFilter();
        TopicMessage last = topicContext.getLastTopicMessage();
        long limit = filter.hasLimit() ? filter.getLimit() - topicContext.getCount().get() : 0;
        Instant startTime = last != null ? last.getConsensusTimestampInstant().plusNanos(1) : filter.getStartTime();

        TopicMessageFilter newFilter = filter.toBuilder()
                .limit(limit)
                .startTime(startTime)
                .build();

        return topicListener.listen(newFilter)
                .takeUntilOther(pastEndTime(topicContext))
                .concatMap(t -> missingMessages(topicContext, t));
    }

    private Flux<Object> pastEndTime(TopicContext topicContext) {
        if (topicContext.getFilter().getEndTime() == null) {
            return Flux.never();
        }

        return Flux.empty().repeatWhen(Repeat.create(r -> !topicContext.isComplete(), Long.MAX_VALUE)
                .fixedBackoff(grpcProperties.getEndTimeInterval()));
    }

    /**
     * A flow can have missing messages if the importer is down for a long time when the client subscribes. When the
     * incoming flow catches up and receives the next message for the topic, it will fill in any missing messages from
     * when it was down.
     */
    private Flux<TopicMessage> missingMessages(TopicContext topicContext, TopicMessage current) {
        if (topicContext.isNext(current)) {
            return Flux.just(current);
        }

        TopicMessage last = topicContext.getLastTopicMessage();
        long numMissingMessages = current.getSequenceNumber() - last.getSequenceNumber() - 1;

        // fail fast on out of order messages
        if (numMissingMessages < -1) {
            throw new IllegalStateException(String
                    .format("Encountered out of order missing messages, last: %s, current: %s", last, current));
        }

        // ignore duplicate message already processed by larger subscribe context
        if (numMissingMessages == -1) {
            log.debug("Encountered duplicate missing message to be ignored, last: {}, current: {}", last, current);
            return Flux.empty();
        }

        TopicMessageFilter newFilter = topicContext.getFilter().toBuilder()
                .endTime(current.getConsensusTimestampInstant())
                .limit(numMissingMessages)
                .startTime(last.getConsensusTimestampInstant().plusNanos(1))
                .build();

        log.info("[{}] Querying topic {} for missing messages between sequence {} and {}",
                newFilter.getSubscriberId(), topicContext.getTopicId(), last.getSequenceNumber(),
                current.getSequenceNumber());

        return topicMessageRetriever.retrieve(newFilter)
                .concatWithValues(current)
                .name("findMissing")
                .metrics();
    }

    @Data
    private class TopicContext {

        private final TopicMessageFilter filter;
        private final String topicId;
        private final Stopwatch stopwatch;
        private final AtomicLong count;
        private final Instant startTime;
        private volatile TopicMessage lastTopicMessage;

        public TopicContext(TopicMessageFilter filter) {
            this.filter = filter;
            topicId = grpcProperties.getShard() + "." + filter.getRealmNum() + "." + filter.getTopicNum();
            stopwatch = Stopwatch.createStarted();
            count = new AtomicLong(0L);
            startTime = Instant.now();
        }

        boolean isComplete() {
            if (filter.getEndTime() == null) {
                return false;
            }

            if (filter.getEndTime().isBefore(startTime)) {
                return true;
            }

            return filter.getEndTime().plus(grpcProperties.getEndTimeInterval()).isBefore(Instant.now());
        }

        boolean isNext(TopicMessage topicMessage) {
            return lastTopicMessage == null || topicMessage.getSequenceNumber() == lastTopicMessage
                    .getSequenceNumber() + 1;
        }

        private int rate() {
            var elapsed = stopwatch.elapsed(TimeUnit.MILLISECONDS);
            return elapsed > 0 ? (int) (1000.0 * count.get() / elapsed) : 0;
        }

        void finished(SignalType signalType) {
            log.info("[{}] Topic {} {} with {} messages in {} ({}/s)",
                    filter.getSubscriberId(), signalType, topicId, count, stopwatch, rate());
        }

        void onNext(TopicMessage topicMessage) {
            if (!isNext(topicMessage)) {
                throw new IllegalStateException(String
                        .format("Encountered out of order messages, last: %s, current: %s", lastTopicMessage,
                                topicMessage));
            }

            lastTopicMessage = topicMessage;
            count.incrementAndGet();
            log.trace("[{}] Topic {} received message #{}: {}", filter.getSubscriberId(), topicId, count, topicMessage);
        }
    }
}
