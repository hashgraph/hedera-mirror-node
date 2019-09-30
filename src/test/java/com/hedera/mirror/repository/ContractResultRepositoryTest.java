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

import com.hedera.mirror.domain.ContractResult;
import com.hedera.mirror.domain.Entities;
import com.hedera.mirror.domain.RecordFile;
import com.hedera.mirror.domain.Transaction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ContractResultRepositoryTest extends AbstractRepositoryTest {

    @Test
    void insert() {
    	
    	final byte[] callResult = "CallResult".getBytes();
    	final byte[] functionParameters = "functionParameters".getBytes();
    	final long gasSupplied = 200;
    	final long gasUsed = 100;

    	RecordFile recordfile = insertRecordFile();
    	Entities entity = insertAccountEntity(1, 0, 0, 1);
    	Transaction transaction = insertTransaction(recordfile.getId(), entity.getId(), "CONTRACTCALL");

    	ContractResult contractResult = new ContractResult();
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
