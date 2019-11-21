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

import lombok.Getter;
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
public class CryptoTransactionGenerator extends TransactionGenerator {

    private final CryptoTransactionProperties properties;
    @Getter
    private final Distribution<Consumer<Transaction>> transactionDistribution;

    public CryptoTransactionGenerator(
            CryptoTransactionProperties properties, EntityManager entityManager, DomainWriter domainWriter) {
        super(entityManager, domainWriter, properties.getNumSeedAccounts());
        this.properties = properties;
        transactionDistribution = new FrequencyDistribution<>(Map.of(
                this::createAccount, this.properties.getCreatesFrequency(),
                this::transfer, this.properties.getTransfersFrequency(),
                this::updateAccount, this.properties.getUpdatesFrequency(),
                this::deleteAccount, this.properties.getDeletesFrequency()
        ));
    }

    @Override
    public void seedEntity(Transaction transaction) {
        createAccount(transaction);
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
        int singleTransferAmount = 1000;
        for (int i = 0; i < numTransferLists; i++) {
            domainWriter.addCryptoTransfer(createCryptoTransfer(
                    transaction.getConsensusNs(), accountIds.get(i + 1), singleTransferAmount));
            totalValue += singleTransferAmount;
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
