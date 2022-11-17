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

import com.hedera.mirror.common.domain.contract.ContractState;
import com.hedera.mirror.web3.Web3IntegrationTest;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class ContractStateRepositoryTest extends Web3IntegrationTest {

    private Long contractId = 78L;
    private static final byte[] KEY_VALUE = new byte[32];

    private ContractStateRepository contractStateRepository;

    @Test
    void findStorageSuccessfulCall() {
        contractStateRepository.save(contractState());
        final var result = contractStateRepository.findStorage(contractId, KEY_VALUE);
        assertThat(result).get().isEqualTo(KEY_VALUE);
    }

    private ContractState contractState() {
        ContractState contractState = new ContractState();
        contractState.setContractId(++contractId);
        contractState.setSlot(KEY_VALUE);
        contractState.setValue(KEY_VALUE);
        return contractState;
    }
}
