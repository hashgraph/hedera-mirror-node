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

import com.hedera.mirror.importer.ImporterIntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class TransactionRepositoryTest extends ImporterIntegrationTest {

    private final TransactionRepository transactionRepository;

    @Test
    void prune() {
        domainBuilder.transaction().persist();
        var transaction2 = domainBuilder.transaction().persist();
        var transaction3 = domainBuilder.transaction().persist();

        transactionRepository.prune(transaction2.getConsensusTimestamp());

        assertThat(transactionRepository.findAll()).containsExactly(transaction3);
    }

    @Test
    void save() {
        var transaction = domainBuilder.transaction().get();
        transactionRepository.save(transaction);
        assertThat(transactionRepository.findById(transaction.getConsensusTimestamp()))
                .get()
                .isEqualTo(transaction);

        var t2 = domainBuilder
                .transaction()
                .customize(t -> t.maxCustomFees(null))
                .get();
        transactionRepository.save(t2);
        var actual = transactionRepository.findById(t2.getConsensusTimestamp());
        assertThat(actual).contains(t2);
    }
}
