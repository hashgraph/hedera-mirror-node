package com.hedera.mirror.importer.repository;

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

import static org.assertj.core.api.Assertions.assertThat;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.Collection;
import java.util.List;
import javax.annotation.Resource;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.Transaction;
import com.hedera.mirror.importer.domain.TransactionTypeEnum;

public class TransactionRepositoryTest extends AbstractRepositoryTest {

    @Resource
    private TransactionRepository transactionRepository;

    private long count = 0;

    @Test
    void save() {
        Transaction transaction = transactionRepository.save(transaction());
        assertThat(transactionRepository.findById(transaction.getConsensusNs())).get().isEqualTo(transaction);
    }

    @Test
    void existsByEntityIdAndTypeIn() {
        Transaction transaction = transaction();
        Collection<Integer> types = List.of(transaction.getType());

        Transaction transactionWrongType = transaction();
        transactionWrongType.setType(-1);

        Transaction transactionWrongEntityId = transaction();
        transactionWrongEntityId.setEntityId(EntityId.of(1, 1, 1, EntityTypeEnum.ACCOUNT));
        transactionRepository.saveAll(List.of(transactionWrongType, transactionWrongEntityId));

        assertThat(transactionRepository.existsByEntityIdAndTypeIn(transaction.getEntityId(), types)).isFalse();

        transactionRepository.save(transaction);

        assertThat(transactionRepository.existsByEntityIdAndTypeIn(transaction.getEntityId(), types)).isTrue();
    }

    private Transaction transaction() {
        Transaction transaction = new Transaction();
        transaction.setChargedTxFee(100L);
        transaction.setConsensusNs(++count);
        transaction.setEntityId(EntityId.of(0, 0, 1, EntityTypeEnum.ACCOUNT));
        transaction.setInitialBalance(1000L);
        transaction.setMemo("transaction memo".getBytes());
        transaction.setNodeAccountId(EntityId.of(0, 0, 2, EntityTypeEnum.ACCOUNT));
        transaction.setPayerAccountId(EntityId.of(0, 0, 3, EntityTypeEnum.ACCOUNT));
        transaction.setResult(ResponseCodeEnum.SUCCESS.getNumber());
        transaction.setType(TransactionTypeEnum.CRYPTOCREATEACCOUNT.getProtoId());
        transaction.setValidStartNs(20L);
        transaction.setValidDurationSeconds(11L);
        transaction.setMaxFee(33L);
        return transaction;
    }
}
