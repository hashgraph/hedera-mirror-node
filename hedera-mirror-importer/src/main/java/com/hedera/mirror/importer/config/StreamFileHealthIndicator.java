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
import io.micrometer.core.instrument.search.Search;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

@RequiredArgsConstructor
public class StreamFileHealthIndicator implements HealthIndicator {
    public static final String STREAM_FILE_PARSE_DURATION = "hedera.mirror.parse.duration";
    public static final Health HEALTH_DOWN = Health.down().build();
    public static final Health HEALTH_UP = Health.up().build();

    private final MeterRegistry meterRegistry;
    private final String streamType;
    private final Duration streamFileStatusCheckWindow;
    private final Instant endDate;

    private final AtomicLong lastCount = new AtomicLong(0);
    private Instant lastCheck = Instant.now();
    private Health lastHealthStatus = Health.unknown().build(); // unknown until at least 1 stream file is parsed

    @Override
    public Health health() {
        Instant currentInstant = Instant.now();
        Search searchTimer = meterRegistry.find(STREAM_FILE_PARSE_DURATION).tag("type", streamType);
        long currentCount = searchTimer.timer().count();

        Health health = currentCount > lastCount.getAndSet(currentCount) ? HEALTH_UP : HEALTH_DOWN;
        if (health == HEALTH_DOWN && currentInstant.isBefore(lastCheck.plus(streamFileStatusCheckWindow))) {
            // consider case where endTime has been passed
            if (endDate.isBefore(currentInstant)) {
                return HEALTH_UP;
            }

            // return cached value while in window
            return lastHealthStatus;
        }

        lastHealthStatus = health;
        lastCount.set(currentCount);
        lastCheck = currentInstant;
        return health;
    }
}
