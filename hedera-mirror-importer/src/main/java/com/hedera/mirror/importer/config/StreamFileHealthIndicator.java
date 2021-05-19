package com.hedera.mirror.importer.config;

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

import static com.hedera.mirror.importer.downloader.Downloader.STREAM_CLOSE_LATENCY_METRIC_NAME;
import static com.hedera.mirror.importer.parser.StreamFileParser.STREAM_PARSE_DURATION_METRIC_NAME;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;

import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.parser.ParserProperties;

@Log4j2
@RequiredArgsConstructor
public class StreamFileHealthIndicator implements HealthIndicator {
    private static final String COUNT_KEY = "count";
    private static final String LAST_CHECK_KEY = "lastCheck";
    private static final String REASON_KEY = "reason";

    private final ParserProperties parserProperty;
    private final MeterRegistry meterRegistry;
    private final MirrorProperties mirrorProperties;
    private final Health parsingDisabledHealth = Health
            .unknown()
            .withDetail(REASON_KEY, "Parsing is disabled")
            .build();
    private final Health missingParseDurationTimerHealth = Health
            .unknown()
            .withDetail(REASON_KEY, String.format("%s timer is missing", STREAM_PARSE_DURATION_METRIC_NAME))
            .build();
    private final Health missingStreamCloseTimerHealth = Health
            .unknown()
            .withDetail(REASON_KEY, String.format("%s timer is missing", STREAM_CLOSE_LATENCY_METRIC_NAME))
            .build();
    private final Health endDataInPastHealth;

    private final AtomicReference<Health> lastHealthStatus = new AtomicReference<>(Health
            .unknown()
            .withDetail(COUNT_KEY, 0L)
            .withDetail(LAST_CHECK_KEY, Instant.now())
            .withDetail(REASON_KEY, "Starting up, no files parsed yet")
            .build()); // unknown until at least 1 stream file is parsed

    public StreamFileHealthIndicator(ParserProperties parserProperty, MeterRegistry meterRegistry,
                                     MirrorProperties mirrorProperties) {
        this.parserProperty = parserProperty;
        this.meterRegistry = meterRegistry;
        this.mirrorProperties = mirrorProperties;
        endDataInPastHealth = Health
                .up()
                .withDetail(
                        REASON_KEY,
                        String.format(
                                "EndDate: %s has passed, stream files are no longer expected",
                                mirrorProperties.getEndDate()))
                .build();
    }

    @Override
    public Health health() {
        Instant currentInstant = Instant.now();

        // consider case where parsing is disabled
        if (!parserProperty.isEnabled()) {
            return parsingDisabledHealth;
        }

        // consider case where endTime has been passed
        if (mirrorProperties.getEndDate().isBefore(currentInstant)) {
            return endDataInPastHealth;
        }

        Timer streamParseDurationTimer = getTimer(
                STREAM_PARSE_DURATION_METRIC_NAME,
                Tags.of(
                        "type", parserProperty.getStreamType().toString(),
                        "success", "true"));
        if (streamParseDurationTimer == null) {
            return missingParseDurationTimerHealth;
        }

        long currentCount = streamParseDurationTimer.count();
        Map<String, Object> healthStatusDetails = lastHealthStatus.get().getDetails();
        long lastCount = (Long) healthStatusDetails.get(COUNT_KEY);
        Health health = currentCount > lastCount ?
                getHealthUp(streamParseDurationTimer, currentInstant) :
                getHealthDown(streamParseDurationTimer, currentInstant);

        // handle down but in window of allowance
        if (health.getStatus() == Status.DOWN) {
            Timer streamCloseLatencyDurationTimer = getTimer(
                    STREAM_CLOSE_LATENCY_METRIC_NAME,
                    Tags.of("type", parserProperty.getStreamType().toString()));
            if (streamCloseLatencyDurationTimer == null) {
                return missingStreamCloseTimerHealth;
            }

            long mean = (long) streamCloseLatencyDurationTimer.mean(TimeUnit.MILLISECONDS);
            Instant lastCheck = (Instant) healthStatusDetails.get(LAST_CHECK_KEY);
            if (currentInstant.isBefore(lastCheck.plusMillis(mean).plus(parserProperty.getProcessingTimeout()))) {
                // return cached value while in window
                return lastHealthStatus.get();
            }
        }

        lastHealthStatus.set(health);

        return health;
    }

    private Health getHealthDown(Timer streamFileParseDurationTimer, Instant currentInstant) {
        Health.Builder healthBuilder = Health
                .down()
                .withDetail(REASON_KEY, String
                        .format("No new stream stream files have been parsed since: %s", currentInstant));

        return getHealth(healthBuilder, streamFileParseDurationTimer, currentInstant);
    }

    private Health getHealthUp(Timer streamFileParseDurationTimer, Instant currentInstant) {
        return getHealth(Health.up(), streamFileParseDurationTimer, currentInstant);
    }

    private Health getHealth(Health.Builder healthBuilder, Timer streamFileParseDurationTimer, Instant currentInstant) {
        return healthBuilder
                .withDetail(COUNT_KEY, streamFileParseDurationTimer.count())
                .withDetail(LAST_CHECK_KEY, currentInstant)
                .withDetail("max", streamFileParseDurationTimer.max(TimeUnit.MILLISECONDS))
                .withDetail("mean", streamFileParseDurationTimer.mean(TimeUnit.MILLISECONDS))
                .build();
    }

    private Timer getTimer(String metricName, Tags additionalMetricTags) {
        try {
            return meterRegistry
                    .find(metricName)
                    .tags(additionalMetricTags)
                    .timer();
        } catch (Exception ex) {
            log.trace("metricRegistry missing timer or tags for '{}'.", metricName, ex);
            return null;
        }
    }
}
