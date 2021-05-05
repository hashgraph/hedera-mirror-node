package com.hedera.mirror.monitor.subscribe;

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
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.annotation.PreDestroy;
import javax.inject.Named;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.math3.util.Precision;

@Log4j2
@Named
public class SubscribeMetrics {

    static final String METRIC_DURATION = "hedera.mirror.monitor.subscribe.duration";
    static final String METRIC_E2E = "hedera.mirror.monitor.subscribe.e2e";
    static final String TAG_SCENARIO = "scenario";
    static final String TAG_SUBSCRIBER = "subscriber";
    static final String TAG_TYPE = "type";

    private final Map<Subscription, TimeGauge> durationMetrics;
    private final Map<Subscription, Timer> latencyMetrics;
    private final MeterRegistry meterRegistry;
    private final ScheduledFuture<?> statusThread;

    SubscribeMetrics(MeterRegistry meterRegistry, SubscribeProperties subscribeProperties) {
        this.durationMetrics = new ConcurrentHashMap<>();
        this.latencyMetrics = new ConcurrentHashMap<>();
        this.meterRegistry = meterRegistry;
        long frequency = subscribeProperties.getStatusFrequency().toMillis();
        this.statusThread = Executors.newSingleThreadScheduledExecutor()
                .scheduleWithFixedDelay(this::status, frequency, frequency, TimeUnit.MILLISECONDS);
    }

    public void record(SubscribeResponse response) {
        log.trace("Got response: {}", response);
        Subscription subscription = response.getSubscription();
        Instant publishedTimestamp = response.getPublishedTimestamp();
        durationMetrics.computeIfAbsent(subscription, this::newDurationGauge);

        if (publishedTimestamp != null) {
            Duration latency = Duration.between(publishedTimestamp, response.getReceivedTimestamp());
            latencyMetrics.computeIfAbsent(subscription, this::newLatencyTimer).record(latency);
        }
    }

    @PreDestroy
    void close() {
        statusThread.cancel(true);
    }

    private TimeGauge newDurationGauge(Subscription subscription) {
        return TimeGauge.builder(METRIC_DURATION, subscription, TimeUnit.NANOSECONDS, s -> s.getStopwatch()
                .elapsed(TimeUnit.NANOSECONDS))
                .description("How long the subscriber has been running")
                .tag(TAG_SCENARIO, subscription.getProperties().getName())
                .tag(TAG_SUBSCRIBER, String.valueOf(subscription.getId()))
                .tag(TAG_TYPE, subscription.getType().name())
                .register(meterRegistry);
    }

    private final Timer newLatencyTimer(Subscription subscription) {
        return Timer.builder(METRIC_E2E)
                .description("The end to end transaction latency starting from publish and ending at receive")
                .tag(TAG_SCENARIO, subscription.getProperties().getName())
                .tag(TAG_SUBSCRIBER, String.valueOf(subscription.getId()))
                .tag(TAG_TYPE, subscription.getType().name())
                .register(meterRegistry);
    }

    private void status() {
        durationMetrics.keySet().forEach(this::status);
    }

    private void status(Subscription subscription) {
        long count = subscription.getCount();
        Stopwatch stopwatch = subscription.getStopwatch();
        long elapsed = stopwatch.elapsed(TimeUnit.MICROSECONDS);
        double rate = Precision.round(elapsed > 0 ? (1_000_000.0 * count) / elapsed : 0.0, 1);
        Map<String, Integer> errorCounts = new TreeMap<>();
        subscription.getErrors().forEachEntry(errorCounts::put);
        log.info("{}: {} transactions in {} at {}/s. Errors: {}", subscription, count, stopwatch, rate, errorCounts);
    }
}
