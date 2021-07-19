package com.hedera.mirror.monitor;

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
import com.google.common.base.Throwables;
import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.Multiset;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.step.StepLong;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.math3.util.Precision;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.hedera.mirror.monitor.subscribe.Scenario;

@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public abstract class AbstractScenario<P extends ScenarioProperties, T> implements Scenario<P, T> {

    private static final long UPDATE_INTERVAL = 20_000L; // 20s measured in milliseconds

    @EqualsAndHashCode.Include
    protected final int id;

    @EqualsAndHashCode.Include
    protected final P properties;

    protected final AtomicLong counter = new AtomicLong(0L);
    protected final Multiset<String> errors = ConcurrentHashMultiset.create();
    protected final StepLong intervalCounter = new StepLong(Clock.SYSTEM, UPDATE_INTERVAL);
    protected final AtomicReference<T> last = new AtomicReference<>();
    protected final Logger log = LogManager.getLogger(getClass());
    protected final Stopwatch stopwatch = Stopwatch.createStarted();

    @Override
    public long getCount() {
        return counter.get();
    }

    @Override
    public Duration getElapsed() {
        return stopwatch.elapsed();
    }

    @Override
    public Map<String, Integer> getErrors() {
        Map<String, Integer> errorCounts = new TreeMap<>();
        errors.forEachEntry(errorCounts::put);
        return Collections.unmodifiableMap(errorCounts);
    }

    public Optional<T> getLast() {
        return Optional.ofNullable(last.get());
    }

    @Override
    public double getRate() {
        long intervalCount = intervalCounter.poll();
        return Precision.round((intervalCount * 1000.0) / UPDATE_INTERVAL, 1);
    }

    @Override
    public ScenarioStatus getStatus() {
        if (!isRunning()) {
            return ScenarioStatus.COMPLETED;
        } else if (getRate() <= 0.0) {
            return ScenarioStatus.IDLE;
        } else {
            return ScenarioStatus.RUNNING;
        }
    }

    @Override
    public boolean isRunning() {
        return stopwatch.isRunning();
    }

    @Override
    public void onComplete() {
        if (isRunning()) {
            stopwatch.stop();
            log.info("Stopping '{}' scenario", this);
        }
    }

    @Override
    public void onError(Throwable throwable) {
        Throwable rootCause = Throwables.getRootCause(throwable);
        errors.add(rootCause.getClass().getSimpleName());
    }

    @Override
    public void onNext(T response) {
        counter.incrementAndGet();
        intervalCounter.getCurrent().increment();
        log.trace("{}: Received response {}", this, response);
        last.set(response);
    }

    @Override
    public String toString() {
        return getName();
    }
}
