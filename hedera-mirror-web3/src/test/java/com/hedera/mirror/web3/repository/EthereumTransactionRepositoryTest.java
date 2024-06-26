/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.web3.Web3IntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class EthereumTransactionRepositoryTest extends Web3IntegrationTest {
    private final EthereumTransactionRepository ethereumTransactionRepository;

    @Test
    void findByConsensusTimestampSuccessful() {
        var ethereumTransaction = domainBuilder.ethereumTransaction(false).persist();
        assertThat(ethereumTransactionRepository.findById(ethereumTransaction.getConsensusTimestamp()))
                .contains(ethereumTransaction);
    }

    @Test
    void findByConsensusTimestampAndPayerAccountIdSuccessful() {
        var ethereumTransaction = domainBuilder.ethereumTransaction(false).persist();
        assertThat(ethereumTransactionRepository.findByConsensusTimestampAndPayerAccountId(
                        ethereumTransaction.getConsensusTimestamp(), ethereumTransaction.getPayerAccountId()))
                .contains(ethereumTransaction);
    }
}
