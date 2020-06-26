package com.hedera.datagenerator.common;
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

import java.util.function.Consumer;

import com.hedera.datagenerator.sampling.Distribution;
import com.hedera.mirror.importer.domain.Transaction;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;

public abstract class TransactionGenerator {

    private static final int RESULT_SUCCESS = 22;
    private static final byte[] MEMO = new byte[] {0b0, 0b1, 0b01, 0b10, 0b11};

    protected final EntityManager entityManager;
    protected final EntityListener entityListener;
    private final int numSeedEntities;
    private int numTransactionsGenerated;

    protected TransactionGenerator(EntityManager entityManager, EntityListener entityListener, int numSeedEntities) {
        this.entityManager = entityManager;
        this.entityListener = entityListener;
        this.numSeedEntities = numSeedEntities;
        numTransactionsGenerated = 0;
    }

    public void generateTransaction(long consensusTimestampNs) {
        Transaction transaction = new Transaction();
        // Set default value for fields
        transaction.setConsensusNs(consensusTimestampNs);
        transaction.setNodeAccountId(entityManager.getNodeAccount());
        transaction.setResult(RESULT_SUCCESS);
        transaction.setChargedTxFee(100_000L); // Note: place holder value only. Doesn't affect balances.
        // set to fixed 10 sec before consensus time
        transaction.setValidStartNs(consensusTimestampNs - 10_000_000_000L);
        transaction.setValidDurationSeconds(120L);
        transaction.setMaxFee(1_000_000L);
        transaction.setInitialBalance(0L);
        transaction.setPayerAccountId(entityManager.getAccounts().getRandomEntity());
        transaction.setMemo(MEMO);

        if (numTransactionsGenerated < numSeedEntities) {
            seedEntity(transaction);
        } else {
            getTransactionDistribution().sample().accept(transaction);
        }
        entityListener.onTransaction(transaction);
        numTransactionsGenerated++;
    }

    protected abstract void seedEntity(Transaction transaction);

    protected abstract Distribution<Consumer<Transaction>> getTransactionDistribution();
}
