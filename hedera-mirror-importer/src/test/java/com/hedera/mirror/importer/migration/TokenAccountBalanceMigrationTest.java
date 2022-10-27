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
import com.hedera.mirror.common.domain.token.TokenId;
import com.hedera.mirror.common.domain.token.TokenSupplyTypeEnum;
import com.hedera.mirror.common.domain.token.TokenTransfer;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.repository.AccountBalanceFileRepository;
import com.hedera.mirror.importer.repository.TokenAccountHistoryRepository;
import com.hedera.mirror.importer.repository.TokenAccountRepository;
import com.hedera.mirror.importer.repository.TokenBalanceRepository;
import com.hedera.mirror.importer.repository.TokenTransferRepository;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Tag("migration")
class TokenAccountBalanceMigrationTest extends IntegrationTest {

    private final AccountBalanceFileRepository accountBalanceFileRepository;
    private long consensusTimestamp;
    private TokenAccount tokenAccount;
    private TokenAccount tokenAccount2;
    private TokenAccount tokenAccount3;
    private TokenBalance tokenBalance;
    private final TokenAccountRepository tokenAccountRepository;
    private final TokenAccountHistoryRepository tokenAccountHistoryRepository;
    private final TokenBalanceRepository tokenBalanceRepository;
    private final TokenAccountBalanceMigration tokenAccountBalanceMigration;
    private final TokenTransferRepository tokenTransferRepository;

    @Test
    void migrateWhenEmpty() {
        tokenAccountBalanceMigration.doMigrate();
        assertThat(tokenAccountRepository.findAll()).isEmpty();
    }

    @Test
    void migrate() {
        // given
        setup();

        // when
        tokenAccountBalanceMigration.doMigrate();

        // then
        assertThat(tokenAccountRepository.findAll())
                .containsExactlyInAnyOrder(tokenAccount, tokenAccount2, tokenAccount3);
        assertThat(tokenAccountHistoryRepository.findAll()).isEmpty();
    }

    @Test
    void migrateTokenTransfer() {
        // given
        setup();

        // token transfer between the consensusTimestamp of the balance file and the current timestamp
        var balanceUpdatedAfterBalanceFileConsensusTimestamp = 12345L;
        var tokenTransferId = new TokenTransfer.Id(consensusTimestamp + 1, tokenBalance.getId().getTokenId(),
                tokenBalance.getId().getAccountId());
        domainBuilder.tokenTransfer()
                .customize(c -> c.id(tokenTransferId).amount(balanceUpdatedAfterBalanceFileConsensusTimestamp).build()).persist();
        var secondBalanceUpdate = 222L;
        var tokenTransferId2 = new TokenTransfer.Id(consensusTimestamp + 2, tokenBalance.getId().getTokenId(),
                tokenBalance.getId().getAccountId());
        domainBuilder.tokenTransfer()
                .customize(c -> c.id(tokenTransferId2).amount(secondBalanceUpdate).build()).persist();

        // when
        tokenAccountBalanceMigration.doMigrate();

        // then
        tokenAccount.setBalance(balanceUpdatedAfterBalanceFileConsensusTimestamp + secondBalanceUpdate + tokenAccount.getBalance());
        assertThat(tokenAccountRepository.findAll())
                .containsExactlyInAnyOrder(tokenAccount, tokenAccount2, tokenAccount3);
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
        tokenAccount2.setBalance(0L);
        tokenAccount3.setBalance(0L);
        assertThat(tokenAccountRepository.findAll())
                .containsExactlyInAnyOrder(tokenAccount, tokenAccount2, tokenAccount3);
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
        tokenAccount2.setBalance(0L);
        tokenAccount3.setBalance(0L);
        assertThat(tokenAccountRepository.findAll())
                .containsExactlyInAnyOrder(tokenAccount, tokenAccount2, tokenAccount3);
    }

    @Test
    void migrateWhenNoTokenTransfer() {
        // given
        setup();
        tokenTransferRepository.prune(consensusTimestamp + 10);

        // when
        tokenAccountBalanceMigration.doMigrate();

        // then
        tokenAccount.setBalance(100L);
        tokenAccount2.setBalance(33L);
        assertThat(tokenAccountRepository.findAll())
                .containsExactlyInAnyOrder(tokenAccount, tokenAccount2, tokenAccount3);
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

        var accountId1 = EntityId.of("0.0.3", ACCOUNT);
        var accountId2 = EntityId.of("0.0.4", ACCOUNT);
        var tokenId1 = EntityId.of("0.0.1000", TOKEN);
        var tokenId2 = EntityId.of("0.0.1001", TOKEN);

        domainBuilder.token().customize(c -> c.supplyType(TokenSupplyTypeEnum.FINITE)
                .tokenId(new TokenId(tokenId1))
                .totalSupply(1_000_000_000L)
                .type(TokenTypeEnum.FUNGIBLE_COMMON).build()).persist();
        domainBuilder.token().customize(c -> c.supplyType(TokenSupplyTypeEnum.FINITE)
                .tokenId(new TokenId(tokenId2))
                .totalSupply(1_000_000_000L)
                .type(TokenTypeEnum.FUNGIBLE_COMMON).build()).persist();

        domainBuilder.accountBalanceFile()
                .customize(a -> a.consensusTimestamp(consensusTimestamp))
                .persist();

        var tokenBalanceId = new TokenBalance.Id(consensusTimestamp, accountId1, tokenId1);
        var tokenBalanceId2 = new TokenBalance.Id(consensusTimestamp, accountId1, tokenId2);
        var tokenBalanceId3 = new TokenBalance.Id(consensusTimestamp, accountId2, tokenId1);

        var tokenBalance1Amount = 100L;
        tokenBalance = domainBuilder.tokenBalance()
                .customize(c -> c.id(tokenBalanceId).balance(tokenBalance1Amount)).persist();
        tokenAccount = domainBuilder.tokenAccount()
                .customize(c -> c.accountId(accountId1.getId())
                        .tokenId(tokenId1.getId()))
                .persist();
        tokenAccount2 = domainBuilder.tokenAccount()
                .customize(c -> c.accountId(accountId1.getId())
                        .tokenId(tokenId2.getId()))
                .persist();
        tokenAccount3 = domainBuilder.tokenAccount()
                .customize(c -> c.accountId(accountId2.getId())
                        .tokenId(tokenId1.getId()))
                .persist();

        var tokenBalance2Amount = 33L;
        domainBuilder.tokenBalance()
                .customize(c -> c.id(tokenBalanceId2).balance(tokenBalance2Amount)).persist();

        var tokenBalance3Amount = 4444L;
        domainBuilder.tokenBalance()
                .customize(c -> c.id(tokenBalanceId3).balance(tokenBalance3Amount)).persist();

        var tokenTransfer2Amount = 1L;
        var tokenTransferId2 = new TokenTransfer.Id(consensusTimestamp + 10, tokenId2,
                accountId1);
        domainBuilder.tokenTransfer()
                .customize(c -> c.id(tokenTransferId2).amount(tokenTransfer2Amount).build()).persist();

        tokenAccount.setBalance(tokenBalance1Amount);
        tokenAccount2.setBalance(tokenBalance2Amount + tokenTransfer2Amount);
        tokenAccount3.setBalance(tokenBalance3Amount);
    }
}
