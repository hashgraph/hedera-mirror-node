package com.hedera.faker.domain.generators.transaction;
/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.inject.Named;

import lombok.extern.log4j.Log4j2;

import com.hedera.faker.common.CryptoTransactionProperties;
import com.hedera.faker.common.EntityManager;
import com.hedera.faker.common.TransactionGenerator;
import com.hedera.faker.domain.writer.DomainWriter;
import com.hedera.faker.sampling.Distribution;
import com.hedera.faker.sampling.FrequencyDistribution;
import com.hedera.mirror.importer.domain.CryptoTransfer;
import com.hedera.mirror.importer.domain.Transaction;

/**
 * Generates crypto transactions (CRYPTOCREATEACCOUNT, CRYPTOUPDATEACCOUNT, CRYPTOTRANSFER, CRYPTODELETE).
 */
@Named
@Log4j2
public class CryptoTransactionGenerator implements TransactionGenerator {
    private final int RESULT_SUCCESS = 22;
    private final byte[] MEMO = new byte[] {0b0, 0b1, 0b01, 0b10, 0b11}; // TODO: change size to avg. size seen in prod
    private final int CRYPTO_TRANSFER_AMOUNT = 1000;

    private final CryptoTransactionProperties properties;
    private final EntityManager entityManager;
    private final DomainWriter domainWriter;
    private final Distribution<Consumer<Transaction>> transactionType;
    private int numTransactionsGenerated;

    public CryptoTransactionGenerator(
            CryptoTransactionProperties properties, EntityManager entityManager, DomainWriter domainWriter) {
        this.properties = properties;
        this.entityManager = entityManager;
        this.domainWriter = domainWriter;
        numTransactionsGenerated = 0;

        Map<Consumer<Transaction>, Integer> transactionTypeDistribution = Map.of(
                this::createAccount, this.properties.getCreatesPerThousand(),
                this::transfer, this.properties.getTransfersPerThousand(),
                this::updateAccount, this.properties.getUpdatesPerThousand(),
                this::deleteAccount, this.properties.getDeletesPerThousand()
        );
        transactionType = new FrequencyDistribution<>(transactionTypeDistribution);
    }

    @Override
    public void generateTransaction(long consensusTimestampNs) {
        Transaction transaction = new Transaction();
        transaction.setConsensusNs(consensusTimestampNs);
        transaction.setNodeAccountId(entityManager.getNodeAccountId());
        transaction.setResult(RESULT_SUCCESS);
        transaction.setChargedTxFee(100_000L); // Note: place holder value only. Doesn't affect balances.
        // set to fixed 10 sec before consensus time
        transaction.setValidStartNs(consensusTimestampNs - 10_000_000_000L);
        transaction.setValidDurationSeconds(120L);
        transaction.setMaxFee(1_000_000L);
        transaction.setMemo(MEMO);

        if (numTransactionsGenerated < properties.getNumSeedAccounts()) {
            createAccount(transaction);
        } else {
            transactionType.sample().accept(transaction);
        }
        domainWriter.addTransaction(transaction);
        numTransactionsGenerated++;
    }

    private void createAccount(Transaction transaction) {
        long value = 1_000_000_000L;
        transaction.setInitialBalance(value);
        transaction.setType(11);  // 11 = CRYPTOCREATEACCOUNT
        Long newAccountId = entityManager.getAccounts().newEntity();
        transaction.setEntityId(newAccountId);
        transaction.setPayerAccountId(entityManager.getPortalEntity());
        domainWriter.addCryptoTransfer(createCryptoTransfer(
                transaction.getConsensusNs(), entityManager.getPortalEntity(), -value));
        domainWriter.addCryptoTransfer(createCryptoTransfer(
                transaction.getConsensusNs(), newAccountId, value));
        log.trace("CRYPTOCREATEACCOUNT transaction: entity {}", newAccountId);
    }

    private void updateAccount(Transaction transaction) {
        transaction.setInitialBalance(0L);
        transaction.setType(15);  // 15 = CRYPTOUPDATEACCOUNT
        Long accountId = entityManager.getAccounts().getRandom();
        transaction.setEntityId(accountId);
        transaction.setPayerAccountId(accountId);
        log.trace("CRYPTOUPDATEACCOUNT transaction: entity {}", accountId);
    }

    private void transfer(Transaction transaction) {
        transaction.setInitialBalance(0L);
        transaction.setType(14);  // 14 = CRYPTOTRANSFER

        long numTransferLists = properties.getNumTransferLists().sample();
        // first account is sender, rest are receivers
        List<Long> accountIds = entityManager.getAccounts().getRandom((int) numTransferLists + 1);

        long totalValue = 0;
        for (int i = 0; i < numTransferLists; i++) {
            domainWriter.addCryptoTransfer(createCryptoTransfer(
                    transaction.getConsensusNs(), accountIds.get(i + 1), CRYPTO_TRANSFER_AMOUNT));
            totalValue += CRYPTO_TRANSFER_AMOUNT;
        }
        domainWriter.addCryptoTransfer(createCryptoTransfer(
                transaction.getConsensusNs(), accountIds.get(0), -totalValue));
        transaction.setPayerAccountId(accountIds.get(0));
        log.trace("CRYPTOTRANSFER transaction: num transfer lists {}", numTransferLists);
    }

    private void deleteAccount(Transaction transaction) {
        transaction.setInitialBalance(0L);
        transaction.setType(12);  // 12 = CRYPTODELETE
        Long accountId = entityManager.getAccounts().getRandom();
        entityManager.getAccounts().delete(accountId);
        transaction.setEntityId(accountId);
        transaction.setPayerAccountId(accountId); // TODO: is payer account different or same as entity being deleted?
        log.trace("CRYPTODELETE transaction: entity {}", accountId);
    }

    private CryptoTransfer createCryptoTransfer(long consensusNs, long accountId, long value) {
        CryptoTransfer cryptoTransfer = new CryptoTransfer();
        cryptoTransfer.setConsensusTimestamp(consensusNs);
        cryptoTransfer.setRealmNum(0L);
        cryptoTransfer.setEntityNum(accountId);
        cryptoTransfer.setAmount(value);
        entityManager.addBalance(accountId, value);
        return cryptoTransfer;
    }
}
