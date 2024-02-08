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

package com.hedera.mirror.importer.parser.balance;

import static com.hedera.mirror.importer.config.DateRangeCalculator.DateRangeFilter;

import com.hedera.mirror.common.domain.StreamType;
import com.hedera.mirror.common.domain.balance.AccountBalance;
import com.hedera.mirror.common.domain.balance.AccountBalanceFile;
import com.hedera.mirror.common.domain.balance.TokenBalance;
import com.hedera.mirror.importer.config.DateRangeCalculator;
import com.hedera.mirror.importer.leader.Leader;
import com.hedera.mirror.importer.parser.AbstractStreamFileParser;
import com.hedera.mirror.importer.parser.batch.BatchPersister;
import com.hedera.mirror.importer.repository.StreamFileRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Named;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.transaction.annotation.Transactional;

/**
 * Parse an account balances file and load it into the database.
 */
@Named
public class AccountBalanceFileParser extends AbstractStreamFileParser<AccountBalanceFile> {

    private final BatchPersister batchPersister;
    private final DateRangeCalculator dateRangeCalculator;

    public AccountBalanceFileParser(
            BatchPersister batchPersister,
            MeterRegistry meterRegistry,
            BalanceParserProperties parserProperties,
            StreamFileRepository<AccountBalanceFile, Long> accountBalanceFileRepository,
            DateRangeCalculator dateRangeCalculator,
            BalanceStreamFileListener streamFileListener) {
        super(meterRegistry, parserProperties, streamFileListener, accountBalanceFileRepository);
        this.batchPersister = batchPersister;
        this.dateRangeCalculator = dateRangeCalculator;
    }

    /**
     * Process the file and load all the data into the database.
     */
    @Override
    @Leader
    @Retryable(
            backoff =
                    @Backoff(
                            delayExpression = "#{@balanceParserProperties.getRetry().getMinBackoff().toMillis()}",
                            maxDelayExpression = "#{@balanceParserProperties.getRetry().getMaxBackoff().toMillis()}",
                            multiplierExpression = "#{@balanceParserProperties.getRetry().getMultiplier()}"),
            maxAttemptsExpression = "#{@balanceParserProperties.getRetry().getMaxAttempts()}")
    @Transactional(timeoutString = "#{@balanceParserProperties.getTransactionTimeout().toSeconds()}")
    public synchronized void parse(AccountBalanceFile accountBalanceFile) {
        super.parse(accountBalanceFile);
    }

    @Override
    protected void doParse(AccountBalanceFile accountBalanceFile) {
        log.info("Starting processing account balances file {}", accountBalanceFile.getName());
        DateRangeFilter filter = dateRangeCalculator.getFilter(StreamType.BALANCE);
        int batchSize = ((BalanceParserProperties) parserProperties).getBatchSize();
        long count = 0L;

        if (filter.filter(accountBalanceFile.getConsensusTimestamp())) {
            List<AccountBalance> accountBalances = new ArrayList<>(batchSize);
            Map<TokenBalance.Id, TokenBalance> tokenBalances = new HashMap<>(batchSize);

            count = accountBalanceFile
                    .getItems()
                    .doOnNext(accountBalance -> {
                        accountBalances.add(accountBalance);
                        for (var tokenBalance : accountBalance.getTokenBalances()) {
                            if (tokenBalances.putIfAbsent(tokenBalance.getId(), tokenBalance) != null) {
                                log.warn("Skipping duplicate token balance: {}", tokenBalance);
                            }
                        }

                        if (accountBalances.size() >= batchSize) {
                            batchPersister.persist(accountBalances);
                            accountBalances.clear();
                        }

                        if (tokenBalances.size() >= batchSize) {
                            batchPersister.persist(tokenBalances.values());
                            tokenBalances.clear();
                        }
                    })
                    .count()
                    .block();

            batchPersister.persist(accountBalances);
            batchPersister.persist(tokenBalances.values());
        }

        Instant loadEnd = Instant.now();
        accountBalanceFile.setCount(count);
        accountBalanceFile.setLoadEnd(loadEnd.getEpochSecond());
    }
}
