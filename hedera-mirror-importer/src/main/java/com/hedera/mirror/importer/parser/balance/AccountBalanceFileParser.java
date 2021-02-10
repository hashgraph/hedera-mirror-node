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

import static com.hedera.mirror.importer.config.IntegrationConfiguration.CHANNEL_BALANCE;
import static com.hedera.mirror.importer.config.MirrorDateRangePropertiesProcessor.DateRangeFilter;

import com.google.common.base.Stopwatch;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import lombok.extern.log4j.Log4j2;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.Poller;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.transaction.annotation.Transactional;

import com.hedera.mirror.importer.config.MirrorDateRangePropertiesProcessor;
import com.hedera.mirror.importer.domain.AccountBalance;
import com.hedera.mirror.importer.domain.AccountBalanceFile;
import com.hedera.mirror.importer.domain.TokenBalance;
import com.hedera.mirror.importer.exception.ParserSQLException;
import com.hedera.mirror.importer.parser.StreamFileParser;
import com.hedera.mirror.importer.repository.AccountBalanceFileRepository;
import com.hedera.mirror.importer.util.Utility;

/**
 * Parse an account balances file and load it into the database.
 */
@Log4j2
@MessageEndpoint
public class AccountBalanceFileParser implements StreamFileParser<AccountBalanceFile> {

    private static final String INSERT_SET_STATEMENT = "insert into account_balance_sets (consensus_timestamp) " +
            "values (?) on conflict do nothing;";
    private static final String INSERT_BALANCE_STATEMENT = "insert into account_balance " +
            "(consensus_timestamp, account_id, balance) values (?, ?, ?) on conflict do " +
            "nothing;";
    private static final String INSERT_TOKEN_BALANCE_STATEMENT = "insert into token_balance " +
            "(consensus_timestamp, account_id, balance, token_id) values (?, ?, ?, ?) on conflict do " +
            "nothing;";
    private static final String UPDATE_SET_STATEMENT = "update account_balance_sets set is_complete = ?, " +
            "processing_end_timestamp = now() at time zone 'utc' where consensus_timestamp = ? and is_complete = " +
            "false;";

    enum F_INSERT_SET {
        ZERO,
        CONSENSUS_TIMESTAMP
    }

    enum F_INSERT_BALANCE {
        ZERO,
        CONSENSUS_TIMESTAMP, ACCOUNT_ID, BALANCE
    }

    enum F_INSERT_TOKEN_BALANCE {
        ZERO,
        CONSENSUS_TIMESTAMP, ACCOUNT_ID, BALANCE, TOKEN_ID
    }

    enum F_UPDATE_SET {
        ZERO,
        IS_COMPLETE, CONSENSUS_TIMESTAMP
    }

    private final BalanceParserProperties parserProperties;
    private final DataSource dataSource;
    private final Timer parseDurationMetricFailure;
    private final Timer parseDurationMetricSuccess;
    private final Timer parseLatencyMetric;
    private final MirrorDateRangePropertiesProcessor mirrorDateRangePropertiesProcessor;
    private final AccountBalanceFileRepository accountBalanceFileRepository;

    public AccountBalanceFileParser(BalanceParserProperties parserProperties, DataSource dataSource,
                                    MeterRegistry meterRegistry,
                                    MirrorDateRangePropertiesProcessor mirrorDateRangePropertiesProcessor,
                                    AccountBalanceFileRepository accountBalanceFileRepository) {
        this.parserProperties = parserProperties;
        this.dataSource = dataSource;
        this.mirrorDateRangePropertiesProcessor = mirrorDateRangePropertiesProcessor;
        this.accountBalanceFileRepository = accountBalanceFileRepository;

        Timer.Builder parseDurationTimerBuilder = Timer.builder("hedera.mirror.parse.duration")
                .description("The duration in seconds it took to parse the file and store it in the database")
                .tag("type", parserProperties.getStreamType().toString());
        parseDurationMetricFailure = parseDurationTimerBuilder.tag("success", "false").register(meterRegistry);
        parseDurationMetricSuccess = parseDurationTimerBuilder.tag("success", "true").register(meterRegistry);

        parseLatencyMetric = Timer.builder("hedera.mirror.parse.latency")
                .description("The difference in ms between the consensus time of the last transaction in the file " +
                        "and the time at which the file was processed successfully")
                .tag("type", parserProperties.getStreamType().toString())
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
    @ServiceActivator(inputChannel = CHANNEL_BALANCE,
            poller = @Poller(fixedDelay = "${hedera.mirror.importer.parser.balance.frequency:100}")
    )
    @Transactional
    public void parse(AccountBalanceFile accountBalanceFile) {
        if (!parserProperties.isEnabled()) {
            return;
        }

        log.info("Starting processing account balances file {}", accountBalanceFile.getName());
        Stopwatch stopwatch = Stopwatch.createStarted();
        boolean success = false;
        int insertBatchSize = parserProperties.getBatchSize();
        DateRangeFilter dateRangeFilter = mirrorDateRangePropertiesProcessor
                .getDateRangeFilter(parserProperties.getStreamType());

        Connection connection = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement insertSetStatement = connection.prepareStatement(INSERT_SET_STATEMENT);
             PreparedStatement insertBalanceStatement = connection.prepareStatement(INSERT_BALANCE_STATEMENT);
             PreparedStatement insertTokenBalanceStatement = connection
                     .prepareStatement(INSERT_TOKEN_BALANCE_STATEMENT);
             PreparedStatement updateSetStatement = connection.prepareStatement(UPDATE_SET_STATEMENT)) {

            long consensusTimestamp = -1;
            List<AccountBalance> accountBalanceList = new ArrayList<>();
            List<TokenBalance> tokenBalanceList = new ArrayList<>();
            boolean skip = false;
            long validCount = 0;

            for (AccountBalance accountBalance : accountBalanceFile.getItems()) {
                if (consensusTimestamp == -1) {
                    consensusTimestamp = accountBalance.getId().getConsensusTimestamp();
                    if (accountBalanceFile.getConsensusTimestamp() != consensusTimestamp) {
                        // The assumption is that the dataset has been validated via signatures and running hashes,
                        // so it is the "next" dataset, and the consensus timestamp in it is correct. The fact that
                        // the filename timestamp and timestamp in the file differ should still be investigated.
                        log.error("Account balance dataset timestamp mismatch! Processing can continue, but this " +
                                        "must be investigated! Dataset {} internal timestamp {} filename timestamp {}.",
                                accountBalanceFile.getName(), consensusTimestamp, accountBalanceFile
                                        .getConsensusTimestamp());
                    }

                    if (dateRangeFilter != null && !dateRangeFilter.filter(consensusTimestamp)) {
                        log.warn("Account balances file {} not in configured date range {}, skip it",
                                accountBalanceFile.getName(), dateRangeFilter);
                        skip = true;
                    } else {
                        insertAccountBalanceSet(insertSetStatement, consensusTimestamp);
                    }
                }

                validCount++;

                if (!skip) {
                    accountBalanceList.add(accountBalance);
                    tokenBalanceList.addAll(accountBalance.getTokenBalances());
                    tryInsertBatchAccountBalance(insertBalanceStatement, accountBalanceList, insertBatchSize);
                    tryInsertBatchTokenBalance(insertTokenBalanceStatement, tokenBalanceList, insertBatchSize);
                }
            }

            if (!skip) {
                tryInsertBatchAccountBalance(insertBalanceStatement, accountBalanceList, 1);
                tryInsertBatchTokenBalance(insertTokenBalanceStatement, tokenBalanceList, 1);
                updateAccountBalanceSet(updateSetStatement, consensusTimestamp);
            }

            byte[] bytes = accountBalanceFile.getBytes();
            if (!parserProperties.isPersistBytes()) {
                accountBalanceFile.setBytes(null);
            }

            Instant loadEnd = Instant.now();
            accountBalanceFile.setCount(validCount);
            accountBalanceFile.setLoadEnd(loadEnd.getEpochSecond());
            accountBalanceFileRepository.save(accountBalanceFile);

            if (parserProperties.isKeepFiles()) {
                Utility.archiveFile(accountBalanceFile.getName(), bytes, parserProperties.getParsedPath());
            }

            if (!skip) {
                log.info("Successfully processed account balances file {}, {} records inserted in {}",
                        accountBalanceFile.getName(), validCount, stopwatch);
            }

            Instant consensusInstant = Instant.ofEpochSecond(0L, consensusTimestamp);
            parseLatencyMetric.record(Duration.between(consensusInstant, loadEnd));
            success = true;
        } catch (SQLException ex) {
            log.error("Failed to load account balance file {}", accountBalanceFile.getName(), ex);
            throw new ParserSQLException("Error processing account balance file " + accountBalanceFile.getName(), ex);
        } catch (Exception ex) {
            log.error("Failed to load account balance file {}", accountBalanceFile.getName(), ex);
            throw ex;
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
            Timer timer = success ? parseDurationMetricSuccess : parseDurationMetricFailure;
            timer.record(stopwatch.elapsed());
        }
    }

    private void insertAccountBalanceSet(PreparedStatement insertSetStatement, long consensusTimestamp) throws SQLException {
        insertSetStatement.setLong(F_INSERT_SET.CONSENSUS_TIMESTAMP.ordinal(), consensusTimestamp);
        insertSetStatement.execute();
    }

    private void tryInsertBatchAccountBalance(PreparedStatement insertBalanceStatement,
                                              List<AccountBalance> accountBalanceList, int threshold) throws SQLException {
        if (accountBalanceList.size() < threshold) {
            return;
        }

        for (var accountBalance : accountBalanceList) {
            AccountBalance.Id id = accountBalance.getId();
            insertBalanceStatement.setLong(F_INSERT_BALANCE.CONSENSUS_TIMESTAMP.ordinal(), id.getConsensusTimestamp());
            insertBalanceStatement.setLong(F_INSERT_BALANCE.ACCOUNT_ID.ordinal(), id.getAccountId().getId());
            insertBalanceStatement.setLong(F_INSERT_BALANCE.BALANCE.ordinal(), accountBalance.getBalance());
            insertBalanceStatement.addBatch();
        }

        accountBalanceList.clear();

        int[] result = insertBalanceStatement.executeBatch();
        for (int code : result) {
            if (code == Statement.EXECUTE_FAILED) {
                throw new ParserSQLException("Some account balance insert statements in the batch failed to execute");
            }
        }
    }

    private void tryInsertBatchTokenBalance(PreparedStatement insertTokenBalanceStatement,
                                            List<TokenBalance> tokenBalances, int threshold) throws SQLException {
        if (tokenBalances.size() < threshold) {
            return;
        }

        for (var tokenBalance : tokenBalances) {
            TokenBalance.Id id = tokenBalance.getId();
            insertTokenBalanceStatement
                    .setLong(F_INSERT_TOKEN_BALANCE.CONSENSUS_TIMESTAMP.ordinal(), id.getConsensusTimestamp
                            ());
            insertTokenBalanceStatement
                    .setLong(F_INSERT_TOKEN_BALANCE.ACCOUNT_ID.ordinal(), id.getAccountId().getId());
            insertTokenBalanceStatement
                    .setLong(F_INSERT_TOKEN_BALANCE.BALANCE.ordinal(), tokenBalance.getBalance());
            insertTokenBalanceStatement
                    .setLong(F_INSERT_TOKEN_BALANCE.TOKEN_ID.ordinal(), id.getTokenId().getId());
            insertTokenBalanceStatement.addBatch();
        }

        tokenBalances.clear();

        int[] result = insertTokenBalanceStatement.executeBatch();
        for (int code : result) {
            if (code == Statement.EXECUTE_FAILED) {
                throw new ParserSQLException("Some token balance insert statements in the batch failed to execute");
            }
        }
    }

    private void updateAccountBalanceSet(PreparedStatement updateSetStatement, long consensusTimestamp) throws
            SQLException {
        updateSetStatement.setBoolean(F_UPDATE_SET.IS_COMPLETE.ordinal(), true);
        updateSetStatement.setLong(F_UPDATE_SET.CONSENSUS_TIMESTAMP.ordinal(), consensusTimestamp);
        updateSetStatement.execute();
    }
}
