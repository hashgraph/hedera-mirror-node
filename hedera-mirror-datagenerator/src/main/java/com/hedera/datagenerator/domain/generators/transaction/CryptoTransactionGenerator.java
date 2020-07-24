package com.hedera.datagenerator.domain.generators.transaction;
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

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.inject.Named;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import com.hedera.datagenerator.common.CryptoTransactionProperties;
import com.hedera.datagenerator.common.EntityManager;
import com.hedera.datagenerator.common.TransactionGenerator;
import com.hedera.datagenerator.sampling.Distribution;
import com.hedera.datagenerator.sampling.FrequencyDistribution;
import com.hedera.mirror.importer.domain.CryptoTransfer;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.Transaction;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;

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
            CryptoTransactionProperties properties, EntityManager entityManager, EntityListener entityListener) {
        super(entityManager, entityListener, properties.getNumSeedAccounts());
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
        final long value = 1_000_000_000L;
        transaction.setInitialBalance(value);
        transaction.setType(11);  // 11 = CRYPTOCREATEACCOUNT
        EntityId newAccount = entityManager.getAccounts().newEntity();
        transaction.setEntityId(newAccount);
        transaction.setPayerAccountId(entityManager.getPortalEntity());
        entityListener.onCryptoTransfer(createCryptoTransfer(
                transaction.getConsensusNs(), entityManager.getPortalEntity(), -value));
        entityListener.onCryptoTransfer(createCryptoTransfer(
                transaction.getConsensusNs(), newAccount, value));
        log.trace("CRYPTOCREATEACCOUNT transaction: entity {}", newAccount);
    }

    private void updateAccount(Transaction transaction) {
        transaction.setInitialBalance(0L);
        transaction.setType(15);  // 15 = CRYPTOUPDATEACCOUNT
        EntityId account = entityManager.getAccounts().getRandomEntity();
        transaction.setEntityId(account);
        transaction.setPayerAccountId(account);
        log.trace("CRYPTOUPDATEACCOUNT transaction: entity {}", account);
    }

    private void transfer(Transaction transaction) {
        transaction.setInitialBalance(0L);
        transaction.setType(14);  // 14 = CRYPTOTRANSFER

        long numTransferLists = properties.getNumTransferLists().sample();
        // first account is sender, rest are receivers
        List<EntityId> accountIds = entityManager.getAccounts().getRandomEntityNum((int) numTransferLists + 1);

        final long singleTransferAmount = 1_000L;
        for (int i = 0; i < numTransferLists; i++) {
            entityListener.onCryptoTransfer(
                    createCryptoTransfer(transaction.getConsensusNs(), accountIds.get(i + 1), singleTransferAmount));
        }
        entityListener.onCryptoTransfer(createCryptoTransfer(
                transaction.getConsensusNs(), accountIds.get(0), -1 * singleTransferAmount * numTransferLists));
        transaction.setPayerAccountId(accountIds.get(0));
        log.trace("CRYPTOTRANSFER transaction: num transfer lists {}", numTransferLists);
    }

    private void deleteAccount(Transaction transaction) {
        transaction.setInitialBalance(0L);
        transaction.setType(12);  // 12 = CRYPTODELETE
        EntityId account = entityManager.getAccounts().getRandomEntity();
        entityManager.getAccounts().delete(account);
        transaction.setEntityId(account);
        transaction.setPayerAccountId(account);
        log.trace("CRYPTODELETE transaction: entity {}", account);
    }

    private CryptoTransfer createCryptoTransfer(long consensusNs, EntityId account, long value) {
        entityManager.addBalance(account, value);
        return new CryptoTransfer(consensusNs, value, account);
    }
}
