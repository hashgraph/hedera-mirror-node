package com.hedera.mirror.monitor.publish;

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
import io.grpc.StatusRuntimeException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.Timer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.math3.util.Precision;
import org.springframework.scheduling.annotation.Scheduled;

import com.hedera.datagenerator.sdk.supplier.TransactionType;
import com.hedera.hashgraph.sdk.HederaStatusException;
import com.hedera.hashgraph.sdk.LocalValidationException;

@Log4j2
@Named
@RequiredArgsConstructor
public class PublishMetrics {

    private static final String SUCCESS = "SUCCESS";

    private final AtomicLong counter = new AtomicLong(0L);
    private final Multiset<String> errors = ConcurrentHashMultiset.create();
    private final Stopwatch stopwatch = Stopwatch.createStarted();
    private final Map<Tags, Timer> handleTimers = new ConcurrentHashMap<>();
    private final Map<Tags, Timer> submitTimers = new ConcurrentHashMap<>();
    private final Map<Tags, TimeGauge> durationGauges = new ConcurrentHashMap<>();
    private final MeterRegistry meterRegistry;

    @FunctionalInterface
    interface CheckedFunction<T, R> {
        R apply(T t) throws Exception;
    }

    public PublishResponse record(PublishRequest publishRequest,
                                  CheckedFunction<PublishRequest, PublishResponse> function) {
        long startTime = System.currentTimeMillis();
        String status = SUCCESS;
        TransactionType type = publishRequest.getType();
        PublishResponse response = null;

        try {
            response = function.apply(publishRequest);
            counter.incrementAndGet();

            return response;
        } catch (LocalValidationException e) {
            throw e;
        } catch (StatusRuntimeException e) {
            StatusRuntimeException sre = (StatusRuntimeException) e.getCause();
            status = sre.getStatus().getCode().name();
            log.debug("Network error {} submitting {} transaction: {}", status, type, sre.getStatus().getDescription());
            throw new PublishException(e);
        } catch (HederaStatusException e) {
            status = e.status.name();
            log.debug("Hedera {} error submitting {} transaction: {}", status, type, e.getMessage());
            throw new PublishException(e);
        } catch (Exception e) {
            status = e.getClass().getSimpleName();
            log.debug("{} submitting {} transaction: {}", status, type, e.getMessage());
            throw new PublishException(e);
        } finally {
            long endTime = response != null ? response.getTimestamp().toEpochMilli() : System.currentTimeMillis();
            String scenarioName = publishRequest.getScenarioName();
            Tags tags = new Tags(scenarioName, status, type);
            Timer submitTimer = submitTimers.computeIfAbsent(tags, this::newSubmitMetric);
            submitTimer.record(endTime - startTime, TimeUnit.MILLISECONDS);
            durationGauges.computeIfAbsent(tags, this::newDurationMetric);

            if (response != null && response.getReceipt() != null) {
                long elapsed = System.currentTimeMillis() - startTime;
                Timer handleTimer = handleTimers.computeIfAbsent(tags, this::newHandleMetric);
                handleTimer.record(elapsed, TimeUnit.MILLISECONDS);
            }

            if (!SUCCESS.equals(status)) {
                errors.add(status);
            }
        }
    }

    private TimeGauge newDurationMetric(Tags tags) {
        TimeUnit unit = TimeUnit.NANOSECONDS;
        return TimeGauge.builder("hedera.mirror.monitor.publish.duration", stopwatch, unit, s -> s.elapsed(unit))
                .description("The amount of time this scenario has been publishing transactions")
                .tag(Tags.TAG_SCENARIO, tags.getScenarioName())
                .tag(Tags.TAG_TYPE, tags.getType().toString())
                .register(meterRegistry);
    }

    private Timer newHandleMetric(Tags tags) {
        return Timer.builder("hedera.mirror.monitor.publish.handle")
                .description("The time it takes from submit to being handled by the main nodes")
                .tag(Tags.TAG_SCENARIO, tags.getScenarioName())
                .tag(Tags.TAG_STATUS, tags.getStatus())
                .tag(Tags.TAG_TYPE, tags.getType().toString())
                .register(meterRegistry);
    }

    private Timer newSubmitMetric(Tags tags) {
        return Timer.builder("hedera.mirror.monitor.publish.submit")
                .description("The time it takes to submit a transaction")
                .tag(Tags.TAG_SCENARIO, tags.getScenarioName())
                .tag(Tags.TAG_STATUS, tags.getStatus())
                .tag(Tags.TAG_TYPE, tags.getType().toString())
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${hedera.mirror.monitor.publish.statusFrequency:10000}")
    public void status() {
        long count = counter.get();
        long elapsed = stopwatch.elapsed(TimeUnit.MICROSECONDS);
        double rate = Precision.round(elapsed > 0 ? (1000000.0 * count) / elapsed : 0.0, 1);
        Map<String, Integer> errorCounts = new HashMap<>();
        errors.forEachEntry((k, v) -> errorCounts.put(k, v));
        log.info("Published {} transactions in {} at {}/s. Errors: {}", count, stopwatch, rate, errorCounts);
    }

    @Value
    private class Tags {
        private static final String TAG_SCENARIO = "scenario";
        private static final String TAG_STATUS = "status";
        private static final String TAG_TYPE = "type";

        private final String scenarioName;
        private final String status;
        private final TransactionType type;
    }
}
