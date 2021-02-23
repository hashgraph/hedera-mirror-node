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

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Random;
import javax.annotation.Resource;

import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.domain.Entities;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.Transaction;

public abstract class AbstractRepositoryTest extends IntegrationTest {

    @Resource
    protected TransactionRepository transactionRepository;
    @Resource
    protected EntityRepository entityRepository;
    @Resource
    protected ContractResultRepository contractResultRepository;
    @Resource
    protected CryptoTransferRepository cryptoTransferRepository;
    @Resource
    protected LiveHashRepository liveHashRepository;
    @Resource
    protected FileDataRepository fileDataRepository;
    @Resource
    protected TransactionResultRepository transactionResultRepository;
    @Resource
    protected TransactionTypeRepository transactionTypeRepository;

    private Entities insertEntity(EntityTypeEnum entityType) {
        Random rand = new Random();

        EntityId entity = EntityId.of(rand.nextInt(10000), rand.nextInt(10000), rand.nextInt(10000), entityType);
        return entityRepository.save(entity.toEntity());
    }

    protected final EntityId insertAccountEntity() {
        return insertEntity(EntityTypeEnum.ACCOUNT).toEntityId();
    }

    protected final Transaction insertTransaction(String type) {
        long chargedTxFee = 100;
        long consensusNs = 10;
        long validStartNs = 20;
        EntityId entity = insertAccountEntity();

        Transaction transaction = new Transaction();
        transaction.setChargedTxFee(chargedTxFee);
        transaction.setConsensusNs(consensusNs);
        transaction.setEntityId(entity);
        transaction.setNodeAccountId(entity);
        transaction.setPayerAccountId(entity);
        transaction.setResult(ResponseCodeEnum.SUCCESS.getNumber());
        transaction.setType(TransactionBody.DataCase.valueOf(type).getNumber());
        transaction.setValidStartNs(validStartNs);
        transaction.setValidDurationSeconds(11L);
        transaction.setMaxFee(33L);

        transaction = transactionRepository.save(transaction);

        return transaction;
    }
}
