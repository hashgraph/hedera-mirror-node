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

import com.google.common.base.Throwables;
import io.grpc.StatusRuntimeException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.Timer;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;

import com.hedera.datagenerator.sdk.supplier.TransactionType;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.PrecheckStatusException;
import com.hedera.hashgraph.sdk.ReceiptStatusException;
import com.hedera.mirror.monitor.converter.DurationToStringSerializer;

@Log4j2
@Named
@RequiredArgsConstructor
public class PublishMetrics {

    static final String METRIC_DURATION = "hedera.mirror.monitor.publish.duration";
    static final String METRIC_HANDLE = "hedera.mirror.monitor.publish.handle";
    static final String METRIC_SUBMIT = "hedera.mirror.monitor.publish.submit";
    static final String SUCCESS = "SUCCESS";
    static final String UNKNOWN = "unknown";

    private final Map<Tags, TimeGauge> durationGauges = new ConcurrentHashMap<>();
    private final Map<Tags, Timer> handleTimers = new ConcurrentHashMap<>();
    private final Map<Tags, Timer> submitTimers = new ConcurrentHashMap<>();
    private final MeterRegistry meterRegistry;
    private final PublishProperties publishProperties;

    public void onSuccess(PublishResponse response) {
        recordMetric(response.getRequest(), response, SUCCESS);
    }

    public void onError(PublishException publishException) {
        PublishRequest request = publishException.getPublishRequest();
        String status;
        TransactionType type = request.getScenario().getProperties().getType();
        Throwable throwable = Throwables.getRootCause(publishException);

        if (throwable instanceof PrecheckStatusException) {
            PrecheckStatusException pse = (PrecheckStatusException) throwable;
            status = pse.status.toString();
            log.debug("Network error {} submitting {} transaction: {}", status, type, pse.getMessage());
        } else if (throwable instanceof ReceiptStatusException) {
            ReceiptStatusException rse = (ReceiptStatusException) throwable;
            status = rse.receipt.status.toString();
            log.debug("Hedera error for {} transaction {}: {}", type, rse.transactionId,
                    rse.getMessage());
        } else if (throwable instanceof StatusRuntimeException) {
            StatusRuntimeException sre = (StatusRuntimeException) throwable;
            status = sre.getStatus().getCode().toString();
            log.debug("GRPC error: {}", sre.getMessage());
        } else {
            status = throwable.getClass().getSimpleName();
            log.debug("{} submitting {} transaction: {}", status, type, throwable.getMessage());
        }

        recordMetric(request, null, status);
    }

    private void recordMetric(PublishRequest request, PublishResponse response, String status) {
        String node = Optional.of(request.getTransaction().getNodeAccountIds())
                .filter(l -> !l.isEmpty())
                .map(l -> l.get(0))
                .map(AccountId::toString)
                .orElse(UNKNOWN);
        long startTime = request.getTimestamp().toEpochMilli();
        long endTime = response != null ? response.getTimestamp().toEpochMilli() : System.currentTimeMillis();
        Tags tags = new Tags(node, request.getScenario(), status);

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
        return TimeGauge.builder(METRIC_DURATION, tags.getScenario(), unit, s -> s.getElapsed().toNanos())
                .description("The amount of time this scenario has been publishing transactions")
                .tag(Tags.TAG_NODE, tags.getNode())
                .tag(Tags.TAG_SCENARIO, tags.getScenario().getName())
                .tag(Tags.TAG_TYPE, tags.getType())
                .register(meterRegistry);
    }

    private Timer newHandleMetric(Tags tags) {
        return Timer.builder(METRIC_HANDLE)
                .description("The time it takes from submit to being handled by the main nodes")
                .tag(Tags.TAG_NODE, tags.getNode())
                .tag(Tags.TAG_SCENARIO, tags.getScenario().getName())
                .tag(Tags.TAG_STATUS, tags.getStatus())
                .tag(Tags.TAG_TYPE, tags.getType())
                .register(meterRegistry);
    }

    private Timer newSubmitMetric(Tags tags) {
        return Timer.builder(METRIC_SUBMIT)
                .description("The time it takes to submit a transaction")
                .tag(Tags.TAG_NODE, tags.getNode())
                .tag(Tags.TAG_SCENARIO, tags.getScenario().getName())
                .tag(Tags.TAG_STATUS, tags.getStatus())
                .tag(Tags.TAG_TYPE, tags.getType())
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${hedera.mirror.monitor.publish.statusFrequency:10000}")
    public void status() {
        if (publishProperties.isEnabled()) {
            durationGauges.keySet().stream().map(Tags::getScenario).distinct().forEach(this::status);
        }
    }

    private void status(PublishScenario scenario) {
        if (scenario.isRunning()) {
            String elapsed = DurationToStringSerializer.convert(scenario.getElapsed());
            log.info("{}: {} transactions in {} at {}/s. Errors: {}",
                    scenario, scenario.getCount(), elapsed, scenario.getRate(), scenario.getErrors());
        }
    }

    @Value
    class Tags {
        static final String TAG_NODE = "node";
        static final String TAG_SCENARIO = "scenario";
        static final String TAG_STATUS = "status";
        static final String TAG_TYPE = "type";

        private final String node;
        private final PublishScenario scenario;
        private final String status;

        private String getType() {
            return scenario.getProperties().getType().toString();
        }
    }
}
