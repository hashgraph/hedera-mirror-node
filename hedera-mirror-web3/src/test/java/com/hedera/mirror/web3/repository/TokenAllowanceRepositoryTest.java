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

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.entity.TokenAllowance;
import com.hedera.mirror.web3.Web3IntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class TokenAllowanceRepositoryTest extends Web3IntegrationTest {
    private final TokenAllowanceRepository repository;

    @Test
    void findAllowance() {
        final var tokenAllowance = domainBuilder.tokenAllowance().persist();

        assertThat(repository
                        .findById(tokenAllowance.getId())
                        .map(TokenAllowance::getAmount)
                        .orElse(0L))
                .isEqualTo(tokenAllowance.getAmount());
    }

    @Test
    void findByOwner() {
        long owner = 22L;
        final var tokenAllowance =
                domainBuilder.tokenAllowance().customize(a -> a.owner(owner)).persist();

        assertThat(repository.findByOwner(owner)).hasSize(1).contains(tokenAllowance);
    }

    @Test
    void findByOwnerAndTimestampLessThanBlockTimestamp() {
        final var allowance = domainBuilder.tokenAllowance().persist();

        assertThat(repository
                        .findByOwnerAndTimestamp(allowance.getOwner(), allowance.getTimestampLower() + 1)
                        .get(0))
                .isEqualTo(allowance);
    }

    @Test
    void findByOwnerAndTimestampEqualToBlockTimestamp() {
        final var allowance = domainBuilder.tokenAllowance().persist();

        assertThat(repository
                        .findByOwnerAndTimestamp(allowance.getOwner(), allowance.getTimestampLower())
                        .get(0))
                .isEqualTo(allowance);
    }

    @Test
    void findByOwnerAndTimestampGreaterThanBlockTimestamp() {
        final var allowance = domainBuilder.tokenAllowance().persist();

        assertThat(repository.findByOwnerAndTimestamp(allowance.getOwner(), allowance.getTimestampLower() - 1))
                .isEmpty();
    }

    @Test
    void findByOwnerAndTimestampHistoricalLessThanBlockTimestamp() {
        final var allowanceHistory = domainBuilder.tokenAllowanceHistory().persist();

        assertThat(repository
                        .findByOwnerAndTimestamp(allowanceHistory.getOwner(), allowanceHistory.getTimestampLower() + 1)
                        .get(0))
                .usingRecursiveComparison()
                .isEqualTo(allowanceHistory);
    }

    @Test
    void findByOwnerAndTimestampHistoricalEqualToBlockTimestamp() {
        final var allowanceHistory = domainBuilder.tokenAllowanceHistory().persist();

        assertThat(repository
                        .findByOwnerAndTimestamp(allowanceHistory.getOwner(), allowanceHistory.getTimestampLower())
                        .get(0))
                .usingRecursiveComparison()
                .isEqualTo(allowanceHistory);
    }

    @Test
    void findByOwnerAndTimestampHistoricalGreaterThanBlockTimestamp() {
        final var allowanceHistory = domainBuilder.tokenAllowanceHistory().persist();

        assertThat(repository.findByOwnerAndTimestamp(
                        allowanceHistory.getOwner(), allowanceHistory.getTimestampLower() - 1))
                .isEmpty();
    }

    @Test
    void findByOwnerAndTimestampHistoricalReturnsLatestEntry() {
        long owner = 1L;
        long tokenId = 2L;
        long spenderId = 3L;
        final var allowanceHistory1 = domainBuilder
                .tokenAllowanceHistory()
                .customize(a -> a.owner(owner).tokenId(tokenId).spender(spenderId))
                .persist();

        final var allowanceHistory2 = domainBuilder
                .tokenAllowanceHistory()
                .customize(a -> a.owner(owner).tokenId(tokenId).spender(spenderId))
                .persist();

        final var latestTimestamp =
                Math.max(allowanceHistory1.getTimestampLower(), allowanceHistory2.getTimestampLower());

        assertThat(repository
                        .findByOwnerAndTimestamp(allowanceHistory1.getOwner(), latestTimestamp + 1)
                        .get(0))
                .returns(latestTimestamp, TokenAllowance::getTimestampLower);
    }
}
