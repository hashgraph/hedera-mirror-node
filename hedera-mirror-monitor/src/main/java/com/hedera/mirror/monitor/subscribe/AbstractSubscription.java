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
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.math3.util.Precision;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public abstract class AbstractSubscription<P extends AbstractSubscriberProperties, T> implements Subscription {

    private static final long UPDATE_INTERVAL = 30L * 1000000L; // 30s in microseconds

    @EqualsAndHashCode.Include
    protected final int id;

    @EqualsAndHashCode.Include
    protected final P properties;

    protected final AtomicLong count = new AtomicLong(0L);
    protected final Multiset<String> errors = ConcurrentHashMultiset.create();
    protected final AtomicLong lastCount = new AtomicLong(0L);
    protected final AtomicLong lastElapsed = new AtomicLong(0L);
    protected final Logger log = LogManager.getLogger(getClass());
    protected final Stopwatch stopwatch = Stopwatch.createStarted();
    protected volatile Optional<T> last = Optional.empty();

    @Override
    public long getCount() {
        return count.get();
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

    @Override
    public double getRate() {
        long previousCount = getCount();
        long elapsed = stopwatch.elapsed(TimeUnit.MICROSECONDS);
        long instantCount = previousCount - lastCount.get();
        long instantElapsed = elapsed - lastElapsed.get();

        // Since multiple threads are calling this, only update the statistics periodically
        if (instantElapsed >= UPDATE_INTERVAL) {
            lastCount.set(previousCount);
            lastElapsed.set(elapsed);
        }

        return getRate(instantCount, instantElapsed);
    }

    private double getRate(long count, long elapsedMicros) {
        return Precision.round(elapsedMicros > 0 ? (count * 1000000.0) / elapsedMicros : 0.0, 1);
    }

    @Override
    public SubscriptionStatus getStatus() {
        if (!stopwatch.isRunning()) {
            return SubscriptionStatus.COMPLETED;
        } else if (getRate() <= 0.0) {
            return SubscriptionStatus.IDLE;
        } else {
            return SubscriptionStatus.RUNNING;
        }
    }

    public void onComplete() {
        stopwatch.stop();
        log.info("Stopping '{}' subscription", this);
    }

    public void onError(Throwable t) {
        errors.add(t.getClass().getSimpleName());
    }

    public void onNext(T response) {
        count.incrementAndGet();
        log.trace("{}: Received response {}", this, response);
        last = Optional.of(response);
    }

    @Override
    public String toString() {
        String name = getName();
        return getProperties().getSubscribers() <= 1 ? name : name + " #" + getId();
    }
}
