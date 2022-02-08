package com.hedera.mirror.importer.parser.balance;

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

import static com.hedera.mirror.importer.config.MirrorDateRangePropertiesProcessor.DateRangeFilter;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Named;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.transaction.annotation.Transactional;

import com.hedera.mirror.importer.config.MirrorDateRangePropertiesProcessor;
import com.hedera.mirror.common.domain.balance.AccountBalance;
import com.hedera.mirror.common.domain.balance.AccountBalanceFile;
import com.hedera.mirror.common.domain.StreamType;
import com.hedera.mirror.common.domain.balance.TokenBalance;
import com.hedera.mirror.importer.leader.Leader;
import com.hedera.mirror.importer.parser.AbstractStreamFileParser;
import com.hedera.mirror.importer.parser.batch.BatchPersister;
import com.hedera.mirror.importer.repository.StreamFileRepository;

/**
 * Parse an account balances file and load it into the database.
 */
@Named
public class AccountBalanceFileParser extends AbstractStreamFileParser<AccountBalanceFile> {

    private final BatchPersister batchPersister;
    private final MirrorDateRangePropertiesProcessor mirrorDateRangePropertiesProcessor;

    public AccountBalanceFileParser(BatchPersister batchPersister,
                                    MeterRegistry meterRegistry,
                                    BalanceParserProperties parserProperties,
                                    StreamFileRepository<AccountBalanceFile, Long> accountBalanceFileRepository,
                                    MirrorDateRangePropertiesProcessor mirrorDateRangePropertiesProcessor) {
        super(meterRegistry, parserProperties, accountBalanceFileRepository);
        this.batchPersister = batchPersister;
        this.mirrorDateRangePropertiesProcessor = mirrorDateRangePropertiesProcessor;
    }

    /**
     * Process the file and load all the data into the database.
     */
    @Override
    @Leader
    @Retryable(backoff = @Backoff(
            delayExpression = "#{@balanceParserProperties.getRetry().getMinBackoff().toMillis()}",
            maxDelayExpression = "#{@balanceParserProperties.getRetry().getMaxBackoff().toMillis()}",
            multiplierExpression = "#{@balanceParserProperties.getRetry().getMultiplier()}"),
            maxAttemptsExpression = "#{@balanceParserProperties.getRetry().getMaxAttempts()}")
    @Transactional(timeoutString = "#{@balanceParserProperties.getTransactionTimeout().toSeconds()}")
    public void parse(AccountBalanceFile accountBalanceFile) {
        super.parse(accountBalanceFile);
    }

    @Override
    protected void doParse(AccountBalanceFile accountBalanceFile) {
        log.info("Starting processing account balances file {}", accountBalanceFile.getName());
        DateRangeFilter filter = mirrorDateRangePropertiesProcessor.getDateRangeFilter(StreamType.BALANCE);
        int batchSize = ((BalanceParserProperties) parserProperties).getBatchSize();
        long count = 0L;

        if (filter.filter(accountBalanceFile.getConsensusTimestamp())) {
            List<AccountBalance> accountBalances = new ArrayList<>(batchSize);
            List<TokenBalance> tokenBalances = new ArrayList<>(batchSize);

            count = accountBalanceFile.getItems().doOnNext(accountBalance -> {
                accountBalances.add(accountBalance);
                tokenBalances.addAll(accountBalance.getTokenBalances());

                if (accountBalances.size() >= batchSize) {
                    batchPersister.persist(accountBalances);
                    accountBalances.clear();
                }

                if (tokenBalances.size() >= batchSize) {
                    batchPersister.persist(tokenBalances);
                    tokenBalances.clear();
                }
            }).count().block();

            batchPersister.persist(accountBalances);
            batchPersister.persist(tokenBalances);
        }

        Instant loadEnd = Instant.now();
        accountBalanceFile.setCount(count);
        accountBalanceFile.setLoadEnd(loadEnd.getEpochSecond());
        streamFileRepository.save(accountBalanceFile);
    }
}
