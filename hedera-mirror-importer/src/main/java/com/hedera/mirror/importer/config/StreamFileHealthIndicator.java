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

import com.hedera.mirror.importer.MirrorProperties;

@RequiredArgsConstructor
public class StreamFileHealthIndicator implements HealthIndicator {
    public static final String STREAM_FILE_PARSE_DURATION = "hedera.mirror.parse.duration";

    private final MeterRegistry meterRegistry;
    private final String streamType;
    private final Duration streamFileStatusCheckWindow;
    private final MirrorProperties mirrorProperties;

    private final AtomicLong lastCount = new AtomicLong(0);
    private final Instant lastCheck = Instant.now();
    private final Health lastHealthStatus = Health.unknown().build(); // unknown until at least 1 stream file is parsed

    @Override
    public Health health() {
        Instant currentInstant = Instant.now();
        Search searchTimer = meterRegistry.find(STREAM_FILE_PARSE_DURATION).tag("type", streamType);
        long currentCount = searchTimer.timer().count();
        if (currentCount == 0 || currentInstant.isBefore(lastCheck.plus(streamFileStatusCheckWindow))) {
            return lastHealthStatus;
        }

        Health health = currentCount > lastCount.getAndSet(currentCount) ? Health.up().build() : Health.down().build();
        // consider demo bucket and cases where endTime has been passed
        if (health == Health.down().build() &&
                (mirrorProperties.getNetwork() == MirrorProperties.HederaNetwork.DEMO ||
                        mirrorProperties.getEndDate().isBefore(currentInstant))) {
            health = Health.up().build();
        }

        return health;
    }
}
