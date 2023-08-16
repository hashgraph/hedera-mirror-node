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

package com.hedera.mirror.importer.migration;

import com.hedera.mirror.common.domain.balance.AccountBalanceFile;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.importer.exception.ImporterException;
import com.hedera.mirror.importer.parser.balance.BalanceStreamFileListener;
import com.hedera.mirror.importer.repository.AccountBalanceFileRepository;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

abstract class TimeSensitiveBalanceMigration extends RepeatableMigration
        implements BalanceStreamFileListener, TransactionSynchronization {

    private static final int NO_BALANCE_FILE = 0;
    private static final int EXECUTED = -1;
    private final AccountBalanceFileRepository accountBalanceFileRepository;
    private final RecordFileRepository recordFileRepository;
    private final AtomicLong firstConsensusTimestamp = new AtomicLong(0L);

    protected TimeSensitiveBalanceMigration(
            Map<String, MigrationProperties> migrationPropertiesMap,
            AccountBalanceFileRepository accountBalanceFileRepository,
            RecordFileRepository recordFileRepository) {
        super(migrationPropertiesMap);
        this.accountBalanceFileRepository = accountBalanceFileRepository;
        this.recordFileRepository = recordFileRepository;
    }

    @Override
    public void onEnd(AccountBalanceFile accountBalanceFile) throws ImporterException {
        try {
            if (firstConsensusTimestamp.get() == EXECUTED) {
                return;
            }

            // Check if this is the first account balance file after importer startup
            if (firstConsensusTimestamp.get() == NO_BALANCE_FILE) {
                // Set current file timestamp to firstConsensusTimestamp.
                if (accountBalanceFileRepository
                        .findLatestBefore(accountBalanceFile.getConsensusTimestamp())
                        .isEmpty()) {
                    firstConsensusTimestamp.set(accountBalanceFile.getConsensusTimestamp());
                } else {
                    // Set firstConsensusTimestamp to -1 to add an early return in case of existing account balance
                    // files.
                    firstConsensusTimestamp.set(EXECUTED);
                    return;
                }
            }

            // Check if at-least one recordFile after the account balance file has been parsed,the migration will then
            // update rows
            if (recordFileRepository
                    .findLatest()
                    .map(RecordFile::getConsensusEnd)
                    .filter(timestamp -> timestamp >= firstConsensusTimestamp.get())
                    .isPresent()) {
                TransactionSynchronizationManager.registerSynchronization(this);
                doMigrate();
            }
        } catch (IOException e) {
            log.error(
                    "Error executing the migration again after consensus_timestamp {}",
                    accountBalanceFile.getConsensusTimestamp());
        }
    }

    @Override
    public void afterCommit() {
        firstConsensusTimestamp.set(EXECUTED);
    }
}
