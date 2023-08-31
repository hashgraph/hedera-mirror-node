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

    private final AccountBalanceFileRepository accountBalanceFileRepository;
    private final JdbcTemplate jdbcTemplate;
    private final HistoricalBalancesProperties properties;
    private final RecordFileRepository recordFileRepository;
    private final TransactionTemplate transactionTemplate;

    private FluxSink<Long> consensusEndSink;

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
        Flux.<Long>create(sink -> this.consensusEndSink = sink)
                // Drop the oldest to minimize the delay of generating next historical balances
                .onBackpressureBuffer(bufferSize, BufferOverflowStrategy.DROP_OLDEST)
                // Ensure generate runs in the same thread
                .publishOn(Schedulers.newSingle("Historical balances service"))
                .doOnNext(this::generate)
                .doOnError(t -> log.error("Error processing data triggered by parsed record file", t))
                .subscribe();
    }

    /**
     * Listens on {@link RecordFileParsedEvent} and emits the consensusEnd to trigger historical balances generation.
     *
     * @param event The record file parsed event published by {@link RecordFileParser}
     */
    @Async
    @TransactionalEventListener
    public void onRecordFileParsed(RecordFileParsedEvent event) {
        consensusEndSink.next(event.getConsensusEnd());
    }

    private void generate(long consensusEnd) {
        boolean shouldGenerate = accountBalanceFileRepository
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
        if (!shouldGenerate) {
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
}
