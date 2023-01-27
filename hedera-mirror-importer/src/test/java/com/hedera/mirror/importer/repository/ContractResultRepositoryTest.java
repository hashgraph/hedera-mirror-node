package com.hedera.mirror.importer.repository;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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
import org.springframework.beans.factory.annotation.Autowired;

import com.hedera.mirror.common.domain.contract.ContractResult;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class ContractResultRepositoryTest extends AbstractRepositoryTest {

    private final ContractResultRepository contractResultRepository;

    @Test
    void prune() {
        domainBuilder.contractResult().persist();
        var contractResult2 = domainBuilder.contractResult().persist();
        var contractResult3 = domainBuilder.contractResult().persist();

        contractResultRepository.prune(contractResult2.getConsensusTimestamp());

        assertThat(contractResultRepository.findAll()).containsExactly(contractResult3);
    }

    @Test
    void save() {
        ContractResult contractResult = domainBuilder.contractResult().persist();
        assertThat(contractResultRepository.findById(contractResult.getId())).get().isEqualTo(contractResult);
    }
}
