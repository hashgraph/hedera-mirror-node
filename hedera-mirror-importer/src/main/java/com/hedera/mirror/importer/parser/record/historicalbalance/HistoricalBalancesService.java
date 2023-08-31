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

import com.google.common.base.Stopwatch;
import com.hedera.mirror.common.domain.StreamType;
import com.hedera.mirror.common.domain.balance.AccountBalanceFile;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.importer.domain.StreamFilename;
import com.hedera.mirror.importer.domain.StreamFilename.FileType;
import com.hedera.mirror.importer.exception.ParserException;
import com.hedera.mirror.importer.parser.balance.AccountBalanceFileParsedEvent;
import com.hedera.mirror.importer.parser.balance.AccountBalanceFileParser;
import com.hedera.mirror.importer.parser.record.RecordFileParsedEvent;
import com.hedera.mirror.importer.parser.record.RecordFileParser;
import com.hedera.mirror.importer.repository.AccountBalanceFileRepository;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import jakarta.inject.Named;
import java.time.Instant;
import lombok.CustomLog;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.BufferOverflowStrategy;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Schedulers;

@ConditionalOnProperty(
        prefix = "hedera.mirror.importer.parser.record.historical-balances",
        name = "enabled",
        matchIfMissing = true)
@CustomLog
@Named
public class HistoricalBalancesService {

    private static final String GENERATE_ACCOUNT_BALANCES_SQL =
            """
            insert into account_balance (account_id, balance, consensus_timestamp)
            select id, balance, ?
            from entity
            where deleted is not true and balance is not null and type in ('ACCOUNT', 'CONTRACT')
            order by id
            """;
    private static final String GENERATE_TOKEN_BALANCES_SQL =
            """
            insert into token_balance (account_id, balance, consensus_timestamp, token_id)
            select account_id, balance, ?, token_id
            from token_account
            where associated is true
            order by account_id, token_id
            """;
    private static final long NOT_SET = 0;

    private final AccountBalanceFileRepository accountBalanceFileRepository;
    private final JdbcTemplate jdbcTemplate;
    private final HistoricalBalancesProperties properties;
    private final RecordFileRepository recordFileRepository;
    private final TransactionTemplate transactionTemplate;

    private long count = 0;
    private long lastTimestamp = NOT_SET;
    private FluxSink<ParsedStreamFile> parsedStreamFileSink;

    public HistoricalBalancesService(
            AccountBalanceFileRepository accountBalanceFileRepository,
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager platformTransactionManager,
            HistoricalBalancesProperties properties,
            RecordFileRepository recordFileRepository) {
        this.accountBalanceFileRepository = accountBalanceFileRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.recordFileRepository = recordFileRepository;

        // Set repeatable read isolation level and transaction timeout
        this.transactionTemplate = new TransactionTemplate(platformTransactionManager);
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        this.transactionTemplate.setTimeout(
                (int) properties.getTransactionTimeout().toSeconds());

        // A backpressure buffer to hold parsed record files for the period twice of the transaction timeout
        int bufferSize =
                (int) (properties.getTransactionTimeout().multipliedBy(2).toSeconds()
                        / StreamType.RECORD.getFileCloseInterval().toSeconds());
        Flux.<ParsedStreamFile>create(sink -> this.parsedStreamFileSink = sink)
                // Ensure generate runs in the same thread
                .publishOn(Schedulers.newSingle("Historical balances service"))
                // Drop the oldest to minimize the delay of generating next historical balances
                .onBackpressureBuffer(bufferSize, BufferOverflowStrategy.DROP_OLDEST)
                .doOnNext(this::generate)
                .doOnError(t -> log.error("Error processing ParsedStreamFile", t))
                .subscribe();
    }

    /**
     * Listens on {@link AccountBalanceFileParsedEvent} and emits a {@link ParsedStreamFile} element.
     *
     * @param event The account balance file parsed event published by {@link AccountBalanceFileParser}
     */
    @Async
    @TransactionalEventListener
    public void onAccountBalanceFileParsed(AccountBalanceFileParsedEvent event) {
        parsedStreamFileSink.next(new ParsedStreamFile(event.getConsensusTimestamp(), StreamType.BALANCE));
    }

    /**
     * Listens on {@link RecordFileParsedEvent} and emits a {@link ParsedStreamFile} element to trigger historical
     * balances generation.
     *
     * @param event The record file parsed event published by {@link RecordFileParser}
     */
    @Async
    @TransactionalEventListener
    public void onRecordFileParsed(RecordFileParsedEvent event) {
        parsedStreamFileSink.next(new ParsedStreamFile(event.getConsensusEnd(), StreamType.RECORD));
    }

    private void generate(ParsedStreamFile parsedStreamFile) {
        if (parsedStreamFile.type == StreamType.BALANCE) {
            if (count == 0) {
                // Only initialize lastTimestamp to the parsed account balance file's consensus timestamp if no
                // historical balances info has generated. Note if there are account balance files in db before importer
                // starts, account balances downloader won't run thus no AccountBalanceFileParsedEvent will get fired
                lastTimestamp = parsedStreamFile.consensusTimestamp;
                log.info("Initialize lastTimestamp to {} from the first parsed account balance file", lastTimestamp);
            }

            return;
        }

        initializeLastTimestamp();

        long consensusEnd = parsedStreamFile.consensusTimestamp;
        if (consensusEnd - lastTimestamp < properties.getMinFrequency().toNanos()) {
            return;
        }

        var stopwatch = Stopwatch.createStarted();
        try {
            log.info("Generating historical balances after processing record file with consensusEnd {}", consensusEnd);

            transactionTemplate.executeWithoutResult(t -> {
                long loadStart = Instant.now().getEpochSecond();
                long timestamp = recordFileRepository
                        .findLatest()
                        .map(RecordFile::getConsensusEnd)
                        // This should never happen since the function is triggered after a record file is parsed
                        .orElseThrow(() -> new ParserException("Record file table is empty"));
                int accountBalancesCount = jdbcTemplate.update(GENERATE_ACCOUNT_BALANCES_SQL, timestamp);
                int tokenBalancesCount = 0;
                if (properties.isTokenBalances()) {
                    tokenBalancesCount = jdbcTemplate.update(GENERATE_TOKEN_BALANCES_SQL, timestamp);
                }

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

                count++;
                lastTimestamp = timestamp;
                log.info(
                        "Generated historical account balance file {} with {} account balances and {} token balances for timestamp {} in {}",
                        filename,
                        accountBalancesCount,
                        tokenBalancesCount,
                        timestamp,
                        stopwatch);
            });
        } catch (Exception e) {
            log.error("Failed to generate historical balances in {}", stopwatch, e);
        }
    }

    private void initializeLastTimestamp() {
        if (lastTimestamp != NOT_SET) {
            return;
        }

        // Initialize last timestamp to the consensus timestamp of the latest account balance file in database if
        // present, otherwise the consensus end of the first record file plus initial delay
        accountBalanceFileRepository
                .findLatest()
                .map(accountBalanceFile -> {
                    long timestamp = accountBalanceFile.getConsensusTimestamp();
                    log.info(
                            "Initialize lastTimestamp to {} - the latest account balance file {}'s timestamp",
                            timestamp,
                            accountBalanceFile.getName());
                    return timestamp;
                })
                .or(() -> recordFileRepository.findFirst().map(recordFile -> {
                    long consensusEnd = recordFile.getConsensusEnd();
                    long timestamp = consensusEnd + properties.getInitialDelay().toNanos();
                    log.info(
                            "Initialize lastTimestamp to {} - the first record file {}'s consensus end {} + initial delay {}",
                            timestamp,
                            recordFile.getName(),
                            consensusEnd,
                            properties.getInitialDelay());
                    return timestamp;
                }))
                .ifPresent(timestamp -> lastTimestamp = timestamp);
    }

    private record ParsedStreamFile(long consensusTimestamp, StreamType type) {}
}
