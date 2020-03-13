package com.hedera.mirror.importer.repository;

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

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.domain.ContractResult;
import com.hedera.mirror.importer.domain.Entities;
import com.hedera.mirror.importer.domain.Transaction;

public class ContractResultRepositoryTest extends AbstractRepositoryTest {

    @Test
    void insert() {
        Entities entity = insertAccountEntity();
        Transaction transaction = insertTransaction(entity, "CONTRACTCALL");

        ContractResult contractResult = new ContractResult();
        contractResult.setCallResult("CallResult".getBytes());
        contractResult.setFunctionParameters("functionParameters".getBytes());
        contractResult.setGasSupplied(200L);
        contractResult.setGasUsed(100L);
        contractResult.setConsensusTimestamp(transaction.getConsensusNs());
        contractResult = contractResultRepository.save(contractResult);

        Assertions.assertThat(contractResultRepository.findById(transaction.getConsensusNs()).get())
                .isNotNull()
                .isEqualTo(contractResult);
    }
}
