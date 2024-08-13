/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.mirror.importer.parser.record;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.Uninterruptibles;
import com.hedera.mirror.common.domain.StreamType;
import com.hedera.mirror.importer.ImporterIntegrationTest;
import com.hedera.mirror.importer.parser.domain.RecordFileBuilder;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import com.hedera.mirror.importer.test.performance.PerformanceProperties;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.junit.jupiter.EnabledIf;

@CustomLog
@EnabledIf(expression = "${hedera.mirror.importer.test.performance.parser.enabled}", loadContext = true)
@RequiredArgsConstructor
@Tag("performance")
class RecordFileParserPerformanceTest extends ImporterIntegrationTest {

    private final PerformanceProperties performanceProperties;
    private final RecordFileParser recordFileParser;
    private final RecordFileBuilder recordFileBuilder;
    private final RecordFileRepository recordFileRepository;

    @BeforeEach
    void setup() {
        recordFileParser.clear();
    }

    @Test
    void scenarios() {
        var properties = performanceProperties.getParser();
        var previous = recordFileRepository.findLatest().orElse(null);
        var scenarios = performanceProperties.getScenarios().getOrDefault(properties.getScenario(), List.of());

        for (var scenario : scenarios) {
            if (!scenario.isEnabled()) {
                log.info("Scenario {} is disabled", scenario.getDescription());
                continue;
            }

            log.info("Executing scenario: {}", scenario);
            long interval = StreamType.RECORD.getFileCloseInterval().toMillis();
            long duration = scenario.getDuration().toMillis();
            long startTime = System.currentTimeMillis();
            long endTime = startTime;
            var stats = new SummaryStatistics();
            var stopwatch = Stopwatch.createStarted();
            var builder = recordFileBuilder.recordFile();

            scenario.getTransactions().forEach(p -> {
                int count = (int) (p.getTps() * interval / 1000);
                builder.recordItems(i -> i.count(count)
                        .entities(p.getEntities())
                        .entityAutoCreation(true)
                        .subType(p.getSubType())
                        .type(p.getType()));
            });

            while (endTime - startTime < duration) {
                var recordFile = builder.previous(previous).build();
                long startNanos = System.nanoTime();
                recordFileParser.parse(recordFile);
                stats.addValue(System.nanoTime() - startNanos);
                previous = recordFile;

                long sleep = interval - (System.currentTimeMillis() - endTime);
                if (sleep > 0) {
                    Uninterruptibles.sleepUninterruptibly(sleep, TimeUnit.MILLISECONDS);
                }
                endTime = System.currentTimeMillis();
            }

            long mean = (long) (stats.getMean() / 1_000_000.0);
            log.info(
                    "Scenario {} took {} to process {} files for a mean of {} ms per file",
                    scenario.getDescription(),
                    stopwatch,
                    stats.getN(),
                    mean);
            assertThat(Duration.ofMillis(mean))
                    .as("Scenario {} had a latency of {} ms", scenario.getDescription(), mean)
                    .isLessThanOrEqualTo(properties.getLatency());
        }
    }
}
