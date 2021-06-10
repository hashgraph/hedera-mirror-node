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

import static com.hedera.mirror.importer.config.MessagingConfiguration.CHANNEL_RECORD;
import static com.hedera.mirror.importer.config.MirrorDateRangePropertiesProcessor.DateRangeFilter;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.log4j.Log4j2;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.Poller;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.transaction.annotation.Transactional;

import com.hedera.mirror.importer.config.MirrorDateRangePropertiesProcessor;
import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.domain.TransactionTypeEnum;
import com.hedera.mirror.importer.parser.AbstractStreamFileParser;
import com.hedera.mirror.importer.parser.domain.RecordItem;
import com.hedera.mirror.importer.util.Utility;

@Log4j2
@MessageEndpoint
@ConditionalOnRecordParser
public class RecordFileParser extends AbstractStreamFileParser<RecordFile> {

    private final RecordItemListener recordItemListener;
    private final RecordStreamFileListener recordStreamFileListener;
    private final MirrorDateRangePropertiesProcessor mirrorDateRangePropertiesProcessor;

    // Metrics
    private final Map<Boolean, Timer> parseDurationMetrics;
    private final Map<Integer, Timer> latencyMetrics;
    private final Map<Integer, DistributionSummary> sizeMetrics;
    private final Timer unknownLatencyMetric;
    private final DistributionSummary unknownSizeMetric;
    private final Timer parseLatencyMetric;

    public RecordFileParser(RecordParserProperties parserProperties, MeterRegistry meterRegistry,
                            RecordItemListener recordItemListener,
                            RecordStreamFileListener recordStreamFileListener,
                            MirrorDateRangePropertiesProcessor mirrorDateRangePropertiesProcessor) {
        super(parserProperties);
        this.recordItemListener = recordItemListener;
        this.recordStreamFileListener = recordStreamFileListener;
        this.mirrorDateRangePropertiesProcessor = mirrorDateRangePropertiesProcessor;

        // build parse metrics
        ImmutableMap.Builder<Boolean, Timer> parseDurationMetricsBuilder = ImmutableMap.builder();
        Timer.Builder parseDurationTimerBuilder = Timer.builder(STREAM_PARSE_DURATION_METRIC_NAME)
                .description("The duration in seconds it took to parse the file and store it in the database")
                .tag("type", parserProperties.getStreamType().toString());

        parseDurationMetricsBuilder.put(true, parseDurationTimerBuilder
                .tag("success", "true")
                .register(meterRegistry));

        parseDurationMetricsBuilder.put(false, parseDurationTimerBuilder
                .tag("success", "false")
                .register(meterRegistry));
        parseDurationMetrics = parseDurationMetricsBuilder.build();

        parseLatencyMetric = Timer.builder("hedera.mirror.parse.latency")
                .description("The difference in ms between the consensus time of the last transaction in the file " +
                        "and the time at which the file was processed successfully")
                .tag("type", parserProperties.getStreamType().toString())
                .register(meterRegistry);

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
    @Retryable(backoff = @Backoff(
            delayExpression = "#{@recordParserProperties.getRetry().getMinBackoff().toMillis()}",
            maxDelayExpression = "#{@recordParserProperties.getRetry().getMaxBackoff().toMillis()}",
            multiplierExpression = "#{@recordParserProperties.getRetry().getMultiplier()}"),
            maxAttemptsExpression = "#{@recordParserProperties.getRetry().getMaxAttempts()}")
    @ServiceActivator(inputChannel = CHANNEL_RECORD,
            poller = @Poller(fixedDelay = "${hedera.mirror.importer.parser.record.frequency:100}")
    )
    @Transactional
    public void parse(RecordFile recordFile) {
        super.parse(recordFile);
    }

    @Override
    protected void parseStreamFile(RecordFile recordFile) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        DateRangeFilter dateRangeFilter = mirrorDateRangePropertiesProcessor
                .getDateRangeFilter(parserProperties.getStreamType());
        AtomicInteger counter = new AtomicInteger(0);
        boolean success = false;

        try {
            recordStreamFileListener.onStart();
            recordFile.getItems().forEach(recordItem -> {
                if (processRecordItem(recordItem, dateRangeFilter)) {
                    counter.incrementAndGet();
                }
            });

            Instant loadEnd = Instant.now();
            recordFile.setLoadEnd(loadEnd.getEpochSecond());
            recordStreamFileListener.onEnd(recordFile);

            Instant consensusEnd = Instant.ofEpochSecond(0L, recordFile.getConsensusEnd());
            parseLatencyMetric.record(Duration.between(consensusEnd, loadEnd));
            success = true;
        } catch (Exception ex) {
            log.error("Error parsing file {}", recordFile.getName(), ex);
            recordStreamFileListener.onError();
            throw ex;
        } finally {
            log.info("Finished parsing {} transactions from record file {} in {}. Success: {}",
                    counter, recordFile.getName(), stopwatch, success);
            parseDurationMetrics.get(success).record(stopwatch.elapsed());
        }
    }

    private boolean processRecordItem(RecordItem recordItem, DateRangeFilter dateRangeFilter) {
        if (log.isTraceEnabled()) {
            log.trace("Transaction = {}, Record = {}",
                    Utility.printProtoMessage(recordItem.getTransaction()),
                    Utility.printProtoMessage(recordItem.getRecord()));
        } else if (log.isDebugEnabled()) {
            log.debug("Storing transaction with consensus timestamp {}", recordItem.getConsensusTimestamp());
        }

        if (dateRangeFilter != null && !dateRangeFilter.filter(recordItem.getConsensusTimestamp())) {
            return false;
        }

        recordItemListener.onItem(recordItem);

        sizeMetrics.getOrDefault(recordItem.getTransactionType(), unknownSizeMetric)
                .record(recordItem.getTransactionBytes().length);

        Instant consensusTimestamp = Utility.convertToInstant(recordItem.getRecord().getConsensusTimestamp());
        latencyMetrics.getOrDefault(recordItem.getTransactionType(), unknownLatencyMetric)
                .record(Duration.between(consensusTimestamp, Instant.now()));
        return true;
    }
}
