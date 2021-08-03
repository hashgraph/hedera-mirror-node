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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;

import com.hedera.mirror.monitor.converter.DurationToStringSerializer;

@Log4j2
@Named
@RequiredArgsConstructor
public class SubscribeMetrics {

    static final String METRIC_DURATION = "hedera.mirror.monitor.subscribe.duration";
    static final String METRIC_E2E = "hedera.mirror.monitor.subscribe.e2e";
    static final String TAG_PROTOCOL = "protocol";
    static final String TAG_SCENARIO = "scenario";
    static final String TAG_SUBSCRIBER = "subscriber";

    private final Map<Scenario<?, ?>, TimeGauge> durationMetrics = new ConcurrentHashMap<>();
    private final Map<Scenario<?, ?>, Timer> latencyMetrics = new ConcurrentHashMap<>();
    private final MeterRegistry meterRegistry;
    private final SubscribeProperties subscribeProperties;

    public void onNext(SubscribeResponse response) {
        log.trace("Response: {}", response);
        Scenario<?, ?> scenario = response.getScenario();
        Instant publishedTimestamp = response.getPublishedTimestamp();
        durationMetrics.computeIfAbsent(scenario, this::newDurationGauge);

        if (publishedTimestamp != null) {
            Duration latency = Duration.between(publishedTimestamp, response.getReceivedTimestamp());
            latencyMetrics.computeIfAbsent(scenario, this::newLatencyTimer).record(latency);
        }
    }

    private TimeGauge newDurationGauge(Scenario<?, ?> scenario) {
        return TimeGauge.builder(METRIC_DURATION, scenario, TimeUnit.NANOSECONDS, s -> s.getElapsed().toNanos())
                .description("How long the subscriber has been running")
                .tag(TAG_PROTOCOL, scenario.getProtocol().toString())
                .tag(TAG_SCENARIO, scenario.getName())
                .tag(TAG_SUBSCRIBER, String.valueOf(scenario.getId()))
                .register(meterRegistry);
    }

    private final Timer newLatencyTimer(Scenario<?, ?> scenario) {
        return Timer.builder(METRIC_E2E)
                .description("The end to end transaction latency starting from publish and ending at receive")
                .tag(TAG_PROTOCOL, scenario.getProtocol().toString())
                .tag(TAG_SCENARIO, scenario.getName())
                .tag(TAG_SUBSCRIBER, String.valueOf(scenario.getId()))
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${hedera.mirror.monitor.subscribe.statusFrequency:10000}")
    public void status() {
        if (subscribeProperties.isEnabled()) {
            durationMetrics.keySet()
                    .stream()
                    .filter(Scenario::isRunning)
                    .forEach(this::status);
        }
    }

    private void status(Scenario<?, ?> s) {
        String elapsed = DurationToStringSerializer.convert(s.getElapsed());
        log.info("{} {}: {} transactions in {} at {}/s. Errors: {}",
                s.getProtocol(), s, s.getCount(), elapsed, s.getRate(), s.getErrors());
    }
}
