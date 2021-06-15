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

import com.google.common.base.Stopwatch;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Named;
import javax.sql.DataSource;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.transaction.annotation.Transactional;

import com.hedera.mirror.importer.config.MirrorDateRangePropertiesProcessor;
import com.hedera.mirror.importer.domain.AccountBalance;
import com.hedera.mirror.importer.domain.AccountBalanceFile;
import com.hedera.mirror.importer.domain.StreamType;
import com.hedera.mirror.importer.domain.TokenBalance;
import com.hedera.mirror.importer.parser.AbstractStreamFileParser;
import com.hedera.mirror.importer.parser.PgCopy;
import com.hedera.mirror.importer.repository.AccountBalanceFileRepository;

/**
 * Parse an account balances file and load it into the database.
 */
@Log4j2
@Named
public class AccountBalanceFileParser extends AbstractStreamFileParser<AccountBalanceFile> {

    private final DataSource dataSource;
    private final Timer parseDurationMetricFailure;
    private final Timer parseDurationMetricSuccess;
    private final Timer parseLatencyMetric;
    private final MirrorDateRangePropertiesProcessor mirrorDateRangePropertiesProcessor;
    private final AccountBalanceFileRepository accountBalanceFileRepository;
    private final PgCopy<AccountBalance> pgCopyAccountBalance;
    private final PgCopy<TokenBalance> pgCopyTokenBalance;

    public AccountBalanceFileParser(BalanceParserProperties properties, DataSource dataSource,
                                    MeterRegistry meterRegistry,
                                    MirrorDateRangePropertiesProcessor mirrorDateRangePropertiesProcessor,
                                    AccountBalanceFileRepository accountBalanceFileRepository) {
        super(properties);
        this.dataSource = dataSource;
        this.mirrorDateRangePropertiesProcessor = mirrorDateRangePropertiesProcessor;
        this.accountBalanceFileRepository = accountBalanceFileRepository;
        pgCopyAccountBalance = new PgCopy<>(AccountBalance.class, meterRegistry, properties);
        pgCopyTokenBalance = new PgCopy<>(TokenBalance.class, meterRegistry, properties);

        Timer.Builder parseDurationTimerBuilder = Timer.builder(STREAM_PARSE_DURATION_METRIC_NAME)
                .description("The duration in seconds it took to parse the file and store it in the database")
                .tag("type", properties.getStreamType().toString());
        parseDurationMetricFailure = parseDurationTimerBuilder.tag("success", "false").register(meterRegistry);
        parseDurationMetricSuccess = parseDurationTimerBuilder.tag("success", "true").register(meterRegistry);

        parseLatencyMetric = Timer.builder("hedera.mirror.parse.latency")
                .description("The difference in ms between the consensus time of the last transaction in the file " +
                        "and the time at which the file was processed successfully")
                .tag("type", properties.getStreamType().toString())
                .register(meterRegistry);
    }

    /**
     * Process the file and load all the data into the database.
     */
    @Override
    @Retryable(backoff = @Backoff(
            delayExpression = "#{@balanceParserProperties.getRetry().getMinBackoff().toMillis()}",
            maxDelayExpression = "#{@balanceParserProperties.getRetry().getMaxBackoff().toMillis()}",
            multiplierExpression = "#{@balanceParserProperties.getRetry().getMultiplier()}"),
            maxAttemptsExpression = "#{@balanceParserProperties.getRetry().getMaxAttempts()}")
    @Transactional
    public void parse(AccountBalanceFile accountBalanceFile) {
        super.parse(accountBalanceFile);
    }

    @Override
    protected void doParse(AccountBalanceFile accountBalanceFile) {
        long consensusTimestamp = accountBalanceFile.getConsensusTimestamp();
        long count = 0L;
        String name = accountBalanceFile.getName();
        Stopwatch stopwatch = Stopwatch.createStarted();
        boolean success = false;

        log.info("Starting processing account balances file {}", name);
        DateRangeFilter filter = mirrorDateRangePropertiesProcessor.getDateRangeFilter(StreamType.BALANCE);
        Connection connection = DataSourceUtils.getConnection(dataSource);

        try {
            if (filter.filter(consensusTimestamp)) {
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
            accountBalanceFileRepository.save(accountBalanceFile);

            log.info("Successfully processed {} account balances from {} in {}", count, name, stopwatch);
            Instant consensusInstant = Instant.ofEpochSecond(0L, consensusTimestamp);
            parseLatencyMetric.record(Duration.between(consensusInstant, loadEnd));
            success = true;
        } catch (Exception ex) {
            log.error("Failed to load account balance file {}", name, ex);
            throw ex;
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
            Timer timer = success ? parseDurationMetricSuccess : parseDurationMetricFailure;
            timer.record(stopwatch.elapsed());
        }
    }
}
