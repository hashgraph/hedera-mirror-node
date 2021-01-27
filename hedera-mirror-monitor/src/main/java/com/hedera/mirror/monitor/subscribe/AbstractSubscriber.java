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
import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.Multiset;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.Timer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.commons.math3.util.Precision;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.hedera.datagenerator.sdk.supplier.TransactionType;

public abstract class AbstractSubscriber<P extends AbstractSubscriberProperties> implements Subscriber {

    static final String METRIC_DURATION = "hedera.mirror.monitor.subscribe.duration";
    static final String METRIC_E2E = "hedera.mirror.monitor.subscribe.e2e";

    protected final Logger log = LogManager.getLogger(getClass());

    protected final AtomicLong counter;
    protected final Multiset<String> errors;
    protected final Stopwatch stopwatch;
    protected final P subscriberProperties;

    private final TimeGauge durationMetric;
    private final Map<TransactionType, Timer> latencyMetrics;
    private final MeterRegistry meterRegistry;
    private final ScheduledFuture<?> statusThread;

    protected AbstractSubscriber(MeterRegistry meterRegistry, P subscriberProperties) {
        this.counter = new AtomicLong(0L);
        this.errors = ConcurrentHashMultiset.create();
        this.stopwatch = Stopwatch.createStarted();
        this.subscriberProperties = subscriberProperties;
        this.durationMetric = TimeGauge
                .builder(METRIC_DURATION, stopwatch, TimeUnit.NANOSECONDS, s -> s.elapsed(TimeUnit.NANOSECONDS))
                .description("How long the subscriber has been running")
                .tag("scenario", subscriberProperties.getName())
                .tag("subscriber", getClass().getSimpleName())
                .register(meterRegistry);
        this.latencyMetrics = new ConcurrentHashMap<>();
        this.meterRegistry = meterRegistry;
        long frequency = subscriberProperties.getStatusFrequency().toMillis();
        this.statusThread = Executors.newSingleThreadScheduledExecutor()
                .scheduleWithFixedDelay(this::status, frequency, frequency, TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        meterRegistry.remove(durationMetric);
        statusThread.cancel(true);
    }

    protected final Timer getLatencyTimer(TransactionType type) {
        return latencyMetrics.computeIfAbsent(type, t -> Timer.builder(METRIC_E2E)
                .description("The end to end transaction latency starting from publish and ending at receive")
                .tag("scenario", subscriberProperties.getName())
                .tag("subscriber", getClass().getSimpleName())
                .tag("type", t.name())
                .register(meterRegistry)
        );
    }

    protected abstract void onError(Throwable t);

    protected abstract boolean shouldRetry(Throwable t);

    private void status() {
        long count = counter.get();
        long elapsed = stopwatch.elapsed(TimeUnit.MICROSECONDS);
        double rate = Precision.round(elapsed > 0 ? (1_000_000.0 * count) / elapsed : 0.0, 1);
        Map<String, Integer> errorCounts = new HashMap<>();
        errors.forEachEntry(errorCounts::put);
        log.info("{}: {} transactions in {} at {}/s. Errors: {}", subscriberProperties
                .getName(), count, stopwatch, rate, errorCounts);
    }
}
