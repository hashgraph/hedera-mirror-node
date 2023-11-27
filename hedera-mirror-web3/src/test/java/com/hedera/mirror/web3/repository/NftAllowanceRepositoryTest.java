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

import com.hedera.mirror.common.domain.entity.AbstractNftAllowance;
import com.hedera.mirror.common.domain.entity.NftAllowance;
import com.hedera.mirror.web3.Web3IntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class NftAllowanceRepositoryTest extends Web3IntegrationTest {
    private final NftAllowanceRepository allowanceRepository;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void spenderHasApproveForAll(boolean isApproveForAll) {
        final var allowance = domainBuilder
                .nftAllowance()
                .customize(a -> a.approvedForAll(isApproveForAll))
                .persist();

        assertThat(allowanceRepository
                        .findById(allowance.getId())
                        .map(NftAllowance::isApprovedForAll)
                        .orElse(false))
                .isEqualTo(isApproveForAll);
    }

    @Test
    void findBySpenderAndApprovedForAllIsTrue() {
        final var allowance = domainBuilder
                .nftAllowance()
                .customize(a -> a.approvedForAll(true))
                .persist();
        assertThat(allowanceRepository.findByOwnerAndApprovedForAllIsTrue(allowance.getOwner()))
                .hasSize(1)
                .contains(allowance);
    }

    @Test
    void noMatchingRecord() {
        assertThat(allowanceRepository
                        .findById(new AbstractNftAllowance.Id())
                        .map(NftAllowance::isApprovedForAll)
                        .orElse(false))
                .isFalse();
    }

    @Test
    void findByTimestampAndOwnerAndApprovedForAllIsTrueGreaterThanBlockTimestamp() {
        final var allowance = domainBuilder
                .nftAllowance()
                .customize(e -> e.approvedForAll(true))
                .persist();

        assertThat(allowanceRepository.findByOwnerAndTimestampAndApprovedForAllIsTrue(
                        allowance.getId().getOwner(), allowance.getTimestampLower() - 1))
                .isEmpty();
    }

    @Test
    void findByTimestampAndOwnerAndApprovedForAllIsFalseGreaterThanBlockTimestamp() {
        final var allowance = domainBuilder.nftAllowance().persist();

        assertThat(allowanceRepository.findByOwnerAndTimestampAndApprovedForAllIsTrue(
                        allowance.getId().getOwner(), allowance.getTimestampLower() - 1))
                .isEmpty();
    }

    @Test
    void findByTimestampAndOwnerAndApprovedForAllIsTrueEqualToBlockTimestamp() {
        final var allowance = domainBuilder
                .nftAllowance()
                .customize(e -> e.approvedForAll(true))
                .persist();

        assertThat(allowanceRepository
                        .findByOwnerAndTimestampAndApprovedForAllIsTrue(
                                allowance.getId().getOwner(), allowance.getTimestampLower())
                        .get(0))
                .isEqualTo(allowance);
    }

    @Test
    void findByTimestampAndOwnerAndApprovedForAllIsTrueLessThanBlockTimestamp() {
        final var allowance = domainBuilder
                .nftAllowance()
                .customize(e -> e.approvedForAll(true))
                .persist();

        assertThat(allowanceRepository
                        .findByOwnerAndTimestampAndApprovedForAllIsTrue(
                                allowance.getId().getOwner(), allowance.getTimestampLower() + 1)
                        .get(0))
                .isEqualTo(allowance);
    }

    @Test
    void findByTimestampAndOwnerAndApprovedForAllIsFalseLessThanBlockTimestamp() {
        final var allowance = domainBuilder.nftAllowance().persist();

        assertThat(allowanceRepository.findByOwnerAndTimestampAndApprovedForAllIsTrue(
                        allowance.getId().getOwner(), allowance.getTimestampLower() + 1))
                .isEmpty();
    }

    @Test
    void findByTimestampAndOwnerAndApprovedForAllIsTrueHistoricalLessThanBlockTimestamp() {
        final var allowanceHistory = domainBuilder
                .nftAllowanceHistory()
                .customize(a -> a.approvedForAll(true))
                .persist();

        assertThat(allowanceRepository
                        .findByOwnerAndTimestampAndApprovedForAllIsTrue(
                                allowanceHistory.getId().getOwner(), allowanceHistory.getTimestampLower() + 1)
                        .get(0))
                .usingRecursiveComparison()
                .isEqualTo(allowanceHistory);
    }

    @Test
    void findByTimestampAndOwnerAndApprovedForAllIsFalseHistoricalLessThanBlockTimestamp() {
        final var allowanceHistory = domainBuilder
                .nftAllowanceHistory()
                .customize(a -> a.approvedForAll(true))
                .persist();

        assertThat(allowanceRepository
                        .findByOwnerAndTimestampAndApprovedForAllIsTrue(
                                allowanceHistory.getId().getOwner(), allowanceHistory.getTimestampLower() + 1)
                        .get(0))
                .usingRecursiveComparison()
                .isEqualTo(allowanceHistory);
    }

    @Test
    void findByTimestampAndOwnerAndApprovedForAllIsTrueHistoricalEqualToBlockTimestamp() {
        final var allowanceHistory = domainBuilder
                .nftAllowanceHistory()
                .customize(a -> a.approvedForAll(true))
                .persist();

        assertThat(allowanceRepository
                        .findByOwnerAndTimestampAndApprovedForAllIsTrue(
                                allowanceHistory.getId().getOwner(), allowanceHistory.getTimestampLower())
                        .get(0))
                .usingRecursiveComparison()
                .isEqualTo(allowanceHistory);
    }

    @Test
    void findByTimestampAndOwnerAndApprovedForAllIsTrueHistoricalGreaterThanBlockTimestamp() {
        final var allowanceHistory = domainBuilder
                .nftAllowanceHistory()
                .customize(a -> a.approvedForAll(true))
                .persist();

        assertThat(allowanceRepository.findByOwnerAndTimestampAndApprovedForAllIsTrue(
                        allowanceHistory.getId().getOwner(), allowanceHistory.getTimestampLower() - 1))
                .isEmpty();
    }

    @Test
    void findByTimestampAndOwnerAndApprovedForAllIsTrueHistoricalReturnsLatestEntry() {
        long tokenId = 1L;
        long owner = 2L;
        long spender = 3L;
        final var allowanceHistory1 = domainBuilder
                .nftAllowanceHistory()
                .customize(a -> a.tokenId(tokenId).owner(owner).spender(spender).approvedForAll(true))
                .persist();

        final var allowanceHistory2 = domainBuilder
                .nftAllowanceHistory()
                .customize(a -> a.tokenId(tokenId).owner(owner).spender(spender).approvedForAll(true))
                .persist();

        final var latestTimestamp =
                Math.max(allowanceHistory1.getTimestampLower(), allowanceHistory2.getTimestampLower());

        NftAllowance actualAllowance = allowanceRepository
                .findByOwnerAndTimestampAndApprovedForAllIsTrue(
                        allowanceHistory1.getId().getOwner(), latestTimestamp + 1)
                .get(0);

        assertThat(actualAllowance).usingRecursiveComparison().isEqualTo(allowanceHistory2);
        assertThat(actualAllowance.getTimestampLower()).isEqualTo(latestTimestamp);
    }

    @Test
    void findByIdAndTimestampGreaterThanBlockTimestamp() {
        final var allowance = domainBuilder.nftAllowance().persist();

        assertThat(allowanceRepository.findByIdAndTimestamp(
                        allowance.getOwner(),
                        allowance.getId().getSpender(),
                        allowance.getTokenId(),
                        allowance.getTimestampLower() - 1))
                .isEmpty();
    }

    @Test
    void findByIdAndTimestampEqualToBlockTimestamp() {
        final var allowance = domainBuilder
                .nftAllowance()
                .customize(e -> e.approvedForAll(true))
                .persist();

        assertThat(allowanceRepository.findByIdAndTimestamp(
                        allowance.getOwner(),
                        allowance.getId().getSpender(),
                        allowance.getTokenId(),
                        allowance.getTimestampLower()))
                .get()
                .isEqualTo(allowance);
    }

    @Test
    void findByIdAndTimestampLessThanBlockTimestamp() {
        final var allowance = domainBuilder.nftAllowance().persist();

        assertThat(allowanceRepository.findByIdAndTimestamp(
                        allowance.getOwner(),
                        allowance.getId().getSpender(),
                        allowance.getTokenId(),
                        allowance.getTimestampLower() + 1))
                .get()
                .isEqualTo(allowance);
    }

    @Test
    void findByIdAndTimestampHistoricalLessThanBlockTimestamp() {
        final var allowanceHistory = domainBuilder.nftAllowanceHistory().persist();

        assertThat(allowanceRepository.findByIdAndTimestamp(
                        allowanceHistory.getOwner(),
                        allowanceHistory.getId().getSpender(),
                        allowanceHistory.getTokenId(),
                        allowanceHistory.getTimestampLower() + 1))
                .get()
                .usingRecursiveComparison()
                .isEqualTo(allowanceHistory);
    }

    @Test
    void findByIdAndTimestampHistoricalEqualToBlockTimestamp() {
        final var allowanceHistory = domainBuilder.nftAllowanceHistory().persist();

        assertThat(allowanceRepository.findByIdAndTimestamp(
                        allowanceHistory.getOwner(),
                        allowanceHistory.getId().getSpender(),
                        allowanceHistory.getTokenId(),
                        allowanceHistory.getTimestampLower()))
                .get()
                .usingRecursiveComparison()
                .isEqualTo(allowanceHistory);
    }

    @Test
    void findByIdAndTimestampHistoricalGreaterThanBlockTimestamp() {
        final var allowanceHistory = domainBuilder
                .nftAllowanceHistory()
                .customize(a -> a.approvedForAll(true))
                .persist();

        assertThat(allowanceRepository.findByIdAndTimestamp(
                        allowanceHistory.getOwner(),
                        allowanceHistory.getId().getSpender(),
                        allowanceHistory.getTokenId(),
                        allowanceHistory.getTimestampLower() - 1))
                .isEmpty();
    }

    @Test
    void findByIdAndTimestampHistoricalReturnsLatestEntry() {
        long tokenId = 1L;
        long owner = 2L;
        long spender = 3L;
        final var allowanceHistory1 = domainBuilder
                .nftAllowanceHistory()
                .customize(a -> a.tokenId(tokenId).owner(owner).spender(spender).approvedForAll(true))
                .persist();

        final var allowanceHistory2 = domainBuilder
                .nftAllowanceHistory()
                .customize(a -> a.tokenId(tokenId).owner(owner).spender(spender).approvedForAll(true))
                .persist();

        final var latestTimestamp =
                Math.max(allowanceHistory1.getTimestampLower(), allowanceHistory2.getTimestampLower());

        NftAllowance actualAllowance = allowanceRepository
                .findByIdAndTimestamp(
                        allowanceHistory1.getOwner(),
                        allowanceHistory1.getId().getSpender(),
                        allowanceHistory1.getTokenId(),
                        latestTimestamp + 1)
                .get();

        assertThat(actualAllowance).usingRecursiveComparison().isEqualTo(allowanceHistory2);
        assertThat(actualAllowance.getTimestampLower()).isEqualTo(latestTimestamp);
    }
}
