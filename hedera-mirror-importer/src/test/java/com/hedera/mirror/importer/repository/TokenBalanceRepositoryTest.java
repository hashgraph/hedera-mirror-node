/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.balance.TokenBalance;
import com.hedera.mirror.common.domain.balance.TokenBalance.Id;
import com.hedera.mirror.common.domain.entity.EntityId;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class TokenBalanceRepositoryTest extends AbstractRepositoryTest {

    private final TokenAccountRepository tokenAccountRepository;
    private final TokenBalanceRepository tokenBalanceRepository;

    @Test
    void balanceSnapshot() {
        long timestamp = 100;
        assertThat(tokenBalanceRepository.balanceSnapshot(timestamp)).isZero();

        var tokenAccount = domainBuilder.tokenAccount().persist();
        domainBuilder.tokenAccount().customize(ta -> ta.associated(false)).persist();
        assertThat(tokenBalanceRepository.balanceSnapshot(timestamp)).isOne();
        assertThat(tokenBalanceRepository.findAll())
                .containsExactly(TokenBalance.builder()
                        .balance(tokenAccount.getBalance())
                        .id(new Id(
                                timestamp,
                                EntityId.of(tokenAccount.getAccountId()),
                                EntityId.of(tokenAccount.getTokenId())))
                        .build());
    }

    @Test
    void updateBalanceSnapshot() {
        long lowerRangeTimestamp = 0L;
        long upperRangeTimestamp = 400L;
        long timestamp = 100;
        assertThat(tokenBalanceRepository.updateBalanceSnapshot(lowerRangeTimestamp, upperRangeTimestamp, timestamp))
                .isZero();
        assertThat(tokenBalanceRepository.findAll()).isEmpty();

        var tokenAccount = domainBuilder.tokenAccount().persist();
        var tokenAccount2 = domainBuilder.tokenAccount().persist();
        domainBuilder.tokenAccount().customize(ta -> ta.associated(false)).persist();

        var expected = Stream.of(tokenAccount, tokenAccount2)
                .map(t -> TokenBalance.builder()
                        .balance(t.getBalance())
                        .id(new Id(timestamp, EntityId.of(t.getAccountId()), EntityId.of(t.getTokenId())))
                        .build())
                .collect(Collectors.toList());

        // Update Balance Snapshot includes all balances
        assertThat(tokenBalanceRepository.updateBalanceSnapshot(lowerRangeTimestamp, upperRangeTimestamp, timestamp))
                .isEqualTo(expected.size());
        assertThat(tokenBalanceRepository.findAll()).containsExactlyInAnyOrderElementsOf(expected);

        long timestamp2 = 200;
        tokenAccount.setBalance(tokenAccount.getBalance() + 1);
        tokenAccountRepository.save(tokenAccount);
        expected.add(TokenBalance.builder()
                .balance(tokenAccount.getBalance())
                .id(new Id(
                        timestamp2, EntityId.of(tokenAccount.getAccountId()), EntityId.of(tokenAccount.getTokenId())))
                .build());

        assertThat(tokenBalanceRepository.updateBalanceSnapshot(lowerRangeTimestamp, upperRangeTimestamp, timestamp2))
                .isEqualTo(1);
        assertThat(tokenBalanceRepository.findAll()).containsExactlyInAnyOrderElementsOf(expected);

        var timestamp3 = 300L;
        tokenAccount2.setBalance(tokenAccount2.getBalance() + 1);
        tokenAccountRepository.save(tokenAccount2);
        expected.add(TokenBalance.builder()
                .balance(tokenAccount2.getBalance())
                .id(new Id(
                        timestamp3, EntityId.of(tokenAccount2.getAccountId()), EntityId.of(tokenAccount2.getTokenId())))
                .build());
        var tokenAccount3 = domainBuilder.tokenAccount().persist();
        expected.add(TokenBalance.builder()
                .balance(tokenAccount3.getBalance())
                .id(new Id(
                        timestamp3, EntityId.of(tokenAccount3.getAccountId()), EntityId.of(tokenAccount3.getTokenId())))
                .build());
        assertThat(tokenBalanceRepository.updateBalanceSnapshot(lowerRangeTimestamp, upperRangeTimestamp, timestamp3))
                .isEqualTo(2);
        assertThat(tokenBalanceRepository.findAll()).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void prune() {
        domainBuilder.tokenBalance().persist();
        var tokenBalance2 = domainBuilder.tokenBalance().persist();
        var tokenBalance3 = domainBuilder.tokenBalance().persist();

        tokenBalanceRepository.prune(tokenBalance2.getId().getConsensusTimestamp());

        assertThat(tokenBalanceRepository.findAll()).containsExactly(tokenBalance3);
    }

    @Test
    void save() {
        var tokenBalance1 = domainBuilder.tokenBalance().get();
        var tokenBalance2 = domainBuilder.tokenBalance().get();
        var tokenBalance3 = domainBuilder.tokenBalance().get();

        tokenBalanceRepository.saveAll(List.of(tokenBalance1, tokenBalance2, tokenBalance3));
        assertThat(tokenBalanceRepository.findById(tokenBalance1.getId())).get().isEqualTo(tokenBalance1);
        assertThat(tokenBalanceRepository.findAll())
                .containsExactlyInAnyOrder(tokenBalance1, tokenBalance2, tokenBalance3);
    }
}
