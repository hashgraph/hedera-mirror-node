package com.hedera.mirror.importer.repository;

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

import static org.assertj.core.api.Assertions.assertThat;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.domain.Entities;
import com.hedera.mirror.importer.domain.Transaction;

public class TransactionRepositoryTest extends AbstractRepositoryTest {

    @Test
    void save() {
        Transaction transaction = transactionRepository.save(transaction());
        assertThat(transactionRepository.findById(transaction.getConsensusNs()))
                .get()
                .isEqualTo(transaction);
        assertThat(entityRepository.findById(transaction.getEntity().getId()))
                .get()
                .isEqualTo(transaction.getEntity());
    }

    private Transaction transaction() {
        Long nodeAccountId = insertAccountEntity().getId();
        Long payerAccountId = insertAccountEntity().getId();
        Entities entity = insertAccountEntity();

        Transaction transaction = new Transaction();
        transaction.setChargedTxFee(100L);
        transaction.setConsensusNs(10L);
        transaction.setEntity(entity);
        transaction.setInitialBalance(1000L);
        transaction.setMemo("transaction memo".getBytes());
        transaction.setNodeAccountId(nodeAccountId);
        transaction.setPayerAccountId(payerAccountId);
        transaction.setResult(ResponseCodeEnum.SUCCESS.getNumber());
        transaction.setType(TransactionBody.DataCase.CRYPTOCREATEACCOUNT.getNumber());
        transaction.setValidStartNs(20L);
        transaction.setValidDurationSeconds(11L);
        transaction.setMaxFee(33L);
        return transaction;
    }
}
