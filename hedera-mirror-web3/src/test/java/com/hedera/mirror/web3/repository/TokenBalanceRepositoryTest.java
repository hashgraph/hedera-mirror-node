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

import com.hedera.mirror.common.domain.balance.TokenBalance;
import com.hedera.mirror.web3.Web3IntegrationTest;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class TokenBalanceRepositoryTest extends Web3IntegrationTest {

    private final TokenBalanceRepository tokenBalanceRepository;

    static final long TRANSFER_AMOUNT = 10L;
    static final long TRANSFER_INCREMENT = 1L;

    @Test
    void findHistoricalByIdAndTimestampLessThanBlockTimestamp() {
        var tokenBalance1 = domainBuilder.tokenBalance().persist();

        Assertions.assertThat(tokenBalanceRepository.findByIdAndTimestampLessThan(
                        tokenBalance1.getId().getTokenId().getId(),
                        tokenBalance1.getId().getAccountId().getId(),
                        tokenBalance1.getId().getConsensusTimestamp() + 1))
                .get()
                .isEqualTo(tokenBalance1);
    }

    @Test
    void findHistoricalByIdAndConsensusTimestampEqualToBlockTimestamp() {
        var tokenBalance1 = domainBuilder.tokenBalance().persist();

        Assertions.assertThat(tokenBalanceRepository.findByIdAndTimestampLessThan(
                        tokenBalance1.getId().getTokenId().getId(),
                        tokenBalance1.getId().getAccountId().getId(),
                        tokenBalance1.getId().getConsensusTimestamp()))
                .isEmpty();
    }

    @Test
    void findHistoricalByIdAndConsensusTimestampLessThanBlockTimestamp() {
        var tokenBalance1 = domainBuilder.tokenBalance().persist();

        Assertions.assertThat(tokenBalanceRepository.findByIdAndTimestampLessThan(
                        tokenBalance1.getId().getTokenId().getId(),
                        tokenBalance1.getId().getAccountId().getId(),
                        tokenBalance1.getId().getConsensusTimestamp() - 1))
                .isEmpty();
    }

    @Test
    void shouldNotIncludeBalanceBeforeConsensusTimestamp() {
        var tokenBalance1 = domainBuilder.tokenBalance().persist();
        long consensusTimestamp = tokenBalance1.getId().getConsensusTimestamp();

        persistTokenTransfersBefore(3, consensusTimestamp, tokenBalance1);

        Optional<Long> historicalBalance = tokenBalanceRepository.findHistoricalTokenBalanceUpToTimestamp(
                tokenBalance1.getId().getTokenId().getId(),
                tokenBalance1.getId().getAccountId().getId(),
                consensusTimestamp);
        long tokenBalance = historicalBalance.orElseGet(tokenBalance1::getBalance);
        Assertions.assertThat(tokenBalance).isEqualTo(tokenBalance1.getBalance());
    }

    @Test
    void shouldIncludeBalanceDuringValidTimestampRange() {
        var tokenBalance1 = domainBuilder.tokenBalance().persist();
        long consensusTimestamp = tokenBalance1.getId().getConsensusTimestamp();
        long historicalAccountBalance = tokenBalance1.getBalance();

        persistTokenTransfers(3, consensusTimestamp, tokenBalance1);
        historicalAccountBalance += TRANSFER_AMOUNT * 3;

        Assertions.assertThat(tokenBalanceRepository
                        .findHistoricalTokenBalanceUpToTimestamp(
                                tokenBalance1.getId().getTokenId().getId(),
                                tokenBalance1.getId().getAccountId().getId(),
                                consensusTimestamp + 10)
                        .get())
                .isEqualTo(historicalAccountBalance);
    }

    @Test
    void shouldNotIncludeBalanceAfterTimestampFilter() {
        var tokenBalance1 = domainBuilder.tokenBalance().persist();
        long consensusTimestamp = tokenBalance1.getId().getConsensusTimestamp();
        long historicalAccountBalance = tokenBalance1.getBalance();

        persistTokenTransfers(3, consensusTimestamp, tokenBalance1);
        historicalAccountBalance += TRANSFER_AMOUNT * 3;

        persistTokenTransfers(3, consensusTimestamp + 10, tokenBalance1);

        Assertions.assertThat(tokenBalanceRepository
                        .findHistoricalTokenBalanceUpToTimestamp(
                                tokenBalance1.getId().getTokenId().getId(),
                                tokenBalance1.getId().getAccountId().getId(),
                                consensusTimestamp + 10)
                        .get())
                .isEqualTo(historicalAccountBalance);
    }

    private void persistTokenTransfersBefore(int count, long baseTimestamp, TokenBalance tokenBalance1) {
        for (int i = 0; i < count; i++) {
            long timestamp = baseTimestamp - TRANSFER_INCREMENT * (i + 1);
            persistTokenTransfer(TRANSFER_AMOUNT, timestamp, tokenBalance1);
        }
    }

    private void persistTokenTransfers(int count, long baseTimestamp, TokenBalance tokenBalance1) {
        for (int i = 0; i < count; i++) {
            long timestamp = baseTimestamp + TRANSFER_INCREMENT * (i + 1);
            persistTokenTransfer(TRANSFER_AMOUNT, timestamp, tokenBalance1);
        }
    }

    private void persistTokenTransfer(long transferAmount, long timestamp, TokenBalance tokenBalance1) {
        domainBuilder
                .tokenTransferCustom(
                        transferAmount,
                        timestamp,
                        tokenBalance1.getId().getTokenId(),
                        tokenBalance1.getId().getAccountId())
                .persist();
    }
}
