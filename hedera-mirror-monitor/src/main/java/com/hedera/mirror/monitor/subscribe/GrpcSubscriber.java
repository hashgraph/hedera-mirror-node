package com.hedera.mirror.monitor.subscribe;

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

import static io.grpc.Status.Code.INVALID_ARGUMENT;
import static io.grpc.Status.Code.NOT_FOUND;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.util.concurrent.Uninterruptibles;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.PreDestroy;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.math3.util.Precision;

import com.hedera.datagenerator.common.Utility;
import com.hedera.datagenerator.sdk.supplier.TransactionType;
import com.hedera.hashgraph.sdk.consensus.ConsensusTopicId;
import com.hedera.hashgraph.sdk.mirror.MirrorClient;
import com.hedera.hashgraph.sdk.mirror.MirrorConsensusTopicQuery;
import com.hedera.hashgraph.sdk.mirror.MirrorConsensusTopicResponse;
import com.hedera.hashgraph.sdk.mirror.MirrorSubscriptionHandle;
import com.hedera.mirror.monitor.MonitorProperties;
import com.hedera.mirror.monitor.publish.PublishResponse;

@Log4j2
public class GrpcSubscriber implements Subscriber {

    private final MonitorProperties monitorProperties;
    private final GrpcSubscriberProperties subscriberProperties;
    private final Timer timer;

    private final MirrorClient mirrorClient;
    private final AtomicLong counter;
    private final AtomicLong retries;
    private final Stopwatch stopwatch;
    private final Multiset<String> errors;
    private final ScheduledFuture<?> statusThread;

    private MirrorSubscriptionHandle subscription;
    private volatile MirrorConsensusTopicResponse lastReceived;
    private Instant endTime;

    GrpcSubscriber(MeterRegistry meterRegistry, MonitorProperties monitorProperties,
                   GrpcSubscriberProperties subscriberProperties) {
        this.monitorProperties = monitorProperties;
        this.subscriberProperties = subscriberProperties;
        this.counter = new AtomicLong(0L);
        this.retries = new AtomicLong(0L);
        stopwatch = Stopwatch.createStarted();
        errors = ConcurrentHashMultiset.create();
        this.timer = Timer.builder("hedera.mirror.monitor.subscribe")
                .tag("api", "grpc")
                .tag("type", TransactionType.CONSENSUS_SUBMIT_MESSAGE.toString())
                .register(meterRegistry);

        statusThread = Executors.newSingleThreadScheduledExecutor()
                .scheduleWithFixedDelay(this::status, 5, 5, TimeUnit.SECONDS);

        log.info("Connecting to mirror node {}", monitorProperties.getMirrorNode().getGrpc().getEndpoint());
        this.mirrorClient = new MirrorClient(monitorProperties.getMirrorNode().getGrpc().getEndpoint());
        resubscribe();
    }

    private void onNext(MirrorConsensusTopicResponse topicResponse) {
        counter.incrementAndGet();
        log.trace("Received message #{} with timestamp {}", topicResponse.sequenceNumber,
                topicResponse.consensusTimestamp);

        if (lastReceived != null) {
            long expected = lastReceived.sequenceNumber + 1;
            if (topicResponse.sequenceNumber != expected) {
                log.warn("Expected sequence number {} but received {}", expected, topicResponse.sequenceNumber);
            }
        }

        this.lastReceived = topicResponse;
        Long timestamp = Utility.getTimestamp(topicResponse.message);

        if (timestamp == null || timestamp <= 0 || timestamp >= System.currentTimeMillis()) {
            log.warn("Invalid timestamp in message: {}", timestamp);
            return;
        }

        long latency = System.currentTimeMillis() - timestamp;
        timer.record(latency, TimeUnit.MILLISECONDS);
    }

    private void onError(Throwable t) {
        log.error("Error subscribing: ", t);
        errors.add(getStatusCode(t).name());

        if (shouldRetry(t)) {
            AbstractSubscriberProperties.RetryProperties retry = subscriberProperties.getRetry();
            long delayMillis = retries.get() * retry.getMinBackoff().toMillis();
            Duration retryDuration = Duration.ofMillis(Math.min(delayMillis, retry.getMaxBackoff().toMillis()));
            log.info("Retrying in {}s", retryDuration.toSeconds());
            Uninterruptibles.sleepUninterruptibly(retryDuration);
            resubscribe();
        } else {
            close();
        }
    }

    private boolean shouldRetry(Throwable t) {
        Status.Code code = getStatusCode(t);

        // Don't retry client errors
        if (code == INVALID_ARGUMENT || code == NOT_FOUND) {
            return false;
        }

        return retries.incrementAndGet() < subscriberProperties.getRetry().getMaxAttempts();
    }

    private Status.Code getStatusCode(Throwable t) {
        if (t instanceof StatusRuntimeException) {
            return ((StatusRuntimeException) t).getStatus().getCode();
        }
        return Status.Code.UNKNOWN;
    }

    @Override
    public void onPublish(PublishResponse response) {
        // Ignore for now
    }

    @PreDestroy
    public void close() {
        try {
            log.info("Closing mirror node connection to {}", monitorProperties.getMirrorNode().getGrpc().getEndpoint());
            subscription.unsubscribe();
            mirrorClient.close(1, TimeUnit.SECONDS);
            statusThread.cancel(true);
        } catch (Exception e) {
            // Ignore
        }
    }

    private synchronized void resubscribe() {
        long limit = subscriberProperties.getLimit();
        MirrorConsensusTopicQuery mirrorConsensusTopicQuery = new MirrorConsensusTopicQuery();
        mirrorConsensusTopicQuery.setTopicId(ConsensusTopicId.fromString(subscriberProperties.getTopicId()));
        mirrorConsensusTopicQuery.setLimit(limit > 0 ? limit - counter.get() : 0);

        Instant startTime = lastReceived != null ? lastReceived.consensusTimestamp.plusNanos(1) : subscriberProperties
                .getStartTime();
        startTime = Objects.requireNonNullElseGet(startTime, Instant::now);
        mirrorConsensusTopicQuery.setStartTime(startTime);

        if (endTime != null) {
            mirrorConsensusTopicQuery.setEndTime(endTime);
        } else {
            Duration duration = subscriberProperties.getDuration();
            if (duration != null) {
                endTime = startTime.plus(duration);
                mirrorConsensusTopicQuery.setEndTime(endTime);
            }
        }

        log.info("Starting subscriber: {}", subscriberProperties);
        subscription = mirrorConsensusTopicQuery.subscribe(mirrorClient, this::onNext, this::onError);
        retries.set(0L);
    }

    private void status() {
        long count = counter.get();
        long elapsed = stopwatch.elapsed(TimeUnit.MICROSECONDS);
        double rate = Precision.round(elapsed > 0 ? (1_000_000.0 * count) / elapsed : 0.0, 1);
        Map<String, Integer> errorCounts = new HashMap<>();
        errors.forEachEntry((k, v) -> errorCounts.put(k, v));
        log.info("Received {} transactions in {} at {}/s. Errors: {}", count, stopwatch, rate, errorCounts);
    }
}
