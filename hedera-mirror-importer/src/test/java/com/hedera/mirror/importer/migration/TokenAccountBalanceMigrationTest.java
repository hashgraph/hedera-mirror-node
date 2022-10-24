package com.hedera.mirror.importer.migration;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import static com.hedera.mirror.common.domain.entity.EntityType.ACCOUNT;
import static com.hedera.mirror.common.domain.entity.EntityType.TOKEN;
import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.hedera.mirror.common.domain.balance.TokenBalance;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.TokenAccount;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.repository.AccountBalanceFileRepository;
import com.hedera.mirror.importer.repository.TokenAccountHistoryRepository;
import com.hedera.mirror.importer.repository.TokenAccountRepository;
import com.hedera.mirror.importer.repository.TokenBalanceRepository;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Tag("migration")
class TokenAccountBalanceMigrationTest extends IntegrationTest {

    private final AccountBalanceFileRepository accountBalanceFileRepository;
    private long consensusTimestamp;
    private TokenAccount tokenAccount;
    private TokenAccount tokenAccount2;
    private TokenBalance tokenBalance;
    private final TokenAccountRepository tokenAccountRepository;
    private final TokenAccountHistoryRepository tokenAccountHistoryRepository;
    private final TokenBalanceRepository tokenBalanceRepository;
    private final TokenAccountBalanceMigration tokenAccountBalanceMigration;

    @Test
    void migrateWhenEmpty() {
        tokenAccountBalanceMigration.doMigrate();
        assertThat(tokenAccountRepository.findAll()).isEmpty();
    }

    @Test
    void migrate() {
        // given
        setup();

        // updates between the consensusTimestamp of the balance file and the current timestamp
        var balanceUpdatedAfterBalanceFileConsensusTimestamp = 12345L;
        var tokenBalanceId = new TokenBalance.Id(consensusTimestamp + 1,
                EntityId.of("0.0.3", ACCOUNT), EntityId.of("0.0.1000", TOKEN));
        tokenBalance.setId(tokenBalanceId);
        tokenBalance.setBalance(balanceUpdatedAfterBalanceFileConsensusTimestamp);
        tokenBalanceRepository.save(tokenBalance);

        var updated = tokenBalanceRepository.findById(tokenBalance.getId());

        var newTokenBalanceId = new TokenBalance.Id(consensusTimestamp + 1,
                EntityId.of("0.0.3", ACCOUNT), EntityId.of("0.0.1005", TOKEN));
        var newTokenBalance = domainBuilder.tokenBalance().customize(
                b -> b.id(newTokenBalanceId).balance(9999L)).persist();
        var newTokenAccount = domainBuilder.tokenAccount()
                .customize(c -> c.accountId(newTokenBalance.getId().getAccountId().getId())
                        .tokenId(newTokenBalance.getId().getTokenId().getId()))
                .persist();

        // when
        tokenAccountBalanceMigration.doMigrate();

        // then
        tokenAccount.setBalance(balanceUpdatedAfterBalanceFileConsensusTimestamp);
        newTokenAccount.setBalance(9999L);
        assertThat(tokenAccountRepository.findAll()).containsExactlyInAnyOrder(tokenAccount, tokenAccount2, newTokenAccount);
        assertThat(tokenAccountHistoryRepository.findAll()).isEmpty();
    }

    @Test
    void migrateWhenNoAccountBalance() {
        // given
        setup();
        accountBalanceFileRepository.deleteAll();

        // when
        tokenAccountBalanceMigration.doMigrate();

        // then
        tokenAccount.setBalance(0L);
        tokenAccount2.setBalance(300L);
        assertThat(tokenAccountRepository.findAll()).containsExactlyInAnyOrder(tokenAccount, tokenAccount2);
    }

    @Test
    void migrateWhenNoTokenBalance() {
        // given
        setup();
        tokenBalanceRepository.prune(consensusTimestamp);

        // when
        tokenAccountBalanceMigration.doMigrate();

        // then
        tokenAccount.setBalance(0L);
        tokenAccount2.setBalance(300L);
        assertThat(tokenAccountRepository.findAll()).containsExactlyInAnyOrder(tokenAccount, tokenAccount2);
    }

    @Test
    void migrateWhenNoTokenAccount() {
        // given
        setup();
        tokenAccountRepository.deleteAll();

        // when
        tokenAccountBalanceMigration.doMigrate();

        // then
        assertThat(tokenAccountRepository.findAll()).isEmpty();
    }

    private void setup() {
        consensusTimestamp = domainBuilder.timestamp();
        domainBuilder.accountBalanceFile()
                .customize(a -> a.consensusTimestamp(consensusTimestamp))
                .persist();

        var tokenBalanceId = new TokenBalance.Id(consensusTimestamp,
                EntityId.of("0.0.3", ACCOUNT), EntityId.of("0.0.1000", TOKEN));
        var tokenBalanceId2 = new TokenBalance.Id(consensusTimestamp,
                EntityId.of("0.0.3", ACCOUNT), EntityId.of("0.0.1001", TOKEN));

        tokenBalance = domainBuilder.tokenBalance()
                .customize(c -> c.id(tokenBalanceId).balance(100L)).persist();
        var tokenBalance2 = domainBuilder.tokenBalance()
                .customize(c -> c.id(tokenBalanceId2).balance(33L)).persist();

        tokenAccount = domainBuilder.tokenAccount()
                .customize(c -> c.accountId(tokenBalance.getId().getAccountId().getId())
                        .tokenId(tokenBalance.getId().getTokenId().getId()))
                .persist();
        tokenAccount2 = domainBuilder.tokenAccount()
                .customize(c -> c.accountId(tokenBalance2.getId().getAccountId().getId())
                        .tokenId(tokenBalance2.getId().getTokenId().getId())
                        .balance(300L))
                .persist();

        tokenAccount.setBalance(100L);
        tokenAccount2.setBalance(33L);
    }
}
