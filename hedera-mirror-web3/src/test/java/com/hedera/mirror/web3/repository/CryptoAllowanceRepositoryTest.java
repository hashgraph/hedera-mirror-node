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
import com.hedera.mirror.common.domain.entity.CryptoAllowance;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.web3.Web3IntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class CryptoAllowanceRepositoryTest extends Web3IntegrationTest {
    private final CryptoAllowanceRepository cryptoAllowanceRepository;

    @Test
    void findByOwnerAndApprovedForAllIsTrue() {
        final var allowance = domainBuilder.cryptoAllowance().persist();

        assertThat(cryptoAllowanceRepository.findByOwner(allowance.getOwner()))
                .hasSize(1)
                .contains(allowance);
    }

    @Test
    void findByOwnerAndTimestampLessThanBlockTimestamp() {
        final var allowance = domainBuilder.cryptoAllowance().persist();

        assertThat(cryptoAllowanceRepository.findByOwnerAndTimestamp(
                        allowance.getOwner(), allowance.getTimestampLower() + 1))
                .get()
                .isEqualTo(allowance);
    }

    @Test
    void findByOwnerAndTimestampEqualToBlockTimestamp() {
        final var allowance = domainBuilder.cryptoAllowance().persist();

        assertThat(cryptoAllowanceRepository.findByOwnerAndTimestamp(
                        allowance.getOwner(), allowance.getTimestampLower()))
                .get()
                .isEqualTo(allowance);
    }

    @Test
    void findByOwnerAndTimestampGreaterThanBlockTimestamp() {
        final var allowance = domainBuilder.cryptoAllowance().persist();

        assertThat(cryptoAllowanceRepository.findByOwnerAndTimestamp(
                        allowance.getOwner(), allowance.getTimestampLower() - 1))
                .isEmpty();
    }

    @Test
    void findByOwnerAndTimestampHistoricalLessThanBlockTimestamp() {
        final var allowanceHistory = domainBuilder.cryptoAllowanceHistory().persist();

        assertThat(cryptoAllowanceRepository.findByOwnerAndTimestamp(
                        allowanceHistory.getOwner(), allowanceHistory.getTimestampLower() + 1))
                .get()
                .usingRecursiveComparison()
                .isEqualTo(allowanceHistory);
    }

    @Test
    void findByOwnerAndTimestampHistoricalEqualToBlockTimestamp() {
        final var allowanceHistory = domainBuilder.cryptoAllowanceHistory().persist();

        assertThat(cryptoAllowanceRepository.findByOwnerAndTimestamp(
                        allowanceHistory.getOwner(), allowanceHistory.getTimestampLower()))
                .get()
                .usingRecursiveComparison()
                .isEqualTo(allowanceHistory);
    }

    @Test
    void findByOwnerAndTimestampHistoricalGreaterThanBlockTimestamp() {
        final var allowanceHistory = domainBuilder.cryptoAllowanceHistory().persist();

        assertThat(cryptoAllowanceRepository.findByOwnerAndTimestamp(
                        allowanceHistory.getOwner(), allowanceHistory.getTimestampLower() - 1))
                .isEmpty();
    }

    @Test
    void findByOwnerAndTimestampHistoricalReturnsLatestEntry() {
        long owner = 1L;
        final var allowanceHistory1 = domainBuilder
                .cryptoAllowanceHistory()
                .customize(a -> a.owner(owner))
                .persist();

        final var allowanceHistory2 = domainBuilder
                .cryptoAllowanceHistory()
                .customize(a -> a.owner(owner))
                .persist();

        final var latestTimestamp =
                Math.max(allowanceHistory1.getTimestampLower(), allowanceHistory2.getTimestampLower());

        assertThat(cryptoAllowanceRepository.findByOwnerAndTimestamp(allowanceHistory1.getOwner(), latestTimestamp + 1))
                .hasValueSatisfying(
                        actual -> assertThat(actual).returns(latestTimestamp, CryptoAllowance::getTimestampLower));
    }

    @Test
    void findByOwnerAndTimestampWithTransferHappyPath() {
        long payerAccountId = 1L;
        long cryptoAllowanceTimestamp = System.currentTimeMillis();
        long cryptoTransferTimestamp = cryptoAllowanceTimestamp + 1;
        long blockTimestamp = cryptoAllowanceTimestamp + 2;

        final var allowance = domainBuilder
                .cryptoAllowance()
                .customize(a -> a.payerAccountId(EntityId.of(payerAccountId))
                        .amount(3)
                        .timestampRange(Range.atLeast(cryptoAllowanceTimestamp)))
                .persist();

        final var cryptoTransfer = domainBuilder
                .cryptoTransfer()
                .customize(c -> c.payerAccountId(EntityId.of(payerAccountId))
                        .amount(1)
                        .consensusTimestamp(cryptoTransferTimestamp))
                .persist();

        assertThat(cryptoAllowanceRepository.findByOwnerAndTimestamp(allowance.getOwner(), blockTimestamp))
                .hasValueSatisfying(actual -> assertThat(actual).returns(2L, CryptoAllowance::getAmount));
    }

    @Test
    void findByOwnerAndTimestampWithTransferAfterBlockTimestamp() {
        long payerAccountId = 1L;
        long cryptoAllowanceTimestamp = System.currentTimeMillis();
        long blockTimestamp = cryptoAllowanceTimestamp + 1;
        long cryptoTransferTimestamp = cryptoAllowanceTimestamp + 2;

        final var allowance = domainBuilder
                .cryptoAllowance()
                .customize(a -> a.payerAccountId(EntityId.of(payerAccountId))
                        .amount(3)
                        .timestampRange(Range.atLeast(cryptoAllowanceTimestamp)))
                .persist();

        // This transfer should not be selected and the amount should not be subtracted from the allowance.
        final var cryptoTransfer = domainBuilder
                .cryptoTransfer()
                .customize(c -> c.payerAccountId(EntityId.of(payerAccountId))
                        .amount(1)
                        .consensusTimestamp(cryptoTransferTimestamp))
                .persist();

        assertThat(cryptoAllowanceRepository.findByOwnerAndTimestamp(allowance.getOwner(), blockTimestamp))
                .hasValueSatisfying(actual -> assertThat(actual).returns(3L, CryptoAllowance::getAmount));
    }
}
