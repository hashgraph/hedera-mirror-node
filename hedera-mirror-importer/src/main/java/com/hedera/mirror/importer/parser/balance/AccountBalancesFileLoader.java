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
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.inject.Named;
import javax.sql.DataSource;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.annotation.Transactional;

import com.hedera.mirror.importer.domain.AccountBalance;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.domain.TokenBalance;
import com.hedera.mirror.importer.exception.MissingFileException;
import com.hedera.mirror.importer.exception.ParserSQLException;
import com.hedera.mirror.importer.reader.balance.BalanceFileReader;
import com.hedera.mirror.importer.util.Utility;

/**
 * Parse an account balances file and load it into the database.
 */
@Log4j2
@Named
public class AccountBalancesFileLoader {
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
    private static final String UPDATE_ACCOUNT_BALANCE_FILE_STATEMENT = "update account_balance_file " +
            "set " +
            "   consensus_timestamp = ?," +
            "   count = ?," +
            "   load_start = ?," +
            "   load_end = ?" +
            "where name = ?;";

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

    enum F_UPDATE_ACCOUNT_BALANCE_FILE {
        ZERO,
        CONSENSUS_TIMESTAMP, COUNT, LOAD_START, LOAD_END, NAME
    }

    private final int insertBatchSize;
    private final DataSource dataSource;
    private final BalanceFileReader balanceFileReader;
    private final Timer parseDurationMetricFailure;
    private final Timer parseDurationMetricSuccess;
    private final Timer parseLatencyMetric;

    public AccountBalancesFileLoader(BalanceParserProperties balanceParserProperties, DataSource dataSource,
                                     BalanceFileReader balanceFileReader, MeterRegistry meterRegistry) {
        insertBatchSize = balanceParserProperties.getBatchSize();
        this.dataSource = dataSource;
        this.balanceFileReader = balanceFileReader;

        Timer.Builder parseDurationTimerBuilder = Timer.builder("hedera.mirror.parse.duration")
                .description("The duration in seconds it took to parse the file and store it in the database")
                .tag("type", balanceParserProperties.getStreamType().toString());
        parseDurationMetricFailure = parseDurationTimerBuilder.tag("success", "false").register(meterRegistry);
        parseDurationMetricSuccess = parseDurationTimerBuilder.tag("success", "true").register(meterRegistry);

        parseLatencyMetric = Timer.builder("hedera.mirror.parse.latency")
                .description("The difference in ms between the consensus time of the last transaction in the file " +
                        "and the time at which the file was processed successfully")
                .tag("type", balanceParserProperties.getStreamType().toString())
                .register(meterRegistry);
    }

    /**
     * Process the file and load all the data into the database.
     */
    @Transactional
    public void loadAccountBalances(@NonNull File balanceFile, DateRangeFilter dateRangeFilter) {
        log.info("Starting processing account balances file {}", balanceFile.getPath());
        String fileName = balanceFile.getName();
        Stopwatch stopwatch = Stopwatch.createStarted();
        Instant startTime = Instant.now();
        boolean success = false;

        Connection connection = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement insertSetStatement = connection.prepareStatement(INSERT_SET_STATEMENT);
             PreparedStatement insertBalanceStatement = connection.prepareStatement(INSERT_BALANCE_STATEMENT);
             PreparedStatement insertTokenBalanceStatement = connection
                     .prepareStatement(INSERT_TOKEN_BALANCE_STATEMENT);
             PreparedStatement updateSetStatement = connection.prepareStatement(UPDATE_SET_STATEMENT);
             PreparedStatement updateAccountBalanceFileStatement = connection
                     .prepareStatement(UPDATE_ACCOUNT_BALANCE_FILE_STATEMENT)) {
            List<AccountBalance> accountBalances = new ArrayList<>();
            balanceFileReader.read(StreamFileData.from(balanceFile), accountBalances::add);
            long consensusTimestamp = -1;
            long timestampFromFileName = Utility.getTimestampFromFilename(fileName);
            List<AccountBalance> accountBalanceList = new ArrayList<>();
            List<TokenBalance> tokenBalanceList = new ArrayList<>();
            boolean skip = false;
            int validCount = 0;

            for (AccountBalance accountBalance : accountBalances) {
                if (consensusTimestamp == -1) {
                    consensusTimestamp = accountBalance.getId().getConsensusTimestamp();
                    if (timestampFromFileName != consensusTimestamp) {
                        // The assumption is that the dataset has been validated via signatures and running hashes,
                        // so it is the "next" dataset, and the consensus timestamp in it is correct. The fact that
                        // the filename timestamp and timestamp in the file differ should still be investigated.
                        log.error("Account balance dataset timestamp mismatch! Processing can continue, but this " +
                                        "must be investigated! Dataset {} internal timestamp {} filename timestamp {}.",
                                fileName, consensusTimestamp, timestampFromFileName);
                    }

                    if (dateRangeFilter != null && !dateRangeFilter.filter(consensusTimestamp)) {
                        log.warn("Account balances file {} not in configured date range {}, skip it",
                                fileName, dateRangeFilter);
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

            updateAccountBalanceFile(updateAccountBalanceFileStatement, consensusTimestamp, validCount,
                    startTime.getEpochSecond(), Instant.now().getEpochSecond(), fileName);

            if (!skip) {
                log.info("Successfully processed account balances file {}, {} records inserted in {}",
                        fileName, validCount, stopwatch);
            }

            success = true;
        } catch (SQLException ex) {
            throw new ParserSQLException("Error processing account balance file " + fileName, ex);
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

    private void updateAccountBalanceFile(PreparedStatement statement, long consensusTimestamp, long count,
                                          long loadStart, long loadEnd, String filename) throws SQLException {
        statement.setLong(F_UPDATE_ACCOUNT_BALANCE_FILE.CONSENSUS_TIMESTAMP.ordinal(), consensusTimestamp);
        statement.setLong(F_UPDATE_ACCOUNT_BALANCE_FILE.COUNT.ordinal(), count);
        statement.setLong(F_UPDATE_ACCOUNT_BALANCE_FILE.LOAD_START.ordinal(), loadStart);
        statement.setLong(F_UPDATE_ACCOUNT_BALANCE_FILE.LOAD_END.ordinal(), loadEnd);
        statement.setString(F_UPDATE_ACCOUNT_BALANCE_FILE.NAME.ordinal(), filename);

        if (statement.executeUpdate() != 1) {
            throw new MissingFileException("File " + filename + " not in the database, thus not updated");
        }

        long loadEndMillis = loadEnd * 1_000; // s -> ms
        long consensusEndMillis = consensusTimestamp / 1_000_000; // ns -> ms
        parseLatencyMetric.record(loadEndMillis - consensusEndMillis, TimeUnit.MILLISECONDS);
    }
}
