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

import com.hedera.IntegrationTest;
import com.hedera.mirror.domain.ContractResult;
import com.hedera.mirror.domain.Entities;
import com.hedera.mirror.domain.RecordFile;
import com.hedera.mirror.domain.Transaction;
import com.hedera.mirror.domain.TransactionResult;
import com.hedera.mirror.domain.TransactionType;

import org.junit.jupiter.api.Test;

import javax.annotation.Resource;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ContractResultRepositoryTest extends IntegrationTest {

    @Resource
    private RecordFileRepository recordFileRepository;
    @Resource
    private EntityRepository entityRepository;
    @Resource
    private EntityTypeRepository entityTypeRepository;
    @Resource
    private TransactionResultRepository transactionResultRepository;
    @Resource
    private TransactionTypeRepository transactionTypeRepository;
    @Resource
    private TransactionRepository transactionRepository;
    @Resource
    private ContractResultRepository contractResultRepository;

    @Test
    void insertContractTest() {
    	
    	RecordFile recordFile = new RecordFile();
    	recordFile.setId(1L);
    	recordFile.setName("testfile");
    	recordFile = recordFileRepository.save(recordFile);

    	Entities entity = new Entities();
    	entity.setId(1L);
    	entity.setEntityShard(0L);
    	entity.setEntityRealm(0L);
    	entity.setEntityNum(1L);
    	entity.setEntityTypeId(entityTypeRepository.findByName("account").get().getId());
    	entity = entityRepository.save(entity);
    	
    	Transaction transaction = new Transaction();
    	transaction.setRecordFileId(recordFile.getId());
    	transaction.setChargedTxFee(100L);
    	transaction.setConsensusNs(10L);
    	transaction.setEntityId(entity.getId());
    	transaction.setNodeAccountId(entity.getId());
    	transaction.setPayerAccountId(entity.getId());
    	TransactionResult result = transactionResultRepository.findByResult("SUCCESS").get();
    	transaction.setResultId(result.getId());
    	TransactionType transactionType = transactionTypeRepository.findByName("CRYPTOTRANSFER").get();
    	transaction.setTransactionTypeId(transactionType.getId());
    	transaction.setValidStartNs(10L);

    	transactionRepository.save(transaction);

    	ContractResult contractResult = new ContractResult();
    	final byte[] callResult = "CallResult".getBytes();
    	final byte[] functionParameters = "functionParameters".getBytes();
    	final long gasSupplied = 200;
    	final long gasUsed = 100;
    	contractResult.setCallResult(callResult);
    	contractResult.setFunctionParameters(functionParameters);
    	contractResult.setGasSupplied(gasSupplied);
    	contractResult.setGasUsed(gasUsed);
    	contractResult.setConsensusTimestamp(transaction.getConsensusNs());
    	contractResultRepository.save(contractResult);
    	
    	ContractResult newContractResult = contractResultRepository.findById(transaction.getConsensusNs()).get();
    	
    	assertAll(
                () -> assertArrayEquals(callResult, newContractResult.getCallResult())
                ,() -> assertArrayEquals(functionParameters, newContractResult.getFunctionParameters())
                ,() -> assertEquals(gasSupplied, newContractResult.getGasSupplied())
                ,() -> assertEquals(gasUsed, newContractResult.getGasUsed())
        );
    }
}
