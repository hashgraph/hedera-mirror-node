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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.search.Search;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;

import com.hedera.mirror.importer.domain.StreamType;
import com.hedera.mirror.importer.parser.AbstractParserProperties;
import com.hedera.mirror.importer.parser.balance.BalanceParserProperties;
import com.hedera.mirror.importer.parser.event.EventParserProperties;
import com.hedera.mirror.importer.parser.record.RecordParserProperties;

public class StreamFileHealthIndicator implements HealthIndicator {
    private static final String COUNT_KEY = "count";
    private static final String LAST_CHECK_KEY = "lastCheck";
    private static final String REASON_KEY = "reason";

    private final boolean enabled;
    private final Instant endDate;
    private final MeterRegistry meterRegistry;
    private final Duration streamFileStatusCheckBuffer;
    private final StreamType streamType;
    private final AtomicReference<Health> lastHealthStatus = new AtomicReference<>(Health
            .unknown()
            .withDetail(COUNT_KEY, 0L)
            .withDetail(LAST_CHECK_KEY, Instant.now())
            .withDetail(REASON_KEY, "Starting up, no files parsed yet")
            .build()); // unknown until at least 1 stream file is parsed

    public StreamFileHealthIndicator(AbstractParserProperties abstractParserProperties, MeterRegistry meterRegistry) {
        enabled = abstractParserProperties.isEnabled();
        this.meterRegistry = meterRegistry;
        streamType = abstractParserProperties.getStreamType();
        switch (streamType) {
            case BALANCE:
                BalanceParserProperties balanceParserProperties = (BalanceParserProperties) abstractParserProperties;
                endDate = balanceParserProperties.getMirrorProperties().getEndDate();
                streamFileStatusCheckBuffer = balanceParserProperties.getStreamFileStatusCheckBuffer();
                break;
            case EVENT:
                EventParserProperties eventParserProperties = (EventParserProperties) abstractParserProperties;
                endDate = eventParserProperties.getMirrorProperties().getEndDate();
                streamFileStatusCheckBuffer = eventParserProperties.getStreamFileStatusCheckBuffer();
                break;
            case RECORD:
                RecordParserProperties recordParserProperties = (RecordParserProperties) abstractParserProperties;
                endDate = recordParserProperties.getMirrorProperties().getEndDate();
                streamFileStatusCheckBuffer = recordParserProperties.getStreamFileStatusCheckBuffer();
                break;
            default:
                throw new UnsupportedOperationException();
        }
    }

    @Override
    public Health health() {
        Instant currentInstant = Instant.now();

        // consider case where parsing is disabled
        if (!enabled) {
            return Health
                    .unknown()
                    .withDetail(REASON_KEY, String.format("%s parsing is disabled", streamType))
                    .build();
        }

        // consider case where endTime has been passed
        if (endDate.isBefore(currentInstant)) {
            return Health
                    .up()
                    .withDetail(
                            REASON_KEY,
                            String.format("EndDate: %s has passed, no stream files are no longer expected", endDate))
                    .build();
        }

        Search searchTimer = meterRegistry.find("hedera.mirror.stream.close.latency");
        Timer streamFileParseDurationTimer = searchTimer
                .tag("type", streamType.toString())
                .tag("success", "true")
                .timer();
        long currentCount = streamFileParseDurationTimer.count();

        Map<String, Object> healthStatusDetails = lastHealthStatus.get().getDetails();
        long lastCount = (Long) healthStatusDetails.get(COUNT_KEY);
        Health health = currentCount > lastCount ?
                getHealthUp(streamFileParseDurationTimer, currentInstant) :
                getHealthDown(streamFileParseDurationTimer, currentInstant);

        // handle down but in window of allowance
        if (health.getStatus() == Status.DOWN) {
            long mean = (long) streamFileParseDurationTimer.mean(TimeUnit.MILLISECONDS);
            Instant lastCheck = (Instant) healthStatusDetails.get(LAST_CHECK_KEY);
            if (currentInstant.isBefore(lastCheck.plusMillis(mean).plus(streamFileStatusCheckBuffer))) {
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
}
