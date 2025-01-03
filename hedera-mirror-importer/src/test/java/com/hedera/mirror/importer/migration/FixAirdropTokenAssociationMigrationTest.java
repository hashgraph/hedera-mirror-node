/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.DomainWrapper;
import com.hedera.mirror.common.domain.balance.AccountBalance;
import com.hedera.mirror.common.domain.balance.TokenBalance;
import com.hedera.mirror.common.domain.balance.TokenBalance.Id;
import com.hedera.mirror.common.domain.balance.TokenBalance.TokenBalanceBuilder;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.AbstractTokenAirdrop;
import com.hedera.mirror.common.domain.token.NftTransfer;
import com.hedera.mirror.common.domain.token.TokenAccount;
import com.hedera.mirror.common.domain.token.TokenAirdropStateEnum;
import com.hedera.mirror.common.domain.token.TokenTransfer;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.DisableRepeatableSqlMigration;
import com.hedera.mirror.importer.ImporterIntegrationTest;
import com.hedera.mirror.importer.db.TimePartitionService;
import com.hedera.mirror.importer.repository.TokenAccountRepository;
import com.hedera.mirror.importer.repository.TokenBalanceRepository;
import jakarta.annotation.Resource;
import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;
import kotlin.text.Charsets;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Profiles;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(initializers = FixAirdropTokenAssociationMigrationTest.Initializer.class)
@DisableRepeatableSqlMigration
@Import(DisablePartitionMaintenanceConfiguration.class)
@Tag("migration")
class FixAirdropTokenAssociationMigrationTest extends ImporterIntegrationTest {

    private static final long BALANCE_SNAPSHOT_INTERVAL = Duration.ofHours(1).toNanos();

    @Resource
    private FixAirdropTokenAssociationMigration migration;

    @Value("classpath:db/migration/v2/R__03_view.sql")
    private File createViewSql;

    @Resource
    private TimePartitionService timePartitionService;

    @Resource
    private TokenAccountRepository tokenAccountRepository;

    @Resource
    private TokenBalanceRepository tokenBalanceRepository;

    private List<TokenAccount> expectedTokenAccounts;
    private List<TokenBalance> expectedTokenBalances;
    private List<TokenAccount> expectedHistoricalTokenAccounts;

    @BeforeEach
    @SneakyThrows
    void beforeEach() {
        if (!isV1()) {
            jdbcOperations.update(FileUtils.readFileToString(createViewSql, Charsets.UTF_8));
        }
    }

    @Test
    void empty() {
        runMigration();
        softly.assertThat(tokenAccountRepository.count()).isZero();
        softly.assertThat(findHistory(TokenAccount.class)).isEmpty();
        softly.assertThat(tokenBalanceRepository.count()).isZero();
    }

    @Test
    void migrate() {
        setup();
        runMigration();

        softly.assertThat(tokenAccountRepository.findAll()).containsExactlyInAnyOrderElementsOf(expectedTokenAccounts);
        softly.assertThat(tokenBalanceRepository.findAll()).containsExactlyInAnyOrderElementsOf(expectedTokenBalances);
        softly.assertThat(findHistory(TokenAccount.class))
                .containsExactlyInAnyOrderElementsOf(expectedHistoricalTokenAccounts);
    }

    @Test
    void migrateWithoutBalanceSnapshots() {
        setup();
        jdbcOperations.update("truncate account_balance");
        jdbcOperations.update("truncate token_balance");
        runMigration();

        softly.assertThat(tokenAccountRepository.findAll()).containsExactlyInAnyOrderElementsOf(expectedTokenAccounts);
        softly.assertThat(tokenBalanceRepository.count()).isZero();
        softly.assertThat(findHistory(TokenAccount.class))
                .containsExactlyInAnyOrderElementsOf(expectedHistoricalTokenAccounts);
    }

    @SneakyThrows
    private void runMigration() {
        migration.doMigrate();
    }

    private void persistAccountBalance(long timestamp) {
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new AccountBalance.Id(timestamp, EntityId.of(2))))
                .persist();
    }

    private TokenAccount persistClaimedAirdrop(
            long accountId,
            long amount,
            long tokenId,
            boolean fungible,
            boolean createExplicitAssociation,
            long timestamp,
            Long timestampUpper) {
        long senderAccountId = domainBuilder.id();
        if (fungible) {
            if (timestampUpper == null) {
                domainBuilder
                        .tokenAirdrop(TokenTypeEnum.FUNGIBLE_COMMON)
                        .customize(ta -> ta.amount(amount)
                                .receiverAccountId(accountId)
                                .senderAccountId(senderAccountId)
                                .state(TokenAirdropStateEnum.CLAIMED)
                                .tokenId(tokenId)
                                .timestampRange(Range.atLeast(timestamp)))
                        .persist();
            } else {
                domainBuilder
                        .tokenAirdropHistory(TokenTypeEnum.FUNGIBLE_COMMON)
                        .customize(ta -> ta.amount(amount)
                                .receiverAccountId(accountId)
                                .senderAccountId(senderAccountId)
                                .state(TokenAirdropStateEnum.CLAIMED)
                                .tokenId(tokenId)
                                .timestampRange(Range.closedOpen(timestamp, timestampUpper)))
                        .persist();
            }
            domainBuilder
                    .tokenTransfer()
                    .customize(t -> t.amount(amount)
                            .id(new TokenTransfer.Id(timestamp, EntityId.of(tokenId), EntityId.of(accountId))))
                    .persist();
            domainBuilder
                    .tokenTransfer()
                    .customize(t -> t.amount(-amount)
                            .id(new TokenTransfer.Id(timestamp, EntityId.of(tokenId), EntityId.of(senderAccountId))))
                    .persist();
        } else {
            var nftTransfers = LongStream.range(0, amount)
                    .map(i -> {
                        AbstractTokenAirdrop airdrop;
                        if (timestampUpper == null) {
                            airdrop = domainBuilder
                                    .tokenAirdrop(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                                    .customize(ta -> ta.receiverAccountId(accountId)
                                            .senderAccountId(senderAccountId)
                                            .state(TokenAirdropStateEnum.CLAIMED)
                                            .tokenId(tokenId)
                                            .timestampRange(Range.atLeast(timestamp)))
                                    .persist();
                        } else {
                            airdrop = domainBuilder
                                    .tokenAirdropHistory(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                                    .customize(ta -> ta.receiverAccountId(accountId)
                                            .senderAccountId(senderAccountId)
                                            .state(TokenAirdropStateEnum.CLAIMED)
                                            .tokenId(tokenId)
                                            .timestampRange(Range.closedOpen(timestamp, timestampUpper)))
                                    .persist();
                        }
                        return airdrop.getSerialNumber();
                    })
                    .mapToObj(serialNumber -> NftTransfer.builder()
                            .receiverAccountId(EntityId.of(accountId))
                            .senderAccountId(EntityId.of(senderAccountId))
                            .serialNumber(serialNumber)
                            .tokenId(EntityId.of(tokenId))
                            .build())
                    .toList();
            domainBuilder
                    .transaction()
                    .customize(t -> t.consensusTimestamp(timestamp)
                            .nftTransfer(nftTransfers)
                            .payerAccountId(EntityId.of(accountId))
                            .type(TransactionType.TOKENCLAIMAIRDROP.getProtoId())
                            .validStartNs(timestamp - 100))
                    .persist();
        }

        if (createExplicitAssociation) {
            // The token association was explicitly created before claiming the airdrop
            long createdTimestamp = timestamp - 100;
            var timestampRange =
                    timestampUpper == null ? Range.atLeast(timestamp) : Range.closedOpen(timestamp, timestampUpper);
            return domainBuilder
                    .tokenAccount()
                    .customize(ta -> ta.accountId(accountId)
                            .balance(amount)
                            .balanceTimestamp(timestamp)
                            .createdTimestamp(createdTimestamp)
                            .timestampRange(timestampRange)
                            .tokenId(tokenId))
                    .persist();
        } else {
            // Assume the token claim aidrop implicitly creates the association
            var timestampRange =
                    timestampUpper == null ? Range.atLeast(timestamp) : Range.closedOpen(timestamp, timestampUpper);
            return domainBuilder
                    .tokenAccount()
                    .customize(ta -> ta.accountId(accountId)
                            .balance(amount)
                            .balanceTimestamp(timestamp)
                            .createdTimestamp(timestamp)
                            .timestampRange(timestampRange)
                            .tokenId(tokenId))
                    .get();
        }
    }

    private void setup() {
        expectedTokenAccounts = new ArrayList<>();
        expectedTokenBalances = new ArrayList<>();
        expectedHistoricalTokenAccounts = new ArrayList<>();

        // The second one should be a full balance snapshot
        var lastPartitionRange = timePartitionService
                .getTimePartitions("account_balance")
                .getLast()
                .getTimestampRange();
        long firstSnapshotTimestamp =
                lastPartitionRange.lowerEndpoint() - Duration.ofMinutes(50).toNanos();
        long secondSnapshotTimestamp = firstSnapshotTimestamp + BALANCE_SNAPSHOT_INTERVAL;
        long thirdSnapshotTimestamp = secondSnapshotTimestamp + BALANCE_SNAPSHOT_INTERVAL;
        persistAccountBalance(firstSnapshotTimestamp);
        persistAccountBalance(secondSnapshotTimestamp);
        persistAccountBalance(thirdSnapshotTimestamp);

        long account1 = domainBuilder.id();
        long account2 = domainBuilder.id();
        long account3 = domainBuilder.id();

        long fungibleToken1 = domainBuilder.id();
        long fungibleToken2 = domainBuilder.id();
        long nonFungibleToken1 = domainBuilder.id();

        // account1 associated itself with fungibleToken1 before claiming the pending airdrop
        long timestamp = firstSnapshotTimestamp - Duration.ofMinutes(5).toNanos();
        var tokenAccount = persistClaimedAirdrop(account1, 100, fungibleToken1, true, true, timestamp, null);
        expectedTokenAccounts.add(tokenAccount);
        expectedTokenBalances.addAll(List.of(
                tokenBalanceBuilder(account1, 100, firstSnapshotTimestamp, fungibleToken1)
                        .persist(),
                tokenBalanceBuilder(account1, 100, secondSnapshotTimestamp, fungibleToken1)
                        .persist()));

        // Claimed airdrop with missing token association
        timestamp += Duration.ofSeconds(1).toNanos();
        tokenAccount = persistClaimedAirdrop(account2, 200, fungibleToken2, true, false, timestamp, null);
        expectedTokenAccounts.add(tokenAccount);
        expectedTokenBalances.addAll(List.of(
                tokenBalanceBuilder(account2, 200, firstSnapshotTimestamp, fungibleToken2)
                        .get(),
                tokenBalanceBuilder(account2, 200, secondSnapshotTimestamp, fungibleToken2)
                        .get()));

        // Claimed non-fungible airdrop with missing token association
        timestamp += Duration.ofSeconds(1).toNanos();
        tokenAccount = persistClaimedAirdrop(account1, 5, nonFungibleToken1, false, false, timestamp, null);
        expectedTokenAccounts.add(tokenAccount);
        expectedTokenBalances.addAll(List.of(
                tokenBalanceBuilder(account1, 5, firstSnapshotTimestamp, nonFungibleToken1)
                        .get(),
                tokenBalanceBuilder(account1, 5, secondSnapshotTimestamp, nonFungibleToken1)
                        .get()));

        // Complicated scenario, account2 claimed pending airdrop for fungibleToken1 before the first balance snapshot
        // without an existing token association. Then, after the first balance snapshot, account2's fungibleToken1 was
        // wiped, and account2 dissociated from fungibleToken1. A while later, another pending airdrop happened, and
        // account2 claimed it again. What's expected is there will be two token associations for account2 and
        // fungibleToken1. The token balance snapshot should have one at the first balance snapshot for the first
        // association, and the other at second balance snapshot for the second association.
        timestamp += Duration.ofSeconds(1).toNanos();
        long dissociateTimestamp =
                firstSnapshotTimestamp + Duration.ofMinutes(1).toNanos();
        tokenAccount =
                persistClaimedAirdrop(account2, 300, fungibleToken1, true, false, timestamp, dissociateTimestamp);
        expectedHistoricalTokenAccounts.add(tokenAccount);
        expectedTokenBalances.add(tokenBalanceBuilder(account2, 300, firstSnapshotTimestamp, fungibleToken1)
                .get());

        // Dissociation, partial
        tokenAccount = domainBuilder
                .tokenAccount()
                .customize(ta -> ta.accountId(account2)
                        .associated(false)
                        .balance(0)
                        .balanceTimestamp(null)
                        .createdTimestamp(null)
                        .timestampRange(Range.atLeast(dissociateTimestamp))
                        .tokenId(fungibleToken1))
                .persist();

        // The second airdrop
        timestamp = dissociateTimestamp + Duration.ofSeconds(10).toNanos();
        tokenAccount.setTimestampUpper(timestamp);
        expectedHistoricalTokenAccounts.add(tokenAccount);
        tokenAccount = persistClaimedAirdrop(account2, 600, fungibleToken1, true, false, timestamp, null);
        expectedTokenAccounts.add(tokenAccount);
        expectedTokenBalances.add(tokenBalanceBuilder(account2, 600, secondSnapshotTimestamp, fungibleToken1)
                .get());

        // Account3 has receiver signature required on, so airdrops will always be pending and require claiming
        // regardless of the token association status
        timestamp += Duration.ofSeconds(10).toNanos();
        tokenAccount = persistClaimedAirdrop(account3, 400, fungibleToken1, true, false, timestamp, null);
        expectedTokenBalances.add(tokenBalanceBuilder(account3, 400, secondSnapshotTimestamp, fungibleToken1)
                .get());

        timestamp = secondSnapshotTimestamp + Duration.ofSeconds(10).toNanos();
        persistClaimedAirdrop(account3, 600, fungibleToken1, true, false, timestamp, null);
        tokenAccount.setBalance(tokenAccount.getBalance() + 600);
        tokenAccount.setBalanceTimestamp(timestamp);
        expectedTokenAccounts.add(tokenAccount);
        expectedTokenBalances.add(
                tokenBalanceBuilder(account3, tokenAccount.getBalance(), thirdSnapshotTimestamp, fungibleToken1)
                        .get());
    }

    private DomainWrapper<TokenBalance, TokenBalanceBuilder> tokenBalanceBuilder(
            long accountId, long balance, long timestamp, long tokenId) {
        return domainBuilder.tokenBalance().customize(tb -> tb.balance(balance)
                .id(new Id(timestamp, EntityId.of(accountId), EntityId.of(tokenId))));
    }

    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
            var environment = configurableApplicationContext.getEnvironment();
            String version = environment.acceptsProfiles(Profiles.of("v2")) ? "2.5.2" : "1.100.0";
            TestPropertyValues.of("spring.flyway.target=" + version).applyTo(environment);
        }
    }
}
