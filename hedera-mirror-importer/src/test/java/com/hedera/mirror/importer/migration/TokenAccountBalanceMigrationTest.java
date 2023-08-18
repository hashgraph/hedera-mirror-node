/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.migration;

import static com.hedera.mirror.common.domain.entity.EntityType.ACCOUNT;
import static com.hedera.mirror.common.domain.entity.EntityType.TOKEN;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.balance.AccountBalanceFile;
import com.hedera.mirror.common.domain.balance.TokenBalance;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.TokenAccount;
import com.hedera.mirror.common.domain.token.TokenSupplyTypeEnum;
import com.hedera.mirror.common.domain.token.TokenTransfer;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.repository.AccountBalanceFileRepository;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import com.hedera.mirror.importer.repository.TokenAccountHistoryRepository;
import com.hedera.mirror.importer.repository.TokenAccountRepository;
import com.hedera.mirror.importer.repository.TokenBalanceRepository;
import com.hedera.mirror.importer.repository.TokenTransferRepository;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Tag("migration")
class TokenAccountBalanceMigrationTest extends IntegrationTest {

    private final AccountBalanceFileRepository accountBalanceFileRepository;
    private final RecordFileRepository recordFileRepository;
    private final TokenAccountRepository tokenAccountRepository;
    private final TokenAccountHistoryRepository tokenAccountHistoryRepository;
    private final TokenBalanceRepository tokenBalanceRepository;
    private final TokenAccountBalanceMigration tokenAccountBalanceMigration;
    private final TokenTransferRepository tokenTransferRepository;

    private AccountBalanceFile accountBalanceFile;
    private AtomicLong timestamp;
    private TokenAccount tokenAccount;
    private TokenAccount tokenAccount2;
    private TokenAccount tokenAccount3;
    private TokenAccount deletedEntityTokenAccount4;
    private TokenAccount disassociatedTokenAccount5;
    private TokenBalance tokenBalance;

    @BeforeEach
    void beforeEach() {
        timestamp = new AtomicLong(0L);
    }

    @Test
    void checksum() {
        assertThat(tokenAccountBalanceMigration.getChecksum()).isEqualTo(3);
    }

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
                .containsExactlyInAnyOrder(
                        tokenAccount,
                        tokenAccount2,
                        tokenAccount3,
                        deletedEntityTokenAccount4,
                        disassociatedTokenAccount5);
        assertThat(tokenAccountHistoryRepository.findAll()).isEmpty();
    }

    @Test
    void migrateTokenTransfer() {
        // given
        setup();

        // token transfer between the consensusTimestamp of the balance file and the current timestamp
        var balanceUpdatedAfterBalanceFileConsensusTimestamp = 12345L;
        var tokenTransferId = new TokenTransfer.Id(
                accountBalanceFile.getConsensusTimestamp() + 1,
                tokenBalance.getId().getTokenId(),
                tokenBalance.getId().getAccountId());
        domainBuilder
                .tokenTransfer()
                .customize(c -> c.id(tokenTransferId)
                        .amount(balanceUpdatedAfterBalanceFileConsensusTimestamp)
                        .build())
                .persist();
        var secondBalanceUpdate = 222L;
        var tokenTransferId2 = new TokenTransfer.Id(
                accountBalanceFile.getConsensusTimestamp() + 2,
                tokenBalance.getId().getTokenId(),
                tokenBalance.getId().getAccountId());
        domainBuilder
                .tokenTransfer()
                .customize(
                        c -> c.id(tokenTransferId2).amount(secondBalanceUpdate).build())
                .persist();

        // when
        tokenAccountBalanceMigration.doMigrate();

        // then
        tokenAccount.setBalance(
                balanceUpdatedAfterBalanceFileConsensusTimestamp + secondBalanceUpdate + tokenAccount.getBalance());
        assertThat(tokenAccountRepository.findAll())
                .containsExactlyInAnyOrder(
                        tokenAccount,
                        tokenAccount2,
                        tokenAccount3,
                        deletedEntityTokenAccount4,
                        disassociatedTokenAccount5);
        assertThat(tokenAccountHistoryRepository.findAll()).isEmpty();
    }

    @Test
    void migrateWhenNoRecordFile() {
        // given
        setup();
        recordFileRepository.deleteAll();

        // when
        tokenAccountBalanceMigration.doMigrate();

        // then
        deletedEntityTokenAccount4.setBalance(0L);
        tokenAccount.setBalance(0L);
        tokenAccount2.setBalance(0L);
        tokenAccount3.setBalance(0L);
        assertThat(tokenAccountRepository.findAll())
                .containsExactlyInAnyOrder(
                        tokenAccount,
                        tokenAccount2,
                        tokenAccount3,
                        deletedEntityTokenAccount4,
                        disassociatedTokenAccount5);
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
        deletedEntityTokenAccount4.setBalance(0L);
        assertThat(tokenAccountRepository.findAll())
                .containsExactlyInAnyOrder(
                        tokenAccount,
                        tokenAccount2,
                        tokenAccount3,
                        deletedEntityTokenAccount4,
                        disassociatedTokenAccount5);
    }

    @Test
    void migrateWhenNoTokenBalance() {
        // given
        setup();
        tokenBalanceRepository.prune(accountBalanceFile.getConsensusTimestamp());

        // when
        tokenAccountBalanceMigration.doMigrate();

        // then
        tokenAccount.setBalance(0L);
        tokenAccount2.setBalance(0L);
        tokenAccount3.setBalance(0L);
        deletedEntityTokenAccount4.setBalance(0L);
        assertThat(tokenAccountRepository.findAll())
                .containsExactlyInAnyOrder(
                        tokenAccount,
                        tokenAccount2,
                        tokenAccount3,
                        deletedEntityTokenAccount4,
                        disassociatedTokenAccount5);
    }

    @Test
    void migrateWhenNoTokenTransfer() {
        // given
        setup();
        tokenTransferRepository.prune(timestamp(Duration.ofSeconds(10)));

        // when
        tokenAccountBalanceMigration.doMigrate();

        // then
        tokenAccount.setBalance(100L);
        tokenAccount2.setBalance(33L);
        deletedEntityTokenAccount4.setBalance(999999L);
        assertThat(tokenAccountRepository.findAll())
                .containsExactlyInAnyOrder(
                        tokenAccount,
                        tokenAccount2,
                        tokenAccount3,
                        deletedEntityTokenAccount4,
                        disassociatedTokenAccount5);
    }

    @Test
    void migrateWhenNoTokenAccount() {
        // given
        setup();
        tokenAccountRepository.deleteAll();

        // when
        tokenAccountBalanceMigration.doMigrate();

        // then
        disassociatedTokenAccount5.setBalance(30L);
        assertThat(tokenAccountRepository.findAll())
                .usingRecursiveFieldByFieldElementComparatorIgnoringFields(
                        "associated", "createdTimestamp", "timestampRange")
                .containsExactlyInAnyOrder(
                        tokenAccount,
                        tokenAccount2,
                        tokenAccount3,
                        deletedEntityTokenAccount4,
                        disassociatedTokenAccount5)
                .allSatisfy(t -> assertThat(t)
                        .returns(true, TokenAccount::getAssociated)
                        .returns(0L, TokenAccount::getCreatedTimestamp)
                        .returns(Range.atLeast(0L), TokenAccount::getTimestampRange));
        assertThat(tokenAccountHistoryRepository.findAll()).isEmpty();
    }

    private void setup() {
        var entity1 = domainBuilder.entity().customize(e -> e.type(ACCOUNT)).persist();
        var accountId1 = EntityId.of(entity1.getId(), ACCOUNT);
        var entity2 = domainBuilder.entity().customize(e -> e.type(ACCOUNT)).persist();
        var accountId2 = EntityId.of(entity2.getId(), ACCOUNT);
        var entity4 = domainBuilder
                .entity()
                .customize(e -> e.type(ACCOUNT).deleted(true))
                .persist();
        var accountId4 = EntityId.of(entity4.getId(), ACCOUNT);
        var entity5 = domainBuilder.entity().customize(e -> e.type(ACCOUNT)).persist();
        var accountId5 = EntityId.of(entity5.getId(), ACCOUNT);

        var tokenId1 = EntityId.of("0.0.1000", TOKEN);
        var tokenId2 = EntityId.of("0.0.1001", TOKEN);

        domainBuilder
                .token()
                .customize(c -> c.supplyType(TokenSupplyTypeEnum.FINITE)
                        .tokenId(tokenId1.getId())
                        .totalSupply(1_000_000_000L)
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .build())
                .persist();
        domainBuilder
                .token()
                .customize(c -> c.supplyType(TokenSupplyTypeEnum.FINITE)
                        .tokenId(tokenId2.getId())
                        .totalSupply(1_000_000_000L)
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .build())
                .persist();

        // First record file, it's before account balance files
        domainBuilder
                .recordFile()
                .customize(r -> r.consensusStart(timestamp(Duration.ofMinutes(10)))
                        .consensusEnd(timestamp(Duration.ofSeconds(2))))
                .persist();

        // First account balance file
        var firstAccountBalanceFileTimestamp = timestamp(Duration.ofMinutes(10));
        domainBuilder
                .accountBalanceFile()
                .customize(a -> a.consensusTimestamp(firstAccountBalanceFileTimestamp))
                .persist();

        // Old token balance from first balance file
        // This balance is not expected to be migrated as it will not be in the latest balance file.
        var oldTokenBalanceId = new TokenBalance.Id(firstAccountBalanceFileTimestamp, accountId1, tokenId1);
        domainBuilder
                .tokenBalance()
                .customize(c -> c.id(oldTokenBalanceId).balance(99999999L))
                .persist();

        // Second account balance file, this file should be used for the token balance migration
        long accountBalanceTimestamp = timestamp(Duration.ofMinutes(10));
        accountBalanceFile = domainBuilder
                .accountBalanceFile()
                .customize(a -> a.consensusTimestamp(accountBalanceTimestamp))
                .persist();

        var tokenBalanceId = new TokenBalance.Id(accountBalanceTimestamp, accountId1, tokenId1);
        var tokenBalanceId2 = new TokenBalance.Id(accountBalanceTimestamp, accountId1, tokenId2);
        var tokenBalanceId3 = new TokenBalance.Id(accountBalanceTimestamp, accountId2, tokenId1);
        var tokenBalanceId4 = new TokenBalance.Id(accountBalanceTimestamp, accountId4, tokenId1);
        var tokenBalanceId5 = new TokenBalance.Id(accountBalanceTimestamp, accountId5, tokenId1);

        var tokenBalance1Amount = 100L;
        tokenBalance = domainBuilder
                .tokenBalance()
                .customize(c -> c.id(tokenBalanceId).balance(tokenBalance1Amount))
                .persist();
        tokenAccount = domainBuilder
                .tokenAccount()
                .customize(c -> c.accountId(accountId1.getId()).tokenId(tokenId1.getId()))
                .persist();
        tokenAccount2 = domainBuilder
                .tokenAccount()
                .customize(c -> c.accountId(accountId1.getId()).tokenId(tokenId2.getId()))
                .persist();
        tokenAccount3 = domainBuilder
                .tokenAccount()
                .customize(c -> c.accountId(accountId2.getId()).tokenId(tokenId1.getId()))
                .persist();
        // A deleted account, balances for this token account should be 0
        deletedEntityTokenAccount4 = domainBuilder
                .tokenAccount()
                .customize(c -> c.accountId(accountId4.getId()).tokenId(tokenId1.getId()))
                .persist();
        // A disassociated token account, balances for this token account should be 0
        disassociatedTokenAccount5 = domainBuilder
                .tokenAccount()
                .customize(c -> c.accountId(accountId5.getId())
                        .tokenId(tokenId1.getId())
                        .associated(false))
                .persist();

        var tokenBalance2Amount = 33L;
        domainBuilder
                .tokenBalance()
                .customize(c -> c.id(tokenBalanceId2).balance(tokenBalance2Amount))
                .persist();

        var tokenBalance3Amount = 4444L;
        domainBuilder
                .tokenBalance()
                .customize(c -> c.id(tokenBalanceId3).balance(tokenBalance3Amount))
                .persist();

        // Token balance to deleted account, this should not be migrated
        domainBuilder
                .tokenBalance()
                .customize(c -> c.id(tokenBalanceId4).balance(999999L))
                .persist();

        // Token balance to disassociated token account, this should not be migrated
        domainBuilder
                .tokenBalance()
                .customize(c -> c.id(tokenBalanceId5).balance(10L))
                .persist();

        // token transfer after the first account balance file
        var tokenTransfer2Amount = 1L;
        var tokenTransferId2 = new TokenTransfer.Id(timestamp(Duration.ofSeconds(1)), tokenId2, accountId1);
        domainBuilder
                .tokenTransfer()
                .customize(
                        c -> c.id(tokenTransferId2).amount(tokenTransfer2Amount).build())
                .persist();

        // Token transfer to deleted account, should not be added to token account balance
        var tokenTransferId4 = new TokenTransfer.Id(timestamp(Duration.ofSeconds(1)), tokenId1, accountId4);
        domainBuilder
                .tokenTransfer()
                .customize(c -> c.id(tokenTransferId4).amount(9876L).build())
                .persist();

        // Token transfer to disassociated token account, should not be added to token account balance
        var tokenTransferId5 = new TokenTransfer.Id(timestamp(Duration.ofSeconds(1)), tokenId1, accountId5);
        domainBuilder
                .tokenTransfer()
                .customize(c -> c.id(tokenTransferId5).amount(20L).build())
                .persist();

        tokenAccount.setBalance(tokenBalance1Amount);
        tokenAccount2.setBalance(tokenBalance2Amount + tokenTransfer2Amount);
        tokenAccount3.setBalance(tokenBalance3Amount);
        deletedEntityTokenAccount4.setBalance(1009875L);
        disassociatedTokenAccount5.setBalance(0L);

        // Second record file
        domainBuilder
                .recordFile()
                .customize(r -> r.consensusStart(timestamp(Duration.ofSeconds(5)))
                        .consensusEnd(timestamp(Duration.ofSeconds(2))))
                .persist();
    }

    private long timestamp(Duration delta) {
        return timestamp.addAndGet(delta.toNanos());
    }
}
