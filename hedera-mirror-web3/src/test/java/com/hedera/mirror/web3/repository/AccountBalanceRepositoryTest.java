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
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.web3.Web3IntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class AccountBalanceRepositoryTest extends Web3IntegrationTest {

    private final AccountBalanceRepository accountBalanceRepository;

    static final long TRANSFER_AMOUNT = 10L;
    static final long TRANSFER_INCREMENT = 1L;

    @Test
    void findHistoricalByIdAndTimestampLessThanBlockTimestamp() {
        var accountBalance = domainBuilder.accountBalance().persist();
        assertThat(accountBalanceRepository.findByIdAndTimestampLessThan(
                        accountBalance.getId().getAccountId().getId(),
                        accountBalance.getId().getConsensusTimestamp() + 1l))
                .get()
                .isEqualTo(accountBalance);
    }

    @Test
    void findHistoricalByIdAndTimestampEqualToBlockTimestamp() {
        var accountBalance = domainBuilder.accountBalance().persist();
        assertThat(accountBalanceRepository.findByIdAndTimestampLessThan(
                        accountBalance.getId().getAccountId().getId(),
                        accountBalance.getId().getConsensusTimestamp()))
                .get()
                .isEqualTo(accountBalance);
    }

    @Test
    void findHistoricalByIdAndTimestampGreaterThanBlockTimestamp() {
        var accountBalance = domainBuilder.accountBalance().persist();
        assertThat(accountBalanceRepository.findByIdAndTimestampLessThan(
                        accountBalance.getId().getAccountId().getId(),
                        accountBalance.getId().getConsensusTimestamp() - 1l))
                .isEmpty();
    }

    @Test
    void shouldNotIncludeBalanceBeforeConsensusTimestamp() {
        var accountBalance1 = domainBuilder.accountBalance().persist();
        long consensusTimestamp = accountBalance1.getId().getConsensusTimestamp();

        persistCryptoTransfersBefore(3, consensusTimestamp, accountBalance1);

        assertThat(accountBalanceRepository.findHistoricalAccountBalanceUpToTimestamp(
                        accountBalance1.getId().getAccountId().getId(), consensusTimestamp + 10L, 0L))
                .get()
                .isEqualTo(accountBalance1.getBalance());
    }

    @Test
    void shouldIncludeBalanceDuringValidTimestampRange() {
        var accountBalance1 = domainBuilder.accountBalance().persist();

        long consensusTimestamp = accountBalance1.getId().getConsensusTimestamp();
        long historicalAccountBalance = accountBalance1.getBalance();

        persistCryptoTransfers(3, consensusTimestamp, accountBalance1);
        historicalAccountBalance += TRANSFER_AMOUNT * 3;

        assertThat(accountBalanceRepository.findHistoricalAccountBalanceUpToTimestamp(
                        accountBalance1.getId().getAccountId().getId(), consensusTimestamp + 10L, 0L))
                .get()
                .isEqualTo(historicalAccountBalance);
    }

    @Test
    void shouldNotIncludeBalanceAfterTimestampFilter() {
        var accountBalance1 = domainBuilder.accountBalance().persist();
        long consensusTimestamp = accountBalance1.getId().getConsensusTimestamp();
        long historicalAccountBalance = accountBalance1.getBalance();

        persistCryptoTransfers(3, consensusTimestamp, accountBalance1);
        historicalAccountBalance += TRANSFER_AMOUNT * 3;

        persistCryptoTransfers(3, consensusTimestamp + 10, accountBalance1);

        assertThat(accountBalanceRepository.findHistoricalAccountBalanceUpToTimestamp(
                        accountBalance1.getId().getAccountId().getId(), consensusTimestamp + 10l, 0L))
                .get()
                .isEqualTo(historicalAccountBalance);
    }

    @Test
    void shouldGetBalanceWhenAccountBalanceEntryIsMissingTimestampBeforeTheAccountCreation() {
        // Test case: account_balance entry BEFORE crypto transfers is missing
        // pass timestamp that is before the initial balance transfer and account creation
        long accountId = 123L;
        long initialBalance = 15L;
        Entity account = domainBuilder
                .entity()
                .customize(a -> a.id(accountId).balance(initialBalance))
                .persist();
        long accountCreationTimestamp = account.getCreatedTimestamp();

        // account creation initial transfer
        var initialTransfer = domainBuilder
                .cryptoTransfer()
                .customize(
                        b -> b.amount(initialBalance).entityId(accountId).consensusTimestamp(accountCreationTimestamp))
                .persist();
        var secondTransfer = domainBuilder
                .cryptoTransfer()
                .customize(b -> b.amount(TRANSFER_AMOUNT)
                        .entityId(accountId)
                        .consensusTimestamp(initialTransfer.getConsensusTimestamp() + 2L))
                .persist();

        assertThat(accountBalanceRepository.findHistoricalAccountBalanceUpToTimestamp(
                        accountId, accountCreationTimestamp - 1, accountCreationTimestamp))
                .get()
                .isEqualTo(0L);
    }

    @Test
    void shouldGetBalanceWhenAccountBalanceEntryIsMissingNotMatchingTheInitialBalance() {
        // Test case: account_balance entry BEFORE crypto transfers is missing
        // pass timestamp that is after the initial balance transfer
        long accountId = 123L;
        long initialBalance = 15L;
        Entity account = domainBuilder
                .entity()
                .customize(a -> a.id(accountId).balance(initialBalance))
                .persist();
        long accountCreationTimestamp = account.getCreatedTimestamp();

        // account creation initial transfer
        var initialTransfer = domainBuilder
                .cryptoTransfer()
                .customize(
                        b -> b.amount(initialBalance).entityId(accountId).consensusTimestamp(accountCreationTimestamp))
                .persist();
        var secondTransfer = domainBuilder
                .cryptoTransfer()
                .customize(b -> b.amount(TRANSFER_AMOUNT)
                        .entityId(accountId)
                        .consensusTimestamp(initialTransfer.getConsensusTimestamp() + 2L))
                .persist();
        long timestampBetweenTheTransfers = initialTransfer.getConsensusTimestamp() + 1L;

        assertThat(accountBalanceRepository.findHistoricalAccountBalanceUpToTimestamp(
                        accountId, timestampBetweenTheTransfers, accountCreationTimestamp))
                .get()
                .isEqualTo(initialBalance);

        domainBuilder
                .cryptoTransfer()
                .customize(b -> b.amount(TRANSFER_AMOUNT)
                        .entityId(accountId)
                        .consensusTimestamp(secondTransfer.getConsensusTimestamp() + 2L))
                .persist();
        long timestampBetweenSecondAndThirdTheTransfers = secondTransfer.getConsensusTimestamp() + 1L;

        assertThat(accountBalanceRepository.findHistoricalAccountBalanceUpToTimestamp(
                        accountId, timestampBetweenSecondAndThirdTheTransfers, accountCreationTimestamp + 1))
                .get()
                .isEqualTo(TRANSFER_AMOUNT);
    }

    @Test
    void shouldGetBalanceWhenAccountBalanceEntryIsMissingMatchingTheInitialBalance() {
        // Test case: account_balance entry BEFORE crypto transfers is missing
        // usually the account_balance gets persisted ~8 mins after the account creation
        // pass timestamp that is equal to the initial balance transfer
        long accountId = 123L;
        long initialBalance = 15L;
        Entity account = domainBuilder
                .entity()
                .customize(a -> a.id(accountId).balance(initialBalance))
                .persist();
        long accountCreationTimestamp = account.getCreatedTimestamp();

        // account creation initial transfer
        var initialTransfer = domainBuilder
                .cryptoTransfer()
                .customize(
                        b -> b.amount(initialBalance).entityId(accountId).consensusTimestamp(accountCreationTimestamp))
                .persist();
        var secondTransfer = domainBuilder
                .cryptoTransfer()
                .customize(b -> b.amount(TRANSFER_AMOUNT)
                        .entityId(accountId)
                        .consensusTimestamp(initialTransfer.getConsensusTimestamp() + 2L))
                .persist();
        long timestampBetweenTheTransfers = initialTransfer.getConsensusTimestamp() + 1L;

        assertThat(accountBalanceRepository.findHistoricalAccountBalanceUpToTimestamp(
                        accountId, timestampBetweenTheTransfers, accountCreationTimestamp))
                .get()
                .isEqualTo(initialBalance);

        domainBuilder
                .cryptoTransfer()
                .customize(b -> b.amount(TRANSFER_AMOUNT)
                        .entityId(accountId)
                        .consensusTimestamp(secondTransfer.getConsensusTimestamp() + 2L))
                .persist();
        long timestampBetweenSecondAndThirdTheTransfers = secondTransfer.getConsensusTimestamp() + 1L;

        assertThat(accountBalanceRepository.findHistoricalAccountBalanceUpToTimestamp(
                        accountId, timestampBetweenSecondAndThirdTheTransfers, accountCreationTimestamp))
                .get()
                .isEqualTo(TRANSFER_AMOUNT + initialBalance);
    }

    private void persistCryptoTransfersBefore(int count, long baseTimestamp, AccountBalance accountBalance1) {
        for (int i = 0; i < count; i++) {
            long timestamp = baseTimestamp - TRANSFER_INCREMENT * (i + 1l);
            persistCryptoTransfer(timestamp, accountBalance1);
        }
    }

    private void persistCryptoTransfers(int count, long baseTimestamp, AccountBalance accountBalance1) {
        for (int i = 0; i < count; i++) {
            long timestamp = baseTimestamp + TRANSFER_INCREMENT * (i + 1l);
            persistCryptoTransfer(timestamp, accountBalance1);
        }
    }

    private void persistCryptoTransfer(long timestamp, AccountBalance accountBalance1) {
        domainBuilder
                .cryptoTransfer()
                .customize(b -> b.amount(TRANSFER_AMOUNT)
                        .entityId(accountBalance1.getId().getAccountId().getId())
                        .consensusTimestamp(timestamp))
                .persist();
    }
}
