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
import java.util.concurrent.CompletableFuture;
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
import com.hedera.hashgraph.sdk.PrecheckStatusException;
import com.hedera.hashgraph.sdk.ReceiptStatusException;

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
    private final AtomicLong lastCount = new AtomicLong();
    private final AtomicLong lastElapsed = new AtomicLong();

    @FunctionalInterface
    interface CheckedFunction<T, R> {
        R apply(T t) throws Exception;
    }

    public CompletableFuture<PublishResponse> record(PublishRequest publishRequest,
            CheckedFunction<PublishRequest, CompletableFuture<PublishResponse>> function) {
        long startTime = System.currentTimeMillis();

        try {
            return function.apply(publishRequest)
                    .thenApply(response -> {
                        counter.incrementAndGet();
                        recordMetric(publishRequest, response, SUCCESS, startTime);
                        return response;
                    })
                    .exceptionally(throwable -> {
                        Throwable cause = throwable.getCause();
                        String status;
                        TransactionType type = publishRequest.getType();

                        if (cause instanceof PrecheckStatusException) {
                            PrecheckStatusException pse = (PrecheckStatusException) cause;
                            status = pse.status.toString();
                            log.debug("Network error {} submitting {} transaction: {}", status, type, pse.getMessage());
                        } else if (cause instanceof ReceiptStatusException) {
                            ReceiptStatusException rse = (ReceiptStatusException) cause;
                            status = rse.receipt.status.toString();
                            log.debug("Hedera error for {} transaction {}: {}", type, rse.transactionId,
                                    rse.getMessage());
                        } else if (cause instanceof StatusRuntimeException) {
                            StatusRuntimeException sre = (StatusRuntimeException) cause;
                            status = sre.getStatus().getCode().toString();
                            log.debug("GRPC error: {}", sre.getMessage());
                        } else {
                            status = cause.getClass().getSimpleName();
                            log.debug("{} submitting {} transaction: {}", status, type, cause.getMessage());
                        }

                        errors.add(status);
                        recordMetric(publishRequest, null, status, startTime);

                        throw new PublishException(cause);
                    });
        } catch (Exception ex) {
            log.error(ex);
            throw new PublishException(ex);
        }
    }

    private void recordMetric(PublishRequest request, PublishResponse response, String status, long startTime) {
        String scenarioName = request.getScenarioName();
        TransactionType type = request.getType();

        long endTime = response != null ? response.getTimestamp().toEpochMilli() : System.currentTimeMillis();
        Tags tags = new Tags(scenarioName, status, type);
        Timer submitTimer = submitTimers.computeIfAbsent(tags, this::newSubmitMetric);
        submitTimer.record(endTime - startTime, TimeUnit.MILLISECONDS);
        durationGauges.computeIfAbsent(tags, this::newDurationMetric);

        if (response != null && response.getReceipt() != null) {
            long elapsed = System.currentTimeMillis() - startTime;
            Timer handleTimer = handleTimers.computeIfAbsent(tags, this::newHandleMetric);
            handleTimer.record(elapsed, TimeUnit.MILLISECONDS);
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
        double averageRate = getRate(count, elapsed);
        long instantCount = count - lastCount.get();
        long instantElapsed = elapsed - lastElapsed.get();
        double instantRate = getRate(instantCount, instantElapsed);
        Map<String, Integer> errorCounts = new HashMap<>();
        errors.forEachEntry((k, v) -> errorCounts.put(k, v));
        log.info("Published {} transactions in {} at {}/s, {} transactions in last {} s at {}/s. Errors: {}",
                count, stopwatch, averageRate, instantCount, toSeconds(instantElapsed), instantRate, errorCounts);

        lastCount.set(count);
        lastElapsed.set(elapsed);
    }

    private double getRate(long count, long elapsedMicros) {
        return Precision.round(elapsedMicros > 0 ? (count * 1000000.0) / elapsedMicros : 0.0, 1);
    }

    private double toSeconds(long micros) {
        return Precision.round(micros * 1.0 / 1_000_000, 2);
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
