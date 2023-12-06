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

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.TokenAllowance;
import com.hedera.mirror.common.domain.token.TokenTransfer;
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
    void findByIdAndTimestampGreaterThanBlockTimestamp() {
        final var allowance = domainBuilder.tokenAllowance().persist();

        assertThat(repository.findByOwnerSpenderTokenAndTimestamp(
                        allowance.getOwner(),
                        allowance.getId().getSpender(),
                        allowance.getTokenId(),
                        allowance.getTimestampLower() - 1))
                .isEmpty();
    }

    @Test
    void findByIdAndTimestampEqualToBlockTimestamp() {
        final var allowance = domainBuilder.tokenAllowance().persist();

        assertThat(repository.findByOwnerSpenderTokenAndTimestamp(
                        allowance.getOwner(),
                        allowance.getId().getSpender(),
                        allowance.getTokenId(),
                        allowance.getTimestampLower()))
                .get()
                .isEqualTo(allowance);
    }

    @Test
    void findByIdAndTimestampLessThanBlockTimestamp() {
        final var allowance = domainBuilder.tokenAllowance().persist();

        assertThat(repository.findByOwnerSpenderTokenAndTimestamp(
                        allowance.getOwner(),
                        allowance.getId().getSpender(),
                        allowance.getTokenId(),
                        allowance.getTimestampLower() + 1))
                .get()
                .isEqualTo(allowance);
    }

    @Test
    void findByIdAndTimestampHistoricalLessThanBlockTimestamp() {
        final var allowanceHistory = domainBuilder.tokenAllowanceHistory().persist();

        assertThat(repository.findByOwnerSpenderTokenAndTimestamp(
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
        final var allowanceHistory = domainBuilder.tokenAllowanceHistory().persist();

        assertThat(repository.findByOwnerSpenderTokenAndTimestamp(
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
        final var allowanceHistory = domainBuilder.tokenAllowanceHistory().persist();

        assertThat(repository.findByOwnerSpenderTokenAndTimestamp(
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
                .tokenAllowanceHistory()
                .customize(a -> a.tokenId(tokenId).owner(owner).spender(spender))
                .persist();

        final var allowanceHistory2 = domainBuilder
                .tokenAllowanceHistory()
                .customize(a -> a.tokenId(tokenId).owner(owner).spender(spender))
                .persist();

        final var latestTimestamp =
                Math.max(allowanceHistory1.getTimestampLower(), allowanceHistory2.getTimestampLower());

        TokenAllowance actualAllowance = repository
                .findByOwnerSpenderTokenAndTimestamp(
                        allowanceHistory1.getOwner(),
                        allowanceHistory1.getId().getSpender(),
                        allowanceHistory1.getTokenId(),
                        latestTimestamp + 1)
                .get();

        assertThat(actualAllowance).usingRecursiveComparison().isEqualTo(allowanceHistory2);
        assertThat(actualAllowance.getTimestampLower()).isEqualTo(latestTimestamp);
    }

    @Test
    void findByOwnerSpenderTokenAndTimestampWithContractTransferAfterBlockTimestamp() {
        long ownerId = 100L;
        long tokenId = 200L;
        long spenderId = 300L;
        long senderId = 400L;
        long tokenAllowanceTimestamp = System.currentTimeMillis();
        long blockTimestamp = tokenAllowanceTimestamp + 1;
        long tokenTransferTimestamp = tokenAllowanceTimestamp + 2;
        final long initialAmount = 3;
        final long amountForTransfer = -1;

        final var allowance = domainBuilder
                .tokenAllowance()
                .customize(a -> a.owner(ownerId)
                        .amountGranted(initialAmount)
                        .amount(initialAmount)
                        .tokenId(tokenId)
                        .spender(senderId)
                        .timestampRange(Range.atLeast(tokenAllowanceTimestamp)))
                .persist();

        domainBuilder
                .tokenTransfer()
                .customize(t -> t.isApproval(true)
                        .amount(amountForTransfer)
                        .payerAccountId(EntityId.of(spenderId))
                        .id(TokenTransfer.Id.builder()
                                .tokenId(EntityId.of(tokenId))
                                .accountId(EntityId.of(ownerId))
                                .consensusTimestamp(tokenTransferTimestamp)
                                .build()))
                .persist();

        domainBuilder
                .contractResult()
                .customize(c -> c.consensusTimestamp(tokenTransferTimestamp).senderId(EntityId.of(senderId)))
                .persist();

        var result = repository.findByOwnerSpenderTokenAndTimestamp(
                allowance.getOwner(), allowance.getSpender(), allowance.getTokenId(), blockTimestamp);
        assertThat(result.get().getAmount()).isEqualTo(initialAmount);
    }

    @Test
    void findByOwnerSpenderTokenAndTimestampWithRegularTokenTransfersAndContractTokenTransfers() {
        long ownerId = 100L;
        long tokenId = 200L;
        long spenderId = 300L;
        long senderId = 400L;
        long tokenAllowanceTimestamp = System.currentTimeMillis();
        long tokenTransferTimestamp = tokenAllowanceTimestamp + 100;
        long tokenTransfer1Timestamp = tokenAllowanceTimestamp + 200;
        long blockTimestamp = tokenAllowanceTimestamp + 300;
        final long initialAmount = 3;
        final long amountForTransfer = -1;

        final var allowance = domainBuilder
                .tokenAllowance()
                .customize(a -> a.owner(ownerId)
                        .amountGranted(initialAmount)
                        .amount(initialAmount)
                        .tokenId(tokenId)
                        .spender(senderId)
                        .timestampRange(Range.atLeast(tokenAllowanceTimestamp)))
                .persist();

        domainBuilder
                .tokenTransfer()
                .customize(t -> t.isApproval(true)
                        .amount(amountForTransfer)
                        .payerAccountId(EntityId.of(senderId))
                        .id(TokenTransfer.Id.builder()
                                .tokenId(EntityId.of(tokenId))
                                .accountId(EntityId.of(ownerId))
                                .consensusTimestamp(tokenTransferTimestamp)
                                .build()))
                .persist();

        domainBuilder
                .tokenTransfer()
                .customize(t -> t.isApproval(true)
                        .amount(amountForTransfer)
                        .payerAccountId(EntityId.of(senderId))
                        .id(TokenTransfer.Id.builder()
                                .tokenId(EntityId.of(tokenId))
                                .accountId(EntityId.of(ownerId))
                                .consensusTimestamp(tokenTransfer1Timestamp)
                                .build()))
                .persist();

        domainBuilder
                .contractResult()
                .customize(c -> c.consensusTimestamp(tokenTransfer1Timestamp).senderId(EntityId.of(senderId)))
                .persist();

        var result = repository.findByOwnerSpenderTokenAndTimestamp(
                allowance.getOwner(), allowance.getSpender(), allowance.getTokenId(), blockTimestamp);
        assertThat(result.get().getAmount()).isEqualTo(initialAmount + 2 * amountForTransfer);
    }

    @Test
    void findByOwnerSpenderTokenAndTimestampPreventDoubleTransferDecrease() {
        long ownerId = 100L;
        long tokenId = 200L;
        long spenderId = 300L;
        long senderId = 400L;
        long tokenAllowanceTimestamp = System.currentTimeMillis();
        long tokenAllowanceTimestamp1 = tokenAllowanceTimestamp + 1;
        long tokenTransferTimestamp = tokenAllowanceTimestamp + 100;
        long tokenTransfer1Timestamp = tokenTransferTimestamp + 1;
        long tokenTransfer2Timestamp = tokenTransferTimestamp + 2;
        long blockTimestamp = tokenAllowanceTimestamp + 300;
        final long initialAmount = 3;
        final long amountForTransfer = -1;

        final var allowance = domainBuilder
                .tokenAllowance()
                .customize(a -> a.owner(ownerId)
                        .amountGranted(initialAmount)
                        .amount(initialAmount)
                        .tokenId(tokenId)
                        .spender(senderId)
                        .timestampRange(Range.atLeast(tokenAllowanceTimestamp)))
                .persist();

        domainBuilder
                .tokenAllowance()
                .customize(a -> a.owner(ownerId)
                        .amountGranted(initialAmount)
                        .amount(initialAmount)
                        .tokenId(tokenId)
                        .spender(spenderId)
                        .timestampRange(Range.atLeast(tokenAllowanceTimestamp1)))
                .persist();

        // Must be decreased only for the second allowance
        domainBuilder
                .tokenTransfer()
                .customize(t -> t.isApproval(true)
                        .amount(amountForTransfer)
                        .payerAccountId(EntityId.of(spenderId))
                        .id(TokenTransfer.Id.builder()
                                .tokenId(EntityId.of(tokenId))
                                .accountId(EntityId.of(ownerId))
                                .consensusTimestamp(tokenTransferTimestamp)
                                .build()))
                .persist();

        // Must be decreased only for the first allowance
        domainBuilder
                .tokenTransfer()
                .customize(t -> t.isApproval(true)
                        .amount(amountForTransfer)
                        .payerAccountId(EntityId.of(senderId))
                        .id(TokenTransfer.Id.builder()
                                .tokenId(EntityId.of(tokenId))
                                .accountId(EntityId.of(ownerId))
                                .consensusTimestamp(tokenTransfer1Timestamp)
                                .build()))
                .persist();

        // Must be decreased only for the first allowance
        domainBuilder
                .tokenTransfer()
                .customize(t -> t.isApproval(true)
                        .amount(amountForTransfer)
                        .payerAccountId(EntityId.of(senderId))
                        .id(TokenTransfer.Id.builder()
                                .tokenId(EntityId.of(tokenId))
                                .accountId(EntityId.of(ownerId))
                                .consensusTimestamp(tokenTransfer2Timestamp)
                                .build()))
                .persist();

        domainBuilder
                .contractResult()
                .customize(c -> c.consensusTimestamp(tokenTransfer2Timestamp).senderId(EntityId.of(senderId)))
                .persist();

        var result = repository.findByOwnerSpenderTokenAndTimestamp(
                allowance.getOwner(), allowance.getSpender(), allowance.getTokenId(), blockTimestamp);
        assertThat(result.get().getAmount()).isEqualTo(initialAmount + 2 * amountForTransfer);
    }
}
