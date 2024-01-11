/*
 * Copyright (C) 2019-2024 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.importer.config.DateRangeCalculator.DateRangeFilter;
import static com.hedera.mirror.importer.reader.record.ProtoRecordFileReader.VERSION;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import com.hedera.mirror.common.aggregator.LogsBloomAggregator;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.config.DateRangeCalculator;
import com.hedera.mirror.importer.leader.Leader;
import com.hedera.mirror.importer.parser.AbstractStreamFileParser;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import com.hedera.mirror.importer.repository.StreamFileRepository;
import com.hedera.mirror.importer.util.Utility;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.inject.Named;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

@Named
public class RecordFileParser extends AbstractStreamFileParser<RecordFile> {

    private final ApplicationEventPublisher applicationEventPublisher;
    private final AtomicReference<RecordFile> last;
    private final RecordItemListener recordItemListener;
    private final RecordStreamFileListener recordStreamFileListener;
    private final DateRangeCalculator dateRangeCalculator;

    // Metrics
    private final Map<Integer, Timer> latencyMetrics;
    private final Map<Integer, DistributionSummary> sizeMetrics;
    private final Timer unknownLatencyMetric;
    private final DistributionSummary unknownSizeMetric;

    public RecordFileParser(
            ApplicationEventPublisher applicationEventPublisher,
            MeterRegistry meterRegistry,
            RecordParserProperties parserProperties,
            StreamFileRepository<RecordFile, Long> streamFileRepository,
            RecordItemListener recordItemListener,
            RecordStreamFileListener recordStreamFileListener,
            DateRangeCalculator dateRangeCalculator) {
        super(meterRegistry, parserProperties, streamFileRepository);
        this.applicationEventPublisher = applicationEventPublisher;
        this.last = new AtomicReference<>();
        this.recordItemListener = recordItemListener;
        this.recordStreamFileListener = recordStreamFileListener;
        this.dateRangeCalculator = dateRangeCalculator;

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
    public synchronized void parse(RecordFile recordFile) {
        super.parse(recordFile);
    }

    @Override
    protected void doParse(RecordFile recordFile) {
        DateRangeFilter dateRangeFilter = dateRangeCalculator.getFilter(parserProperties.getStreamType());

        try {
            Flux<RecordItem> recordItems = recordFile.getItems();

            if (log.isDebugEnabled() || log.isTraceEnabled()) {
                recordItems = recordItems.doOnNext(this::logItem);
            }

            recordStreamFileListener.onStart();
            var aggregator = new RecordItemAggregator();

            long count = recordItems
                    .doOnNext(aggregator::accept)
                    .filter(r -> dateRangeFilter.filter(r.getConsensusTimestamp()))
                    .doOnNext(recordItemListener::onItem)
                    .doOnNext(this::recordMetrics)
                    .count()
                    .block();

            recordFile.setCount(count);
            aggregator.update(recordFile);
            updateIndex(recordFile);
            recordStreamFileListener.onEnd(recordFile);
            applicationEventPublisher.publishEvent(new RecordFileParsedEvent(this, recordFile.getConsensusEnd()));
            last.set(recordFile);
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
                .record(recordItem.getTransaction().getSerializedSize());

        var consensusTimestamp = Instant.ofEpochSecond(0, recordItem.getConsensusTimestamp());
        latencyMetrics
                .getOrDefault(recordItem.getTransactionType(), unknownLatencyMetric)
                .record(Duration.between(consensusTimestamp, Instant.now()));
    }

    // Correct v5 block numbers once we receive a v6 block with a canonical number
    private void updateIndex(RecordFile recordFile) {
        var lastRecordFile = last.get();
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
    }

    private class RecordItemAggregator implements Consumer<RecordItem> {

        private final LogsBloomAggregator logsBloom = new LogsBloomAggregator();
        private long gasUsed = 0L;

        @Override
        public void accept(RecordItem recordItem) {
            if (!recordItem.isTopLevel()) {
                return;
            }

            var rec = recordItem.getTransactionRecord();
            var result = rec.hasContractCreateResult() ? rec.getContractCreateResult() : rec.getContractCallResult();

            if (ContractFunctionResult.getDefaultInstance().equals(result)) {
                return;
            }

            gasUsed += result.getGasUsed();
            logsBloom.aggregate(DomainUtils.toBytes(result.getBloom()));
        }

        public void update(RecordFile recordFile) {
            recordFile.setGasUsed(gasUsed);
            recordFile.setLoadEnd(Instant.now().getEpochSecond());
            recordFile.setLogsBloom(logsBloom.getBloom());
        }
    }
}
