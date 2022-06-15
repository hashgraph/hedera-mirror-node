package com.hedera.mirror.importer.retention;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionOperations;

import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import com.hedera.mirror.importer.repository.RetentionRepository;

@Log4j2
@Named
@RequiredArgsConstructor
public class RetentionJob {

    private final RecordFileRepository recordFileRepository;
    private final RetentionProperties retentionProperties;
    private final Collection<RetentionRepository> retentionRepositories;
    private final TransactionOperations transactionOperations;

    @Scheduled(fixedDelayString = "${hedera.mirror.importer.retention.frequency:1}", timeUnit = TimeUnit.DAYS)
    public synchronized void prune() {
        if (!retentionProperties.isEnabled()) {
            return;
        }

        Optional<RecordFile> latest = recordFileRepository.findLatest();
        if (latest.isEmpty()) {
            log.warn("Skipping since database is empty");
            return;
        }

        long batchPeriod = retentionProperties.getBatchPeriod().toNanos();
        long retentionPeriod = retentionProperties.getPeriod().toNanos();
        var counters = new TreeMap<String, Long>();
        var stopwatch = Stopwatch.createStarted();

        try {
            var recordFile = recordFileRepository.findEarliest();
            if (recordFile.isEmpty()) {
                return; // Shouldn't occur since we've found at least one row above
            }

            long count = 0L;
            long minTimestamp = recordFile.map(RecordFile::getConsensusStart).orElse(0L);
            long maxTimestamp = latest.map(RecordFile::getConsensusEnd).get() - retentionPeriod;
            log.info("Removing data from {} to {}", toInstant(minTimestamp), toInstant(maxTimestamp));

            while (shouldDelete(recordFile, maxTimestamp)) {
                long startTimestamp = recordFile.map(RecordFile::getConsensusStart).orElse(0L);
                var nextRecordFile = recordFileRepository.findNext(startTimestamp + batchPeriod);
                long endTimestamp = nextRecordFile.map(RecordFile::getConsensusEnd)
                        .map(t -> Math.min(t, maxTimestamp))
                        .orElse(maxTimestamp);

                prune(counters, endTimestamp);

                recordFile = nextRecordFile;
                long countPrevious = count;
                count = counters.values().stream().reduce(0L, Long::sum);
                long elapsed = stopwatch.elapsed(TimeUnit.SECONDS);
                long rate = elapsed > 0 ? count / elapsed : 0L;
                log.info("Pruned {} entries in {} at {}/s", count - countPrevious, stopwatch, rate);
            }

            log.info("Finished pruning tables in {}: {}", stopwatch, counters);
        } catch (Exception e) {
            log.error("Error pruning tables in {}: {}", stopwatch, counters, e);
        }
    }

    private boolean shouldDelete(Optional<RecordFile> recordFile, long maxTimestamp) {
        return recordFile.isPresent() && recordFile.get().getConsensusEnd() < maxTimestamp;
    }

    private void prune(Map<String, Long> counters, long endTimestamp) {
        transactionOperations.executeWithoutResult(t ->
                retentionRepositories.forEach(repository -> {
                    long count = repository.prune(endTimestamp);
                    String table = getTableName(repository);
                    counters.merge(table, count, Long::sum);
                })
        );
    }

    private String getTableName(RetentionRepository repository) {
        Class<?> targetClass = repository.getClass().getInterfaces()[0];
        String className = ClassUtils.getSimpleName(targetClass);
        return StringUtils.removeEnd(className, "Repository");
    }

    private Instant toInstant(long nanos) {
        return Instant.ofEpochSecond(0L, nanos);
    }
}
