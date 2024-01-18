/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

@RequiredArgsConstructor
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

    @Test
    void findByOwnerAndTimestampWithTransferHappyPath() {
        long ownerId = 1L;
        long tokenId = 2L;
        long spenderId = 3L;
        long tokenAllowanceTimestamp = System.currentTimeMillis();
        long tokenTransferTimestamp = tokenAllowanceTimestamp + 1;
        long tokenTransferTimestamp1 = tokenAllowanceTimestamp + 2;
        long blockTimestamp = tokenAllowanceTimestamp + 3;
        final long initialAmount = 3;
        final long amountForTransfer = -1;

        final var allowance = domainBuilder
                .tokenAllowance()
                .customize(a -> a.owner(ownerId)
                        .amountGranted(initialAmount)
                        .amount(initialAmount)
                        .tokenId(tokenId)
                        .spender(spenderId)
                        .timestampRange(Range.atLeast(tokenAllowanceTimestamp)))
                .persist();

        final var allowanceHistory = domainBuilder
                .tokenAllowanceHistory()
                .customize(a -> a.owner(ownerId)
                        .amountGranted(initialAmount)
                        .amount(initialAmount)
                        .tokenId(tokenId)
                        .spender(spenderId + 1)
                        .timestampRange(Range.atLeast(tokenAllowanceTimestamp)))
                .persist();

        final var tokenTransfer = domainBuilder
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

        final var tokenTransfer1 = domainBuilder
                .tokenTransfer()
                .customize(t -> t.isApproval(true)
                        .amount(amountForTransfer)
                        .payerAccountId(EntityId.of(spenderId))
                        .id(TokenTransfer.Id.builder()
                                .tokenId(EntityId.of(tokenId))
                                .accountId(EntityId.of(ownerId))
                                .consensusTimestamp(tokenTransferTimestamp1)
                                .build()))
                .persist();

        var result = repository.findByOwnerAndTimestamp(allowance.getOwner(), blockTimestamp);
        assertThat(result).hasSize(2);
        assertThat(result.get(0)).returns(initialAmount + 2 * amountForTransfer, TokenAllowance::getAmount);
        assertThat(result.get(1)).returns(initialAmount, TokenAllowance::getAmount);
    }

    @Test
    void findByOwnerAndTimestampWithTransferMultipleEntries() {
        long ownerId = 1L;
        long tokenId = 2L;
        long spenderId = 3L;
        long tokenAllowanceTimestamp = System.currentTimeMillis();
        long tokenTransferTimestamp = tokenAllowanceTimestamp + 1;
        long tokenTransferTimestamp1 = tokenAllowanceTimestamp + 2;
        long blockTimestamp = tokenAllowanceTimestamp + 3;
        final long initialAmount = 3;
        final long amountForTransfer = -1;
        final long amountForTransfer1 = -2;

        final var allowance = domainBuilder
                .tokenAllowance()
                .customize(a -> a.owner(ownerId)
                        .amountGranted(initialAmount)
                        .amount(initialAmount)
                        .tokenId(tokenId)
                        .spender(spenderId)
                        .timestampRange(Range.atLeast(tokenAllowanceTimestamp)))
                .persist();

        final var allowanceHistory = domainBuilder
                .tokenAllowanceHistory()
                .customize(a -> a.owner(ownerId)
                        .amountGranted(initialAmount)
                        .amount(initialAmount)
                        .tokenId(tokenId)
                        .spender(spenderId + 1)
                        .timestampRange(Range.atLeast(tokenAllowanceTimestamp)))
                .persist();

        final var tokenTransfer = domainBuilder
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

        final var tokenTransfer1 = domainBuilder
                .tokenTransfer()
                .customize(t -> t.isApproval(true)
                        .amount(amountForTransfer1)
                        .payerAccountId(EntityId.of(spenderId + 1))
                        .id(TokenTransfer.Id.builder()
                                .tokenId(EntityId.of(tokenId))
                                .accountId(EntityId.of(ownerId))
                                .consensusTimestamp(tokenTransferTimestamp1)
                                .build()))
                .persist();

        var result = repository.findByOwnerAndTimestamp(allowance.getOwner(), blockTimestamp);
        assertThat(result).hasSize(2);
        assertThat(result.get(0)).returns(initialAmount + amountForTransfer, TokenAllowance::getAmount);
        assertThat(result.get(1)).returns(initialAmount + amountForTransfer1, TokenAllowance::getAmount);
    }

    @Test
    void findByOwnerAndTimestampWithTransferAfterBlockTimestamp() {
        long ownerId = 1L;
        long tokenId = 2L;
        long spenderId = 3L;
        long tokenAllowanceTimestamp = System.currentTimeMillis();
        long blockTimestamp = tokenAllowanceTimestamp + 1;
        long tokenTransferTimestamp = tokenAllowanceTimestamp + 2;
        final long initialAmount = 3;

        final var allowance = domainBuilder
                .tokenAllowance()
                .customize(a -> a.owner(ownerId)
                        .amountGranted(initialAmount)
                        .amount(initialAmount)
                        .spender(spenderId)
                        .tokenId(tokenId)
                        .timestampRange(Range.atLeast(tokenAllowanceTimestamp)))
                .persist();

        // This transfer should not be selected and the amount should not be subtracted from the allowance.
        final var tokenTransfer = domainBuilder
                .tokenTransfer()
                .customize(t -> t.isApproval(true)
                        .amount(-1)
                        .payerAccountId(EntityId.of(spenderId))
                        .id(TokenTransfer.Id.builder()
                                .tokenId(EntityId.of(tokenId))
                                .consensusTimestamp(tokenTransferTimestamp)
                                .accountId(EntityId.of(ownerId))
                                .build()))
                .persist();

        assertThat(repository
                        .findByOwnerAndTimestamp(allowance.getOwner(), blockTimestamp)
                        .get(0))
                .returns(initialAmount, TokenAllowance::getAmount);
    }

    @Test
    void findByOwnerAndTimestampWithContractTransfer() {
        long ownerId = 100L;
        long tokenId = 200L;
        long spenderId = 300L;
        long senderId = 400L;
        long tokenAllowanceTimestamp = System.currentTimeMillis();
        long tokenTransferTimestamp = tokenAllowanceTimestamp + 1;
        long blockTimestamp = tokenAllowanceTimestamp + 2;
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

        final var tokenTransfer = domainBuilder
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

        final var contractResult = domainBuilder
                .contractResult()
                .customize(c -> c.consensusTimestamp(tokenTransferTimestamp).senderId(EntityId.of(senderId)))
                .persist();

        var result = repository.findByOwnerAndTimestamp(allowance.getOwner(), blockTimestamp);
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).returns(initialAmount + amountForTransfer, TokenAllowance::getAmount);
    }

    @Test
    void findByOwnerAndTimestampWithContractTransferAfterBlockTimestamp() {
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

        final var tokenTransfer = domainBuilder
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

        final var contractResult = domainBuilder
                .contractResult()
                .customize(c -> c.consensusTimestamp(tokenTransferTimestamp).senderId(EntityId.of(senderId)))
                .persist();

        var result = repository.findByOwnerAndTimestamp(allowance.getOwner(), blockTimestamp);
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).returns(initialAmount, TokenAllowance::getAmount);
    }

    @Test
    void findByOwnerAndTimestampWithRegularTokenTransfersAndContractTokenTransfers() {
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

        final var tokenTransfer = domainBuilder
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

        final var contractTokenTransfer = domainBuilder
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

        final var contractResult = domainBuilder
                .contractResult()
                .customize(c -> c.consensusTimestamp(tokenTransfer1Timestamp).senderId(EntityId.of(senderId)))
                .persist();

        var result = repository.findByOwnerAndTimestamp(allowance.getOwner(), blockTimestamp);
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).returns(initialAmount + 2 * amountForTransfer, TokenAllowance::getAmount);
    }

    @Test
    void findByOwnerAndTimestampPreventDoubleTransferDecrease() {
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

        final var allowance1 = domainBuilder
                .tokenAllowance()
                .customize(a -> a.owner(ownerId)
                        .amountGranted(initialAmount)
                        .amount(initialAmount)
                        .tokenId(tokenId)
                        .spender(spenderId)
                        .timestampRange(Range.atLeast(tokenAllowanceTimestamp1)))
                .persist();

        // Must be decreased only for the second allowance
        final var tokenTransfer = domainBuilder
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
        final var tokenTransfer1 = domainBuilder
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
        final var contractTokenTransfer = domainBuilder
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

        final var contractResult = domainBuilder
                .contractResult()
                .customize(c -> c.consensusTimestamp(tokenTransfer2Timestamp).senderId(EntityId.of(senderId)))
                .persist();

        var result = repository.findByOwnerAndTimestamp(allowance.getOwner(), blockTimestamp);
        assertThat(result).hasSize(2);
        assertThat(result.stream()
                        .filter(ta -> ta.getSpender() == senderId)
                        .toList()
                        .get(0))
                .returns(initialAmount + 2 * amountForTransfer, TokenAllowance::getAmount);
        assertThat(result.stream()
                        .filter(ta -> ta.getSpender() == spenderId)
                        .toList()
                        .get(0))
                .returns(initialAmount + amountForTransfer, TokenAllowance::getAmount);
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
