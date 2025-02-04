/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
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

import com.hedera.mirror.importer.ImporterIntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class ContractTransactionRepositoryTest extends ImporterIntegrationTest {
    private final ContractTransactionRepository contractTransactionRepository;

    @Test
    void prune() {
        domainBuilder.contractTransaction().persist();
        var deletePoint = domainBuilder.contractTransaction().persist();
        var keptTransaction = domainBuilder.contractTransaction().persist();

        contractTransactionRepository.prune(deletePoint.getConsensusTimestamp());
        assertThat(contractTransactionRepository.findAll()).containsExactly(keptTransaction);
    }

    @Test
    void save() {
        var contractTransaction = domainBuilder.contractTransaction().persist();
        assertThat(contractTransactionRepository.findById(contractTransaction.getId()))
                .get()
                .isEqualTo(contractTransaction);
    }
}
