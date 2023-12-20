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

import com.hedera.mirror.common.domain.balance.AccountBalance;
import com.hedera.mirror.common.domain.balance.TokenBalance;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.TokenTransfer;
import com.hedera.mirror.web3.Web3IntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class TokenBalanceRepositoryTest extends Web3IntegrationTest {

    private static final long TRANSFER_AMOUNT = 10L;
    private static final long TRANSFER_INCREMENT = 1L;
    private static final EntityId TREASURY_ENTITY_ID = EntityId.of(2);

    private final TokenBalanceRepository tokenBalanceRepository;

    @Test
    void findHistoricalByIdAndTimestampLessThanBlockTimestamp() {
        var tokenBalance1 = domainBuilder.tokenBalance().persist();

        assertThat(tokenBalanceRepository.findByIdAndTimestampLessThan(
                        tokenBalance1.getId().getTokenId().getId(),
                        tokenBalance1.getId().getAccountId().getId(),
                        tokenBalance1.getId().getConsensusTimestamp() + 1))
                .get()
                .isEqualTo(tokenBalance1);
    }

    @Test
    void findHistoricalByIdAndConsensusTimestampEqualToBlockTimestamp() {
        var tokenBalance1 = domainBuilder.tokenBalance().persist();

        assertThat(tokenBalanceRepository.findByIdAndTimestampLessThan(
                        tokenBalance1.getId().getTokenId().getId(),
                        tokenBalance1.getId().getAccountId().getId(),
                        tokenBalance1.getId().getConsensusTimestamp()))
                .get()
                .isEqualTo(tokenBalance1);
    }

    @Test
    void findHistoricalByIdAndConsensusTimestampLessThanBlockTimestamp() {
        var tokenBalance1 = domainBuilder.tokenBalance().persist();

        assertThat(tokenBalanceRepository.findByIdAndTimestampLessThan(
                        tokenBalance1.getId().getTokenId().getId(),
                        tokenBalance1.getId().getAccountId().getId(),
                        tokenBalance1.getId().getConsensusTimestamp() - 1))
                .isEmpty();
    }

    @Test
    void shouldNotIncludeBalanceBeforeConsensusTimestamp() {
        var accountBalance = domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new AccountBalance.Id(domainBuilder.timestamp(), TREASURY_ENTITY_ID)))
                .persist();
        var tokenBalance1 = domainBuilder
                .tokenBalance()
                .customize(tb -> tb.id(new TokenBalance.Id(
                        accountBalance.getId().getConsensusTimestamp(),
                        accountBalance.getId().getAccountId(),
                        domainBuilder.entityId())))
                .persist();
        long consensusTimestamp = tokenBalance1.getId().getConsensusTimestamp();

        persistTokenTransfersBefore(3, consensusTimestamp, tokenBalance1);

        assertThat(tokenBalanceRepository.findHistoricalTokenBalanceUpToTimestamp(
                        tokenBalance1.getId().getTokenId().getId(),
                        tokenBalance1.getId().getAccountId().getId(),
                        consensusTimestamp + 10,
                        0L))
                .get()
                .isEqualTo(tokenBalance1.getBalance());
    }

    @Test
    void shouldIncludeBalanceDuringValidTimestampRange() {
        var accountBalance = domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new AccountBalance.Id(domainBuilder.timestamp(), TREASURY_ENTITY_ID)))
                .persist();
        var tokenBalance1 = domainBuilder
                .tokenBalance()
                .customize(tb -> tb.id(new TokenBalance.Id(
                        accountBalance.getId().getConsensusTimestamp(),
                        accountBalance.getId().getAccountId(),
                        domainBuilder.entityId())))
                .persist();

        long consensusTimestamp = tokenBalance1.getId().getConsensusTimestamp();
        long historicalAccountBalance = tokenBalance1.getBalance();

        persistTokenTransfers(3, consensusTimestamp, tokenBalance1);
        historicalAccountBalance += TRANSFER_AMOUNT * 3;

        assertThat(tokenBalanceRepository.findHistoricalTokenBalanceUpToTimestamp(
                        tokenBalance1.getId().getTokenId().getId(),
                        tokenBalance1.getId().getAccountId().getId(),
                        consensusTimestamp + 10,
                        0L))
                .get()
                .isEqualTo(historicalAccountBalance);
    }

    @Test
    void shouldNotIncludeBalanceAfterTimestampFilter() {
        var accountBalance = domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new AccountBalance.Id(domainBuilder.timestamp(), TREASURY_ENTITY_ID)))
                .persist();
        var tokenBalance1 = domainBuilder
                .tokenBalance()
                .customize(tb -> tb.id(new TokenBalance.Id(
                        accountBalance.getId().getConsensusTimestamp(),
                        accountBalance.getId().getAccountId(),
                        domainBuilder.entityId())))
                .persist();
        long consensusTimestamp = tokenBalance1.getId().getConsensusTimestamp();
        long historicalAccountBalance = tokenBalance1.getBalance(); // 1

        persistTokenTransfers(3, consensusTimestamp, tokenBalance1);
        historicalAccountBalance += TRANSFER_AMOUNT * 3; // 31

        persistTokenTransfers(3, consensusTimestamp + 10, tokenBalance1);

        assertThat(tokenBalanceRepository.findHistoricalTokenBalanceUpToTimestamp(
                        tokenBalance1.getId().getTokenId().getId(),
                        tokenBalance1.getId().getAccountId().getId(),
                        consensusTimestamp + 10,
                        0L))
                .get()
                .isEqualTo(historicalAccountBalance);
    }

    @Test
    void findHistoricalBalanceIfTokenBalanceIsMissing() {
        // Test case: account_balance and token_balance entry BEFORE token transfers is missing
        // usually the account_balance/token_balance gets persisted ~8 mins after the account creation

        var accountId = new AccountBalance.Id(domainBuilder.timestamp(), TREASURY_ENTITY_ID);
        Entity account = domainBuilder
                .entity()
                .customize(a -> a.id(accountId.getAccountId().getId()))
                .persist();
        long accountCreationTimestamp = account.getCreatedTimestamp();
        // not persisted
        var tokenBalance1 = domainBuilder
                .tokenBalance()
                .customize(tb -> tb.id(new TokenBalance.Id(
                        domainBuilder.timestamp(), accountId.getAccountId(), domainBuilder.entityId())))
                .get();

        long consensusTimestamp = tokenBalance1.getId().getConsensusTimestamp();
        long historicalAccountBalance = 0L; // 0 - we just created this account and there are no transfers

        persistTokenTransfers(3, consensusTimestamp, tokenBalance1);
        historicalAccountBalance += TRANSFER_AMOUNT * 3; // 30

        assertThat(tokenBalanceRepository.findHistoricalTokenBalanceUpToTimestamp(
                        tokenBalance1.getId().getTokenId().getId(),
                        tokenBalance1.getId().getAccountId().getId(),
                        consensusTimestamp + 10,
                        accountCreationTimestamp))
                .get()
                .isEqualTo(historicalAccountBalance);
    }

    private void persistTokenTransfersBefore(int count, long baseTimestamp, TokenBalance tokenBalance1) {
        for (int i = 0; i < count; i++) {
            long timestamp = baseTimestamp - TRANSFER_INCREMENT * (i + 1);
            persistTokenTransfer(timestamp, tokenBalance1);
        }
    }

    private void persistTokenTransfers(int count, long baseTimestamp, TokenBalance tokenBalance1) {
        for (int i = 0; i < count; i++) {
            long timestamp = baseTimestamp + TRANSFER_INCREMENT * (i + 1);
            persistTokenTransfer(timestamp, tokenBalance1);
        }
    }

    private void persistTokenTransfer(long timestamp, TokenBalance tokenBalance1) {
        domainBuilder
                .tokenTransfer()
                .customize(b -> b.amount(TRANSFER_AMOUNT)
                        .id(new TokenTransfer.Id(
                                timestamp,
                                tokenBalance1.getId().getTokenId(),
                                tokenBalance1.getId().getAccountId()))
                        .payerAccountId(tokenBalance1.getId().getAccountId()))
                .persist();
    }
}
