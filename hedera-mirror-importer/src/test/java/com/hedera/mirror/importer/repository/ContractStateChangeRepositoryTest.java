/*
 * Copyright (C) 2019-2025 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.contract.ContractStateChange;
import com.hedera.mirror.importer.ImporterIntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class ContractStateChangeRepositoryTest extends ImporterIntegrationTest {

    private final ContractStateChangeRepository contractStateChangeRepository;

    @Test
    void prune() {
        domainBuilder.contractStateChange().persist();
        var contractStateChange2 = domainBuilder.contractStateChange().persist();
        var contractStateChange3 = domainBuilder.contractStateChange().persist();

        contractStateChangeRepository.prune(contractStateChange2.getConsensusTimestamp());

        assertThat(contractStateChangeRepository.findAll()).containsExactly(contractStateChange3);
    }

    @Test
    void save() {
        ContractStateChange contractStateChange =
                domainBuilder.contractStateChange().persist();
        assertThat(contractStateChangeRepository.findById(contractStateChange.getId()))
                .get()
                .isEqualTo(contractStateChange);
    }
}
