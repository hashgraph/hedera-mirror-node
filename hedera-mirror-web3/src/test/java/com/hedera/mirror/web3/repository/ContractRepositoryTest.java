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

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.common.domain.contract.Contract;
import com.hedera.mirror.web3.Web3IntegrationTest;

import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class ContractRepositoryTest extends Web3IntegrationTest {
    private Long contractId = 78L;
    private static final byte[] RUNTIME_CODE = new byte[20];

    private ContractRepository contractRepository;

    @Test
    void findRuntimeBytecodeSuccessfulCall() {
        contractRepository.save(contract());
        final var result = contractRepository.findRuntimeBytecode(contractId);
        assertThat(result).get().isEqualTo(RUNTIME_CODE);
    }

    private Contract contract() {
        Contract contract = new Contract();
        contract.setId(++contractId);
        contract.setRuntimeBytecode(RUNTIME_CODE);
        return contract;
    }
}
