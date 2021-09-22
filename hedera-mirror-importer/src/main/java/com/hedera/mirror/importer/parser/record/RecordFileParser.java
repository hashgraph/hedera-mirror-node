package com.hedera.mirror.importer.parser.record;

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

import static com.hedera.mirror.importer.config.MirrorDateRangePropertiesProcessor.DateRangeFilter;

import com.google.common.collect.ImmutableMap;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import javax.inject.Named;
import org.apache.logging.log4j.Level;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import com.hedera.mirror.importer.config.MirrorDateRangePropertiesProcessor;
import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.domain.TransactionTypeEnum;
import com.hedera.mirror.importer.leader.Leader;
import com.hedera.mirror.importer.parser.AbstractStreamFileParser;
import com.hedera.mirror.importer.parser.domain.RecordItem;
import com.hedera.mirror.importer.repository.StreamFileRepository;
import com.hedera.mirror.importer.util.Utility;

@Named
public class RecordFileParser extends AbstractStreamFileParser<RecordFile> {

    private final RecordItemListener recordItemListener;
    private final RecordStreamFileListener recordStreamFileListener;
    private final MirrorDateRangePropertiesProcessor mirrorDateRangePropertiesProcessor;

    // Metrics
    private final Map<Integer, Timer> latencyMetrics;
    private final Map<Integer, DistributionSummary> sizeMetrics;
    private final Timer unknownLatencyMetric;
    private final DistributionSummary unknownSizeMetric;

    public RecordFileParser(MeterRegistry meterRegistry, RecordParserProperties parserProperties,
                            StreamFileRepository<RecordFile, Long> streamFileRepository,
                            RecordItemListener recordItemListener,
                            RecordStreamFileListener recordStreamFileListener,
                            MirrorDateRangePropertiesProcessor mirrorDateRangePropertiesProcessor) {
        super(meterRegistry, parserProperties, streamFileRepository);
        this.recordItemListener = recordItemListener;
        this.recordStreamFileListener = recordStreamFileListener;
        this.mirrorDateRangePropertiesProcessor = mirrorDateRangePropertiesProcessor;

        // build transaction latency metrics
        ImmutableMap.Builder<Integer, Timer> latencyMetricsBuilder = ImmutableMap.builder();
        ImmutableMap.Builder<Integer, DistributionSummary> sizeMetricsBuilder = ImmutableMap.builder();

        for (TransactionTypeEnum type : TransactionTypeEnum.values()) {
            Timer timer = Timer.builder("hedera.mirror.transaction.latency")
                    .description("The difference in ms between the time consensus was achieved and the mirror node " +
                            "processed the transaction")
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
        unknownLatencyMetric = latencyMetrics.get(TransactionTypeEnum.UNKNOWN.getProtoId());
        unknownSizeMetric = sizeMetrics.get(TransactionTypeEnum.UNKNOWN.getProtoId());
    }

    /**
     * Given a stream file data representing an rcd file from the service parse record items and persist changes
     *
     * @param recordFile containing information about file to be processed
     */
    @Override
    @Leader
    @Retryable(backoff = @Backoff(
            delayExpression = "#{@recordParserProperties.getRetry().getMinBackoff().toMillis()}",
            maxDelayExpression = "#{@recordParserProperties.getRetry().getMaxBackoff().toMillis()}",
            multiplierExpression = "#{@recordParserProperties.getRetry().getMultiplier()}"),
            maxAttemptsExpression = "#{@recordParserProperties.getRetry().getMaxAttempts()}")
    @Transactional(timeoutString = "#{@recordParserProperties.getTransactionTimeout().toSeconds()}")
    public void parse(RecordFile recordFile) {
        super.parse(recordFile);
    }

    @Override
    protected void doParse(RecordFile recordFile) {
        DateRangeFilter dateRangeFilter = mirrorDateRangePropertiesProcessor
                .getDateRangeFilter(parserProperties.getStreamType());

        try {
            Flux<RecordItem> recordItems = recordFile.getItems();

            if (log.getLevel().isInRange(Level.TRACE, Level.DEBUG)) {
                recordItems = recordItems.doOnNext(this::logItem);
            }

            recordStreamFileListener.onStart();
            long count = recordItems.filter(r -> dateRangeFilter.filter(r.getConsensusTimestamp()))
                    .filter(r -> dateRangeFilter.filter(r.getConsensusTimestamp()))
                    .doOnNext(recordItemListener::onItem)
                    .doOnNext(this::recordMetrics)
                    .count()
                    .block();

            recordFile.setCount(count);
            recordFile.setLoadEnd(Instant.now().getEpochSecond());
            recordStreamFileListener.onEnd(recordFile);
        } catch (Exception ex) {
            recordStreamFileListener.onError();
            throw ex;
        }
    }

    private void logItem(RecordItem recordItem) {
        if (log.isTraceEnabled()) {
            log.trace("Transaction = {}, Record = {}",
                    Utility.printProtoMessage(recordItem.getTransaction()),
                    Utility.printProtoMessage(recordItem.getRecord()));
        } else if (log.isDebugEnabled()) {
            log.debug("Storing transaction with consensus timestamp {}", recordItem.getConsensusTimestamp());
        }
    }

    private void recordMetrics(RecordItem recordItem) {
        sizeMetrics.getOrDefault(recordItem.getTransactionType(), unknownSizeMetric)
                .record(recordItem.getTransactionBytes().length);

        Instant consensusTimestamp = Utility.convertToInstant(recordItem.getRecord().getConsensusTimestamp());
        latencyMetrics.getOrDefault(recordItem.getTransactionType(), unknownLatencyMetric)
                .record(Duration.between(consensusTimestamp, Instant.now()));
    }
}
