package com.hedera.mirror.importer.config;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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
import static com.hedera.mirror.importer.parser.AbstractStreamFileParser.STREAM_PARSE_DURATION_METRIC_NAME;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;

import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.leader.LeaderService;
import com.hedera.mirror.importer.parser.ParserProperties;

@RequiredArgsConstructor
public class StreamFileHealthIndicator implements HealthIndicator {
    // health details constants
    private static final String COUNT_KEY = "count";
    private static final String LAST_CHECK_KEY = "lastCheck";
    private static final String MISSING_TIMER_REASON = " timer is missing";
    private static final String REASON_KEY = "reason";

    // static Health responses
    private static final Health parsingDisabledHealth = getHealthWithReason(Status.UNKNOWN, "Parsing is disabled");
    private static final Health missingParseDurationTimerHealth =
            getHealthWithReason(Status.UNKNOWN, STREAM_PARSE_DURATION_METRIC_NAME + MISSING_TIMER_REASON);
    private static final Health missingStreamCloseTimerHealth =
            getHealthWithReason(Status.UNKNOWN, STREAM_CLOSE_LATENCY_METRIC_NAME + MISSING_TIMER_REASON);
    private static final Health endDateInPastHealth =
            getHealthWithReason(Status.UP, "EndDate has passed, stream files are no longer expected");
    private static final Health notLeaderHealth = Health.unknown().withDetail(REASON_KEY, "Not currently leader")
            .build();
    private final AtomicReference<Health> lastHealthStatus = new AtomicReference<>(Health
            .unknown()
            .withDetail(COUNT_KEY, 0L)
            .withDetail(LAST_CHECK_KEY, Instant.now())
            .withDetail(REASON_KEY, "Starting up, no files parsed yet")
            .build()); // unknown until at least 1 stream file is parsed

    private final LeaderService leaderService;
    private final MeterRegistry meterRegistry;
    private final MirrorProperties mirrorProperties;
    private final ParserProperties parserProperty;

    @Override
    public Health health() {
        Instant currentInstant = Instant.now();

        // consider case where parsing is disabled
        if (!parserProperty.isEnabled()) {
            return parsingDisabledHealth;
        }

        // consider case where endTime has been passed
        if (mirrorProperties.getEndDate().isBefore(currentInstant)) {
            return endDateInPastHealth;
        }

        if (!leaderService.isLeader()) {
            return notLeaderHealth;
        }

        Timer streamParseDurationTimer = meterRegistry
                .find(STREAM_PARSE_DURATION_METRIC_NAME)
                .tags(Tags.of(
                        "type", parserProperty.getStreamType().toString(),
                        "success", "true"))
                .timer();
        if (streamParseDurationTimer == null) {
            return missingParseDurationTimerHealth;
        }

        long currentCount = streamParseDurationTimer.count();
        Map<String, Object> healthStatusDetails = lastHealthStatus.get().getDetails();
        long lastCount = (Long) healthStatusDetails.get(COUNT_KEY);
        Instant lastCheck = (Instant) healthStatusDetails.get(LAST_CHECK_KEY);
        Health health = currentCount > lastCount ?
                getHealthUp(streamParseDurationTimer, currentInstant) :
                getHealthDown(streamParseDurationTimer, currentInstant, lastCheck);

        // handle down but in window of allowance
        if (health.getStatus() == Status.DOWN) {
            Health resolvedHealth = getResolvedHealthWhenNoStreamFilesParsed(currentInstant, lastCheck);
            if (resolvedHealth != null) {
                return resolvedHealth;
            }
        }

        lastHealthStatus.set(health);

        return health;
    }

    private Instant getStartTime() {
        return mirrorProperties.getStartDate() == null ?
                MirrorDateRangePropertiesProcessor.STARTUP_TIME : mirrorProperties.getStartDate();
    }

    private Health getResolvedHealthWhenNoStreamFilesParsed(Instant currentInstant, Instant lastCheck) {
        Timer streamCloseLatencyDurationTimer = meterRegistry
                .find(STREAM_CLOSE_LATENCY_METRIC_NAME)
                .tags(Tags.of("type", parserProperty.getStreamType().toString()))
                .timer();
        if (streamCloseLatencyDurationTimer == null) {
            return missingStreamCloseTimerHealth;
        }

        long mean = (long) streamCloseLatencyDurationTimer.mean(TimeUnit.MILLISECONDS);
        if (mean == 0 && currentInstant.isBefore(getStartTime()
                .plus(parserProperty.getStreamType().getFileCloseInterval()))) {
            // return cached value on start up before end of first expected file close interval
            return lastHealthStatus.get();
        }

        long fileCloseMean = mean == 0 ? parserProperty.getStreamType().getFileCloseInterval().toMillis() : mean;
        if (currentInstant.isBefore(lastCheck.plusMillis(fileCloseMean).plus(parserProperty.getProcessingTimeout()))) {
            // return cached value while in window
            return lastHealthStatus.get();
        }

        return null;
    }

    private static Health getHealthWithReason(Status status, String reason) {
        return Health
                .status(status)
                .withDetail(REASON_KEY, reason)
                .build();
    }

    private Health getHealthDown(Timer streamFileParseDurationTimer, Instant currentInstant, Instant lastCheck) {
        Health.Builder healthBuilder = Health
                .down()
                .withDetail(REASON_KEY, String
                        .format("No new stream stream files have been parsed since: %s", lastCheck));

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
                .withDetail("type", parserProperty.getStreamType().toString())
                .build();
    }
}
