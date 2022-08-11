package com.hedera.mirror.web3.repository;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

import com.hedera.mirror.common.domain.contract.Contract;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.web3.ApiContractIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

import javax.annotation.Resource;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

import static com.hedera.mirror.web3.utils.TestConstants.contractId;
import static com.hedera.mirror.web3.utils.TestConstants.contractNum;
import static com.hedera.mirror.web3.utils.TestConstants.initByteCodeBytes;
import static com.hedera.mirror.web3.utils.TestConstants.runtimeByteCodeBytes;

public class ContractRepositoryTest extends ApiContractIntegrationTest {

    @Resource
    private ContractRepository contractRepository;

    @Test
    public void getContractRuntimeBytecode() {
        Contract contract = contractFile();
        contractRepository.save(contract);

        assertThat(contractRepository.findRuntimeBytecodeById(contractId)).get()
                .isEqualTo(Bytes.wrap(contract.getRuntimeBytecode()).toUnprefixedHexString());
    }

    private Contract contractFile(){
        Contract contract = new Contract();
        contract.setId(contractId);
        contract.setFileId(EntityId.of(0,0, contractNum, EntityType.CONTRACT));
        contract.setInitcode(initByteCodeBytes.toArray());
        contract.setRuntimeBytecode(runtimeByteCodeBytes.toArray());
        return contract;
    }
}
