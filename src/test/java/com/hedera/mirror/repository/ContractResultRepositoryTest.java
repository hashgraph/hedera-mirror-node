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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

public class ContractResultRepositoryTest extends AbstractRepositoryTest {

	ContractResult contractResult;

    @Test
    @Transactional
    void contractResultInsert() {
    	
    	RecordFile recordfile = insertRecordFile();
    	Entities entity = insertAccountEntity();
    	Transaction transaction = insertTransaction(recordfile.getId(), entity.getId(), "CONTRACTCALL");

    	contractResult = new ContractResult();
    	contractResult.setCallResult("CallResult".getBytes());
    	contractResult.setFunctionParameters("functionParameters".getBytes());
    	contractResult.setGasSupplied(200L);
    	contractResult.setGasUsed(100L);
    	contractResult.setConsensusTimestamp(transaction.getConsensusNs());
    	contractResult = contractResultRepository.save(contractResult);
    	
    	assertThat(contractResultRepository.findById(transaction.getConsensusNs()).get())
        	.isNotNull()
        	.isEqualTo(contractResult);
    }
}
