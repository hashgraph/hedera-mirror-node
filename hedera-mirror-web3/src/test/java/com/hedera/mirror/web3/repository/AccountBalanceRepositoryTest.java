/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import com.hedera.mirror.web3.Web3IntegrationTest;
import lombok.RequiredArgsConstructor;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class AccountBalanceRepositoryTest extends Web3IntegrationTest {

    private final AccountBalanceRepository accountBalanceRepository;

    @Test
    void findHistoricalByIdAndTimestampLessThanBlockTimestamp() {
        var accountBalance = domainBuilder.accountBalance().persist();
        Assertions.assertThat(accountBalanceRepository.findByIdAndTimestampLessThan(accountBalance.getId().getAccountId().getId(),
                        accountBalance.getId().getConsensusTimestamp() + 1))
                .get()
                .isEqualTo(accountBalance);
    }

    @Test
    void findHistoricalByIdAndTimestampEqualToBlockTimestamp() {
        var accountBalance = domainBuilder.accountBalance().persist();
        Assertions.assertThat(accountBalanceRepository.findByIdAndTimestampLessThan(accountBalance.getId().getAccountId().getId(),
                        accountBalance.getId().getConsensusTimestamp()))
                .isEmpty();
    }

    @Test
    void findHistoricalByIdAndTimestampGreaterThanBlockTimestamp() {
        var accountBalance = domainBuilder.accountBalance().persist();
        Assertions.assertThat(accountBalanceRepository.findByIdAndTimestampLessThan(accountBalance.getId().getAccountId().getId(),
                        accountBalance.getId().getConsensusTimestamp() - 1))
                .isEmpty();
    }
}
