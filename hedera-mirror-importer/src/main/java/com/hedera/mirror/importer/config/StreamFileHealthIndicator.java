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

    private final MeterRegistry meterRegistry;
    private final String streamType;
    private final Duration statusCheckDurationWindow;

    private final AtomicLong lastCount = new AtomicLong(0);
    private final Instant lastCheck = Instant.now();
    private final Health lastHealthStatus = Health.unknown().build(); // unknown until at least 1 stream file is parsed

    @Override
    public Health health() {
        Search searchTimer = meterRegistry.find(STREAM_FILE_PARSE_DURATION).tag("type", streamType);
        long currentCount = searchTimer.timer().count();
        if (currentCount == 0 || Instant.now().isBefore(lastCheck.plus(statusCheckDurationWindow))) {
            return lastHealthStatus;
        }

//        Health.Builder currentHealth = currentCount > lastCount.getAndSet(currentCount) ? Health.up() : Health.down();
//        ScheduledExecutorService parsingStatusCheckScheduler = Executors.newSingleThreadScheduledExecutor();
//        parsingStatusCheckScheduler = Executors.newSingleThreadScheduledExecutor();
//        parsingStatusCheckScheduler.scheduleAtFixedRate(() -> {
//            result.printProgress();
//        }, 0, messageListener.getStatusPrintIntervalMinutes(), TimeUnit.MINUTES);

//        return currentHealth.build();
        return currentCount > lastCount.getAndSet(currentCount) ? Health.up().build() : Health.down().build();
    }
}
