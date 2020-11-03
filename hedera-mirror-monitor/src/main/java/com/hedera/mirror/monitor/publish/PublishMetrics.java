package com.hedera.mirror.monitor.publish;

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
import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.Multiset;
import io.grpc.StatusRuntimeException;
import io.micrometer.core.instrument.MeterRegistry;
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
    private final Map<Tags, Timer> timers = new ConcurrentHashMap<>();
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

        try {
            PublishResponse response = function.apply(publishRequest);
            counter.incrementAndGet();
            return response;
        } catch (LocalValidationException e) {
            log.error("Local error. Halting thread", e);
            throw e;
        } catch (StatusRuntimeException e) {
            StatusRuntimeException sre = (StatusRuntimeException) e.getCause();
            String code = sre.getStatus().getCode().name();
            log.debug("Network error {} submitting {} transaction: {}", code, type, sre.getStatus().getDescription());
            status = code;
        } catch (HederaStatusException e) {
            log.debug("Hedera status error submitting {} transaction: {}", type, e.getMessage());
            status = e.status.name();
        } catch (Exception e) {
            log.debug("Unknown error submitting {} transaction: {}", type, e.getMessage());
            status = e.getClass().getSimpleName();
        } finally {
            long endTime = System.currentTimeMillis();
            Tags tags = new Tags(status, type);
            Timer timer = timers.computeIfAbsent(tags, this::newTimer);
            timer.record(endTime - startTime, TimeUnit.MILLISECONDS);

            if (!SUCCESS.equals(status)) {
                errors.add(status);
            }
        }

        return null;
    }

    private Timer newTimer(Tags tags) {
        return Timer.builder("hedera.mirror.monitor.publish")
                .description("The time it takes to publish a transaction")
                .tag("status", tags.getStatus())
                .tag("type", tags.getType().toString())
                .register(meterRegistry);
    }

    @Scheduled(fixedDelay = 5000)
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
        private final String status;
        private final TransactionType type;
    }
}
