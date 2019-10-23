package com.hedera.mirror.repository;

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

import java.util.Random;

import javax.annotation.Resource;

import com.hedera.IntegrationTest;
import com.hedera.mirror.domain.Entities;
import com.hedera.mirror.domain.RecordFile;
import com.hedera.mirror.domain.Transaction;
import com.hedera.mirror.domain.TransactionResult;
import com.hedera.mirror.domain.TransactionType;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public abstract class AbstractRepositoryTest extends IntegrationTest {
	
    @Resource
    protected TransactionRepository transactionRepository;
    @Resource
    protected EntityRepository entityRepository;
    @Resource
    protected ContractResultRepository contractResultRepository;
    @Resource
    protected RecordFileRepository recordFileRepository;
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
    @Resource
    protected EntityTypeRepository entityTypeRepository;

    private enum EntityType {
    	account
    	,file
    	,contract
    }

    protected final RecordFile insertRecordFile() {
		final String fileName = "testfile";
		RecordFile recordFile = new RecordFile();
		recordFile.setName(fileName);
		recordFile = recordFileRepository.save(recordFile);

		return recordFile;
    }

    private Entities insertEntity(EntityType entityType) {
		Random rand = new Random();

		Entities entity = new Entities();
    	entity.setEntityShard((long)rand.nextInt(10000));
    	entity.setEntityRealm((long)rand.nextInt(10000));
    	entity.setEntityNum((long)rand.nextInt(10000));

    	entity.setEntityTypeId(entityTypeRepository.findByName(entityType.name()).get().getId());
    	entity = entityRepository.save(entity);

    	return entity;
    }

    protected final Entities insertAccountEntity() {
    	return insertEntity(EntityType.account);
    }

    protected final Entities insertFileEntity() {
    	return insertEntity(EntityType.account);
    }

    protected final Entities insertContractEntity() {
    	return insertEntity(EntityType.account);
    }

    protected final Transaction insertTransaction(long recordFileId, long entityId, String type) {
    	final long chargedTxFee = 100;
    	final long consensusNs = 10;
    	final long validStartNs = 20;

    	Transaction transaction = new Transaction();
    	transaction.setRecordFileId(recordFileId);
    	transaction.setChargedTxFee(chargedTxFee);
    	transaction.setConsensusNs(consensusNs);
    	transaction.setEntityId(entityId);
    	transaction.setNodeAccountId(entityId);
    	transaction.setPayerAccountId(entityId);
    	TransactionResult result = transactionResultRepository.findByResult("SUCCESS").get();
    	transaction.setResultId(result.getId());
    	TransactionType transactionType = transactionTypeRepository.findByName(type).get();
    	transaction.setTransactionTypeId(transactionType.getId());
    	transaction.setValidStartNs(validStartNs);

    	transaction = transactionRepository.save(transaction);

    	return transaction;
    }
}
