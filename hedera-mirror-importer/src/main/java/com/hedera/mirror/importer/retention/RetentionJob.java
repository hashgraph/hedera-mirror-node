package com.hedera.mirror.importer.retention;

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

import com.google.common.base.Stopwatch;
import java.time.Instant;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import javax.inject.Named;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionOperations;

import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import com.hedera.mirror.importer.repository.RetentionRepository;
import com.hedera.mirror.importer.util.Utility;

@Log4j2
@Named
@RequiredArgsConstructor
public class RetentionJob {

    private final RecordFileRepository recordFileRepository;
    private final RetentionProperties retentionProperties;
    private final Collection<RetentionRepository> retentionRepositories;
    private final TransactionOperations transactionOperations;

    @Scheduled(fixedDelayString = "#{@retentionProperties.getFrequency().toMillis()}", initialDelay = 120_000)
    public synchronized void prune() {
        if (!retentionProperties.isEnabled()) {
            log.info("Retention is disabled");
            return;
        }

        var retentionPeriod = retentionProperties.getPeriod();
        var latest = recordFileRepository.findLatestWithOffset(retentionPeriod.toNanos());
        if (latest.isEmpty()) {
            log.warn("Skipping since there is no data {} older than the latest data in database", retentionPeriod);
            return;
        }

        var maxTimestamp = latest.get().getConsensusEnd();
        var iterator = new RecordFileIterator(latest.get());
        log.info("Using retention period {} to prune entries on or before {}", retentionPeriod,
                toInstant(maxTimestamp));

        try {
            while (iterator.hasNext()) {
                prune(iterator);
            }

            log.info("Finished pruning tables in {}: {}", iterator.getStopwatch(), iterator.getCounters());
        } catch (Exception e) {
            log.error("Error pruning tables in {}: {}", iterator.getStopwatch(), iterator.getCounters(), e);
        }
    }

    private void prune(RecordFileIterator iterator) {
        var counters = iterator.getCounters();
        long countBefore = counters.values().stream().reduce(0L, Long::sum);
        var stopwatch = iterator.getStopwatch();
        var next = iterator.next();
        long endTimestamp = next.getConsensusEnd();

        transactionOperations.executeWithoutResult(t ->
                retentionRepositories.forEach(repository -> {
                    String table = getTableName(repository);

                    if (retentionProperties.shouldPrune(table)) {
                        long count = repository.prune(endTimestamp);
                        counters.merge(table, count, Long::sum);
                    }
                })
        );

        long countAfter = counters.values().stream().reduce(0L, Long::sum);
        long count = countAfter - countBefore;
        long elapsed = stopwatch.elapsed(TimeUnit.SECONDS);
        long rate = elapsed > 0 ? countAfter / elapsed : 0L;
        log.info("Pruned {} entries on or before {} in {} at {}/s", count, toInstant(endTimestamp), stopwatch, rate);
    }

    private String getTableName(RetentionRepository repository) {
        Class<?> targetClass = repository.getClass().getInterfaces()[0];
        String className = ClassUtils.getSimpleName(targetClass);
        return Utility.toSnakeCase(StringUtils.removeEnd(className, "Repository"));
    }

    private Instant toInstant(long nanos) {
        return Instant.ofEpochSecond(0L, nanos);
    }

    @Data
    private class RecordFileIterator implements Iterator<RecordFile> {

        private final Map<String, Long> counters = new TreeMap<>();
        private final RecordFile max;
        private final Stopwatch stopwatch = Stopwatch.createStarted();
        private RecordFile current;

        public boolean hasNext() {
            // Initialize with the earliest/minimum record file. This can incur an extra prune at the beginning but
            // simplfies logic and is necessary in case there is only one record file in the database.
            if (current == null) {
                var next = recordFileRepository.findNextBetween(0, max.getConsensusEnd());
                if (next.isEmpty()) {
                    return false;
                }

                current = next.get();
                return true;
            }

            // We pruned max in the last iteration, so skip it now
            if (current == max) {
                current = null;
                return false;
            }

            long batchPeriod = retentionProperties.getBatchPeriod().toNanos();
            long endTimestamp = current.getConsensusEnd() + batchPeriod;

            // Ignore batchPeriod if it would put us past the max and just use max instead
            if (endTimestamp >= max.getConsensusEnd()) {
                current = max;
                return true;
            }

            // Next record file is in between min and max
            var next = recordFileRepository.findNextBetween(endTimestamp, max.getConsensusEnd());
            if (next.isEmpty()) {
                current = null;
                return false;
            }

            current = next.get();
            return true;
        }

        @Override
        public RecordFile next() {
            if (current == null) {
                throw new NoSuchElementException("No more record files");
            }
            return current;
        }
    }
}
