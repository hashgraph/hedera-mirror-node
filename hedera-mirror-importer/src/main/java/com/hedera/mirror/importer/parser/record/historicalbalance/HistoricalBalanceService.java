/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.parser.record.historicalbalance;

import static com.hedera.mirror.common.domain.balance.AccountBalanceFile.INVALID_NODE_ID;
import static com.hedera.mirror.importer.parser.AbstractStreamFileParser.STREAM_PARSE_DURATION_METRIC_NAME;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.StreamType;
import com.hedera.mirror.common.domain.balance.AccountBalanceFile;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.importer.db.TimePartition;
import com.hedera.mirror.importer.db.TimePartitionService;
import com.hedera.mirror.importer.domain.StreamFilename;
import com.hedera.mirror.importer.domain.StreamFilename.FileType;
import com.hedera.mirror.importer.exception.ParserException;
import com.hedera.mirror.importer.parser.record.RecordFileParsedEvent;
import com.hedera.mirror.importer.parser.record.RecordFileParser;
import com.hedera.mirror.importer.repository.AccountBalanceFileRepository;
import com.hedera.mirror.importer.repository.AccountBalanceRepository;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import com.hedera.mirror.importer.repository.TokenBalanceRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.inject.Named;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.CustomLog;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

@ConditionalOnProperty(
        name = "enabled",
        matchIfMissing = true,
        prefix = "hedera.mirror.importer.parser.record.historical-balance")
@CustomLog
@Named
public class HistoricalBalanceService {

    private static final String ACCOUNT_BALANCE_TABLE_NAME = "account_balance";
    private static final String TOKEN_BALANCE_TABLE_NAME = "token_balance";
    private static final long LOWER_RANGE = 0L;
    private static final long UPPER_RANGE = 9223372036854775807L;

    private final AccountBalanceFileRepository accountBalanceFileRepository;
    private final AccountBalanceRepository accountBalanceRepository;
    private final HistoricalBalanceProperties properties;
    private final RecordFileRepository recordFileRepository;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final TimePartitionService timePartitionService;
    private final TokenBalanceRepository tokenBalanceRepository;
    private final TransactionTemplate transactionTemplate;

    // metrics
    private final Timer generateDurationMetricFailure;
    private final Timer generateDurationMetricSuccess;

    public HistoricalBalanceService(
            AccountBalanceFileRepository accountBalanceFileRepository,
            AccountBalanceRepository accountBalanceRepository,
            MeterRegistry meterRegistry,
            PlatformTransactionManager platformTransactionManager,
            HistoricalBalanceProperties properties,
            RecordFileRepository recordFileRepository,
            TimePartitionService timePartitionService,
            TokenBalanceRepository tokenBalanceRepository) {
        this.accountBalanceFileRepository = accountBalanceFileRepository;
        this.accountBalanceRepository = accountBalanceRepository;
        this.properties = properties;
        this.recordFileRepository = recordFileRepository;
        this.timePartitionService = timePartitionService;
        this.tokenBalanceRepository = tokenBalanceRepository;

        // Set repeatable read isolation level and transaction timeout
        this.transactionTemplate = new TransactionTemplate(platformTransactionManager);
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate.setTimeout(
                (int) properties.getTransactionTimeout().toSeconds());

        // metrics
        var timer = Timer.builder(STREAM_PARSE_DURATION_METRIC_NAME).tag("type", StreamType.BALANCE.toString());
        generateDurationMetricFailure = timer.tag("success", "false").register(meterRegistry);
        generateDurationMetricSuccess = timer.tag("success", "true").register(meterRegistry);
    }

    /**
     * Listens on {@link RecordFileParsedEvent} and generate historical balance at configured frequency.
     *
     * @param event The record file parsed event published by {@link RecordFileParser}
     */
    @Async
    @TransactionalEventListener
    public void onRecordFileParsed(RecordFileParsedEvent event) {
        if (running.compareAndExchange(false, true)) {
            return;
        }

        var stopwatch = Stopwatch.createStarted();
        Timer timer = null;

        try {
            long consensusEnd = event.getConsensusEnd();
            if (!shouldGenerate(consensusEnd)) {
                return;
            }

            log.info("Generating historical balances after processing record file with consensusEnd {}", consensusEnd);
            transactionTemplate.executeWithoutResult(t -> {
                long loadStart = Instant.now().getEpochSecond();
                long timestamp = recordFileRepository
                        .findLatest()
                        .map(RecordFile::getConsensusEnd)
                        // This should never happen since the function is triggered after a record file is parsed
                        .orElseThrow(() -> new ParserException("Record file table is empty"));

                int accountBalancesCount = balanceSnapshot(timestamp);
                int tokenBalancesCount = tokenBalanceSnapshot(timestamp);

                long loadEnd = Instant.now().getEpochSecond();
                String filename = StreamFilename.getFilename(
                        StreamType.BALANCE, FileType.DATA, Instant.ofEpochSecond(0, timestamp));
                var accountBalanceFile = AccountBalanceFile.builder()
                        .consensusTimestamp(timestamp)
                        .count((long) accountBalancesCount)
                        .loadStart(loadStart)
                        .loadEnd(loadEnd)
                        .name(filename)
                        .nodeId(INVALID_NODE_ID)
                        .synthetic(true)
                        .build();
                accountBalanceFileRepository.save(accountBalanceFile);

                log.info(
                        "Generated historical account balance file {} with {} account balances and {} token balances in {}",
                        filename,
                        accountBalancesCount,
                        tokenBalancesCount,
                        stopwatch);
            });

            timer = generateDurationMetricSuccess;
        } catch (Exception e) {
            log.error("Failed to generate historical balances in {}", stopwatch, e);
            timer = generateDurationMetricFailure;
        } finally {
            running.set(false);

            if (timer != null) {
                timer.record(stopwatch.elapsed());
            }
        }
    }

    private boolean shouldGenerate(long consensusEnd) {
        return accountBalanceFileRepository
                .findLatest()
                .map(AccountBalanceFile::getConsensusTimestamp)
                .or(() -> recordFileRepository
                        .findFirst()
                        .map(RecordFile::getConsensusEnd)
                        .map(timestamp ->
                                timestamp + properties.getInitialDelay().toNanos()))
                .filter(lastTimestamp -> consensusEnd - lastTimestamp
                        >= properties.getMinFrequency().toNanos())
                .isPresent();
    }

    private int balanceSnapshot(long timestamp) {
        if (properties.isDedupe()) {
            var range = getPartitionRange(timestamp, ACCOUNT_BALANCE_TABLE_NAME);
            return accountBalanceRepository.updateBalanceSnapshot(
                    range.lowerEndpoint(), range.upperEndpoint(), timestamp);
        }
        return accountBalanceRepository.balanceSnapshot(timestamp);
    }

    private int tokenBalanceSnapshot(long timestamp) {
        if (!properties.isTokenBalances()) {
            return 0;
        }

        if (properties.isDedupe()) {
            var range = getPartitionRange(timestamp, TOKEN_BALANCE_TABLE_NAME);
            return tokenBalanceRepository.updateBalanceSnapshot(
                    range.lowerEndpoint(), range.upperEndpoint(), timestamp);
        }
        return tokenBalanceRepository.balanceSnapshot(timestamp);
    }

    private Range<Long> getPartitionRange(long timestamp, String tableName) {
        var partitions = timePartitionService.getTimePartitions(tableName);
        return partitions.stream()
                .filter(p -> p.getTimestampRange().contains(timestamp))
                .findFirst()
                .map(TimePartition::getTimestampRange)
                .orElse(Range.closedOpen(LOWER_RANGE, UPPER_RANGE));
    }
}
