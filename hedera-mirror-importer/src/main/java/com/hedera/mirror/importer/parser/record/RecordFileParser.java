/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.importer.config.MirrorDateRangePropertiesProcessor.DateRangeFilter;
import static com.hedera.mirror.importer.reader.record.ProtoRecordFileReader.VERSION;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.config.MirrorDateRangePropertiesProcessor;
import com.hedera.mirror.importer.leader.Leader;
import com.hedera.mirror.importer.parser.AbstractStreamFileParser;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import com.hedera.mirror.importer.repository.StreamFileRepository;
import com.hedera.mirror.importer.util.Utility;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.inject.Named;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.Level;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

@Named
public class RecordFileParser extends AbstractStreamFileParser<RecordFile> {

    private final AtomicReference<RecordFile> last;
    private final RecordItemListener recordItemListener;
    private final RecordStreamFileListener recordStreamFileListener;
    private final MirrorDateRangePropertiesProcessor mirrorDateRangePropertiesProcessor;

    // Metrics
    private final Map<Integer, Timer> latencyMetrics;
    private final Map<Integer, DistributionSummary> sizeMetrics;
    private final Timer unknownLatencyMetric;
    private final DistributionSummary unknownSizeMetric;

    public RecordFileParser(
            MeterRegistry meterRegistry,
            RecordParserProperties parserProperties,
            StreamFileRepository<RecordFile, Long> streamFileRepository,
            RecordItemListener recordItemListener,
            RecordStreamFileListener recordStreamFileListener,
            MirrorDateRangePropertiesProcessor mirrorDateRangePropertiesProcessor) {
        super(meterRegistry, parserProperties, streamFileRepository);
        this.last = new AtomicReference<>();
        this.recordItemListener = recordItemListener;
        this.recordStreamFileListener = recordStreamFileListener;
        this.mirrorDateRangePropertiesProcessor = mirrorDateRangePropertiesProcessor;

        // build transaction latency metrics
        ImmutableMap.Builder<Integer, Timer> latencyMetricsBuilder = ImmutableMap.builder();
        ImmutableMap.Builder<Integer, DistributionSummary> sizeMetricsBuilder = ImmutableMap.builder();

        for (TransactionType type : TransactionType.values()) {
            Timer timer = Timer.builder("hedera.mirror.transaction.latency")
                    .description("The difference in ms between the time consensus was achieved and the mirror node "
                            + "processed the transaction")
                    .tag("type", type.toString())
                    .register(meterRegistry);
            latencyMetricsBuilder.put(type.getProtoId(), timer);

            DistributionSummary distributionSummary = DistributionSummary.builder("hedera.mirror.transaction.size")
                    .description("The size of the transaction in bytes")
                    .baseUnit("bytes")
                    .tag("type", type.toString())
                    .register(meterRegistry);
            sizeMetricsBuilder.put(type.getProtoId(), distributionSummary);
        }

        latencyMetrics = latencyMetricsBuilder.build();
        sizeMetrics = sizeMetricsBuilder.build();
        unknownLatencyMetric = latencyMetrics.get(TransactionType.UNKNOWN.getProtoId());
        unknownSizeMetric = sizeMetrics.get(TransactionType.UNKNOWN.getProtoId());
    }

    /**
     * Given a stream file data representing an rcd file from the service parse record items and persist changes
     *
     * @param recordFile containing information about file to be processed
     */
    @Override
    @Leader
    @Retryable(
            backoff =
                    @Backoff(
                            delayExpression = "#{@recordParserProperties.getRetry().getMinBackoff().toMillis()}",
                            maxDelayExpression = "#{@recordParserProperties.getRetry().getMaxBackoff().toMillis()}",
                            multiplierExpression = "#{@recordParserProperties.getRetry().getMultiplier()}"),
            retryFor = Throwable.class,
            noRetryFor = OutOfMemoryError.class,
            maxAttemptsExpression = "#{@recordParserProperties.getRetry().getMaxAttempts()}")
    @Transactional(timeoutString = "#{@recordParserProperties.getTransactionTimeout().toSeconds()}")
    public void parse(RecordFile recordFile) {
        super.parse(recordFile);
    }

    @Override
    protected void doParse(RecordFile recordFile) {
        DateRangeFilter dateRangeFilter =
                mirrorDateRangePropertiesProcessor.getDateRangeFilter(parserProperties.getStreamType());

        try {
            Flux<RecordItem> recordItems = recordFile.getItems();

            if (log.getLevel().isInRange(Level.DEBUG, Level.TRACE)) {
                recordItems = recordItems.doOnNext(this::logItem);
            }

            recordStreamFileListener.onStart();

            long count = recordItems
                    .doOnNext(recordFile::processItem)
                    .filter(r -> dateRangeFilter.filter(r.getConsensusTimestamp()))
                    .doOnNext(recordItemListener::onItem)
                    .doOnNext(this::recordMetrics)
                    .count()
                    .block();

            recordFile.finishLoad(count);
            updateIndex(recordFile);
            recordStreamFileListener.onEnd(recordFile);
        } catch (Exception ex) {
            recordStreamFileListener.onError();
            throw ex;
        }
    }

    private void logItem(RecordItem recordItem) {
        if (log.isTraceEnabled()) {
            log.trace(
                    "Transaction = {}, Record = {}",
                    Utility.printProtoMessage(recordItem.getTransaction()),
                    Utility.printProtoMessage(recordItem.getTransactionRecord()));
        } else if (log.isDebugEnabled()) {
            log.debug("Parsing transaction with consensus timestamp {}", recordItem.getConsensusTimestamp());
        }
    }

    private void recordMetrics(RecordItem recordItem) {
        sizeMetrics
                .getOrDefault(recordItem.getTransactionType(), unknownSizeMetric)
                .record(recordItem.getTransactionBytes().length);

        var consensusTimestamp = Instant.ofEpochSecond(0, recordItem.getConsensusTimestamp());
        latencyMetrics
                .getOrDefault(recordItem.getTransactionType(), unknownLatencyMetric)
                .record(Duration.between(consensusTimestamp, Instant.now()));
    }

    // Correct v5 block numbers once we receive a v6 block with a canonical number
    private void updateIndex(RecordFile recordFile) {
        var lastInMemory = last.get();
        var lastRecordFile = lastInMemory;
        var recordFileRepository = (RecordFileRepository) streamFileRepository;

        if (lastRecordFile == null) {
            lastRecordFile = recordFileRepository.findLatest().orElse(null);
        }

        if (lastRecordFile != null && lastRecordFile.getVersion() < VERSION && recordFile.getVersion() >= VERSION) {
            long offset = recordFile.getIndex() - lastRecordFile.getIndex() - 1;

            if (offset != 0) {
                var stopwatch = Stopwatch.createStarted();
                int count = recordFileRepository.updateIndex(offset);
                log.info("Updated {} blocks with offset {} in {}", count, offset, stopwatch);
            }
        }

        last.compareAndSet(lastInMemory, recordFile);
    }
}
