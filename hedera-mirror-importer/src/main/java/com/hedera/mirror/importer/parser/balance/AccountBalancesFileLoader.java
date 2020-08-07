package com.hedera.mirror.importer.parser.balance;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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

import com.google.common.base.Stopwatch;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javax.inject.Named;
import javax.sql.DataSource;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;

import com.hedera.mirror.importer.domain.AccountBalance;
import com.hedera.mirror.importer.exception.InvalidDatasetException;
import com.hedera.mirror.importer.util.Utility;

/**
 * Parse an account balances file and load it into the database.
 */
@Log4j2
@Named
public final class AccountBalancesFileLoader {
    private static final String INSERT_SET_STATEMENT = "insert into account_balance_sets (consensus_timestamp) " +
            "values (?) on conflict do nothing;";
    private static final String INSERT_BALANCE_STATEMENT = "insert into account_balance " +
            "(consensus_timestamp, account_realm_num, account_num, balance) values (?, ?, ?, ?) on conflict do nothing;";
    private static final String UPDATE_SET_STATEMENT = "update account_balance_sets set is_complete = ?, " +
            "processing_end_timestamp = now() at time zone 'utc' where consensus_timestamp = ? and is_complete = false;";

    enum F_INSERT_SET {
        ZERO,
        CONSENSUS_TIMESTAMP
    }

    enum F_INSERT_BALANCE {
        ZERO,
        CONSENSUS_TIMESTAMP, ACCOUNT_REALM_NUM, ACCOUNT_NUM, BALANCE
    }

    enum F_UPDATE_SET {
        ZERO,
        IS_COMPLETE, CONSENSUS_TIMESTAMP
    }

    private final int insertBatchSize;
    private final DataSource dataSource;
    private final BalanceFileReader balanceFileReader;

    public AccountBalancesFileLoader(BalanceParserProperties balanceParserProperties, DataSource dataSource,
            BalanceFileReader balanceFileReader) {
        this.insertBatchSize = balanceParserProperties.getBatchSize();
        this.dataSource = dataSource;
        this.balanceFileReader = balanceFileReader;
    }

    /**
     * Process the file and load all the data into the database.
     *
     * @return true on success (if the file was completely and fully processed).
     */
    public boolean loadAccountBalances(@NonNull File balanceFile) {
        log.info("Starting processing account balances file {}", balanceFile.getPath());
        final String fileName = balanceFile.getName();
        long timestampFromFileName = Utility.getTimestampFromFilename(fileName);
        int validCount = 0;
        int insertedCount = 0;
        boolean complete = false;
        Stopwatch stopwatch = Stopwatch.createStarted();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement insertSetStatement = connection.prepareStatement(INSERT_SET_STATEMENT);
             PreparedStatement insertBalanceStatement = connection.prepareStatement(INSERT_BALANCE_STATEMENT);
             PreparedStatement updateSetStatement = connection.prepareStatement(UPDATE_SET_STATEMENT);
             Stream<AccountBalance> stream = balanceFileReader.read(balanceFile)) {
            long consensusTimestamp = -1;
            List<AccountBalance> accountBalanceList = new ArrayList<>();

            var iter = stream.iterator();
            while (iter.hasNext()) {
                AccountBalance accountBalance = iter.next();
                if (consensusTimestamp == -1) {
                    consensusTimestamp = accountBalance.getId().getConsensusTimestamp();
                    if (timestampFromFileName != consensusTimestamp) {
                        // The assumption is that the dataset has been validated via signatures and running hashes, so it is
                        // the "next" dataset, and the consensus timestamp in it is correct.
                        // The fact that the filename timestamp and timestamp in the file differ should still be investigated.
                        log.error("Account balance dataset timestamp mismatch! Processing can continue, but this must be " +
                                        "investigated! Dataset {} internal timestamp {} filename timestamp {}.",
                                fileName, consensusTimestamp, timestampFromFileName);
                    }

                    insertAccountBalanceSet(insertSetStatement, consensusTimestamp);
                }

                validCount++;
                accountBalanceList.add(accountBalance);
                insertedCount += tryInsertBatchAccountBalance(insertBalanceStatement, accountBalanceList, insertBatchSize);
            }

            insertedCount += tryInsertBatchAccountBalance(insertBalanceStatement, accountBalanceList, 1);
            complete = (insertedCount == validCount);
            updateAccountBalanceSet(updateSetStatement, complete, consensusTimestamp);
        } catch (InvalidDatasetException | SQLException ex) {
            log.error("Failed to load account balances file " + fileName, ex);
        }

        if (complete) {
            log.info("Successfully processed account balances file {} with {} out of {} records inserted in {}",
                    fileName, insertedCount, validCount, stopwatch);
        } else {
            log.error("ERRORS processing account balances file {} with {} out of {} records inserted in {}", fileName,
                    insertedCount, validCount, stopwatch);
        }

        return complete;
    }

    private void insertAccountBalanceSet(PreparedStatement insertSetStatement, long consensusTimestamp) throws SQLException {
        insertSetStatement.setLong(F_INSERT_SET.CONSENSUS_TIMESTAMP.ordinal(), consensusTimestamp);
        insertSetStatement.execute();
    }

    private int tryInsertBatchAccountBalance(PreparedStatement insertBalanceStatement, List<AccountBalance> accountBalanceList, int threshold) {
        if (accountBalanceList.size() < threshold) {
            return 0;
        }

        int batchSize = 0;
        for (var accountBalance : accountBalanceList) {
            AccountBalance.AccountBalanceId id = accountBalance.getId();
            try {
                insertBalanceStatement.setLong(F_INSERT_BALANCE.CONSENSUS_TIMESTAMP.ordinal(), id.getConsensusTimestamp());
                insertBalanceStatement.setShort(F_INSERT_BALANCE.ACCOUNT_REALM_NUM.ordinal(), (short)id.getAccountRealmNum());
                insertBalanceStatement.setInt(F_INSERT_BALANCE.ACCOUNT_NUM.ordinal(), id.getAccountNum());
                insertBalanceStatement.setLong(F_INSERT_BALANCE.BALANCE.ordinal(), accountBalance.getBalance());
                insertBalanceStatement.addBatch();
                batchSize++;
            } catch(SQLException ex) {
                log.error("Failed to add account balance to the batch", ex);
            }
        }

        accountBalanceList.clear();
        if (batchSize == 0) {
            return 0;
        }

        int insertedCount = 0;
        try {
            var result = insertBalanceStatement.executeBatch();
            for (int code : result) {
                if (code != Statement.EXECUTE_FAILED) {
                    insertedCount++;
                }
            }
        } catch(SQLException ex) {
            log.error("Failed to batch insert account balances", ex);
        }
        return insertedCount;
    }

    private void updateAccountBalanceSet(PreparedStatement updateSetStatement, boolean complete, long consensusTimestamp)  throws SQLException {
        updateSetStatement.setBoolean(F_UPDATE_SET.IS_COMPLETE.ordinal(), complete);
        updateSetStatement.setLong(F_UPDATE_SET.CONSENSUS_TIMESTAMP.ordinal(), consensusTimestamp);
        updateSetStatement.execute();
    }
}
