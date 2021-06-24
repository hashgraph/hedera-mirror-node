package com.hedera.mirror.importer.parser.balance;

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

import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Named;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.transaction.annotation.Transactional;

import com.hedera.mirror.importer.config.MirrorDateRangePropertiesProcessor;
import com.hedera.mirror.importer.domain.AccountBalance;
import com.hedera.mirror.importer.domain.AccountBalanceFile;
import com.hedera.mirror.importer.domain.StreamType;
import com.hedera.mirror.importer.domain.TokenBalance;
import com.hedera.mirror.importer.leader.Leader;
import com.hedera.mirror.importer.parser.AbstractStreamFileParser;
import com.hedera.mirror.importer.parser.PgCopy;
import com.hedera.mirror.importer.repository.StreamFileRepository;

/**
 * Parse an account balances file and load it into the database.
 */
@Named
public class AccountBalanceFileParser extends AbstractStreamFileParser<AccountBalanceFile> {

    private final DataSource dataSource;
    private final MirrorDateRangePropertiesProcessor mirrorDateRangePropertiesProcessor;
    private final PgCopy<AccountBalance> pgCopyAccountBalance;
    private final PgCopy<TokenBalance> pgCopyTokenBalance;

    public AccountBalanceFileParser(MeterRegistry meterRegistry, BalanceParserProperties parserProperties,
                                    StreamFileRepository<AccountBalanceFile, Long> accountBalanceFileRepository,
                                    DataSource dataSource,
                                    MirrorDateRangePropertiesProcessor mirrorDateRangePropertiesProcessor) {
        super(meterRegistry, parserProperties, accountBalanceFileRepository);
        this.dataSource = dataSource;
        this.mirrorDateRangePropertiesProcessor = mirrorDateRangePropertiesProcessor;
        pgCopyAccountBalance = new PgCopy<>(AccountBalance.class, meterRegistry, parserProperties);
        pgCopyTokenBalance = new PgCopy<>(TokenBalance.class, meterRegistry, parserProperties);
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
        Connection connection = DataSourceUtils.getConnection(dataSource);
        long count = 0L;

        try {
            if (filter.filter(accountBalanceFile.getConsensusTimestamp())) {
                List<AccountBalance> accountBalances = accountBalanceFile.getItems();
                List<TokenBalance> tokenBalances = accountBalances
                        .stream()
                        .flatMap(a -> a.getTokenBalances().stream())
                        .collect(Collectors.toList());

                pgCopyAccountBalance.copy(accountBalances, connection);
                pgCopyTokenBalance.copy(tokenBalances, connection);
                count = accountBalances.size();
            }

            Instant loadEnd = Instant.now();
            accountBalanceFile.setCount(count);
            accountBalanceFile.setLoadEnd(loadEnd.getEpochSecond());
            streamFileRepository.save(accountBalanceFile);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }
}
