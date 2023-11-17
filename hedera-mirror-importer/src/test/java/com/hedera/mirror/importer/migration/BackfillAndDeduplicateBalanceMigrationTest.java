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

package com.hedera.mirror.importer.migration;

import static com.hedera.mirror.importer.parser.domain.RecordItemBuilder.TREASURY;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Lists;
import com.hedera.mirror.common.domain.balance.AccountBalance;
import com.hedera.mirror.common.domain.balance.TokenBalance;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.importer.EnabledIfV1;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.config.Owner;
import com.hedera.mirror.importer.repository.AccountBalanceRepository;
import com.hedera.mirror.importer.repository.TokenBalanceRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StreamUtils;

@EnabledIfV1
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Tag("migration")
class BackfillAndDeduplicateBalanceMigrationTest
        extends AbstractAsyncJavaMigrationTest<BackfillAndDeduplicateBalanceMigration> {

    private static final String REVERT_DDL =
            """
            create table account_balance_old (
              consensus_timestamp nanos_timestamp  not null,
              balance             hbar_tinybars    not null,
              account_id          entity_id        not null,
              primary key (consensus_timestamp, account_id)
            );

            create table token_balance_old (
              consensus_timestamp bigint    not null,
              account_id          entity_id not null,
              balance             bigint    not null,
              token_id            entity_id not null,
              primary key (consensus_timestamp, account_id, token_id)
            );
            """;

    private final AccountBalanceRepository accountBalanceRepository;
    private final @Owner JdbcTemplate jdbcTemplate;
    private final @Getter BackfillAndDeduplicateBalanceMigration migration;
    private final @Getter Class<BackfillAndDeduplicateBalanceMigration> migrationClass =
            BackfillAndDeduplicateBalanceMigration.class;
    private final MirrorProperties mirrorProperties;

    @Value("classpath:db/migration/v1/V1.89.1__add_balance_deduplicate_functions.sql")
    private final Resource migrationSql;

    private final TokenBalanceRepository tokenBalanceRepository;

    @AfterEach
    void teardown() {
        addBalanceDeduplicateFunctions();
        jdbcTemplate.execute(REVERT_DDL);
        getMigrationProperties().getParams().clear();
    }

    @Test
    void empty() {
        // given, when
        runMigration();
        // then
        waitForCompletion();
        assertSchema();
        assertThat(accountBalanceRepository.findAll()).isEmpty();
        assertThat(tokenBalanceRepository.findAll()).isEmpty();
    }

    @Test
    void migrate() {
        // given
        // based on partitionTimeInterval of '10 years' in test application config
        var treasury = EntityId.of(TREASURY);
        var account2 = EntityId.of(domainBuilder.id() + TREASURY);
        var account3 = EntityId.of(domainBuilder.id() + TREASURY);
        var account4 = EntityId.of(domainBuilder.id() + TREASURY);
        var token = EntityId.of(domainBuilder.id() + TREASURY);

        // The balance snapshot already in db, processed by V1.89.2
        long sentinelTimestamp = LocalDate.now(UTC).toEpochSecond(LocalTime.of(22, 0), UTC) * 1_000_000_000L;

        // The first balance snapshot, ensured to be in the preceding partition
        var timestamp =
                new AtomicLong(sentinelTimestamp - Duration.ofDays(11 * 365).toNanos());
        domainBuilder
                .accountBalanceFile()
                .customize(abf -> abf.consensusTimestamp(timestamp.get()))
                .persist();
        var oldAccountBalances = new ArrayList<>(List.of(
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(100).id(new AccountBalance.Id(timestamp.get(), treasury)))
                        .get(),
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(200).id(new AccountBalance.Id(timestamp.get(), account2)))
                        .get(),
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(300).id(new AccountBalance.Id(timestamp.get(), account3)))
                        .get(),
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(400).id(new AccountBalance.Id(timestamp.get(), account4)))
                        .get()));
        var oldTokenBalances = new ArrayList<>(List.of(
                domainBuilder
                        .tokenBalance()
                        .customize(tb -> tb.balance(20).id(new TokenBalance.Id(timestamp.get(), account2, token)))
                        .get(),
                domainBuilder
                        .tokenBalance()
                        .customize(tb -> tb.balance(40).id(new TokenBalance.Id(timestamp.get(), account4, token)))
                        .get()));
        var expectedAccountBalances = new ArrayList<>(oldAccountBalances);
        var expectedTokenBalances = new ArrayList<>(oldTokenBalances);

        // The next snapshot, 15 hours before sentinelTimestamp
        timestamp.set(sentinelTimestamp - Duration.ofHours(15).toNanos());
        domainBuilder
                .accountBalanceFile()
                .customize(abf -> abf.consensusTimestamp(timestamp.get()))
                .persist();
        oldAccountBalances.addAll(List.of(
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(100).id(new AccountBalance.Id(timestamp.get(), treasury)))
                        .get(),
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(200).id(new AccountBalance.Id(timestamp.get(), account2)))
                        .get(),
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(300).id(new AccountBalance.Id(timestamp.get(), account3)))
                        .get(),
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(400).id(new AccountBalance.Id(timestamp.get(), account4)))
                        .get()));
        // account2's token balance no longer exists
        oldTokenBalances.add(domainBuilder
                .tokenBalance()
                .customize(tb -> tb.balance(40).id(new TokenBalance.Id(timestamp.get(), account4, token)))
                .get());
        // Since this is a full snapshot (first in a partition), all balances should be included
        expectedAccountBalances.addAll(List.of(
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(100).id(new AccountBalance.Id(timestamp.get(), treasury)))
                        .get(),
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(200).id(new AccountBalance.Id(timestamp.get(), account2)))
                        .get(),
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(300).id(new AccountBalance.Id(timestamp.get(), account3)))
                        .get(),
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(400).id(new AccountBalance.Id(timestamp.get(), account4)))
                        .get()));
        expectedTokenBalances.addAll(List.of(
                // when deduped, there should be a 0 token balance for account2
                domainBuilder
                        .tokenBalance()
                        .customize(tb -> tb.balance(0).id(new TokenBalance.Id(timestamp.get(), account2, token)))
                        .get(),
                domainBuilder
                        .tokenBalance()
                        .customize(tb -> tb.balance(40).id(new TokenBalance.Id(timestamp.get(), account4, token)))
                        .get()));

        // The next snapshot, 4 hours later
        timestamp.addAndGet(Duration.ofHours(4).toNanos());
        domainBuilder
                .accountBalanceFile()
                .customize(abf -> abf.consensusTimestamp(timestamp.get()))
                .persist();
        oldAccountBalances.addAll(List.of(
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(100).id(new AccountBalance.Id(timestamp.get(), treasury)))
                        .get(),
                // account2 no longer exists
                // account3 has balance change
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(301).id(new AccountBalance.Id(timestamp.get(), account3)))
                        .get(),
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(400).id(new AccountBalance.Id(timestamp.get(), account4)))
                        .get()));
        oldTokenBalances.addAll(List.of(
                domainBuilder
                        .tokenBalance()
                        .customize(tb -> tb.balance(30).id(new TokenBalance.Id(timestamp.get(), account3, token)))
                        .get(),
                domainBuilder
                        .tokenBalance()
                        .customize(tb -> tb.balance(40).id(new TokenBalance.Id(timestamp.get(), account4, token)))
                        .get()));
        expectedAccountBalances.addAll(List.of(
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(100).id(new AccountBalance.Id(timestamp.get(), treasury)))
                        .get(),
                // when deduped, there should be a 0 account balance for account2
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(0).id(new AccountBalance.Id(timestamp.get(), account2)))
                        .get(),
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(301).id(new AccountBalance.Id(timestamp.get(), account3)))
                        .get()));
        expectedTokenBalances.add(domainBuilder
                .tokenBalance()
                .customize(tb -> tb.balance(30).id(new TokenBalance.Id(timestamp.get(), account3, token)))
                .get());

        // The next snapshot, 6 hours later
        timestamp.addAndGet(Duration.ofHours(6).toNanos());
        domainBuilder
                .accountBalanceFile()
                .customize(abf -> abf.consensusTimestamp(timestamp.get()))
                .persist();
        oldAccountBalances.addAll(List.of(
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(100).id(new AccountBalance.Id(timestamp.get(), treasury)))
                        .get(),
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(301).id(new AccountBalance.Id(timestamp.get(), account3)))
                        .get(),
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(400).id(new AccountBalance.Id(timestamp.get(), account4)))
                        .get()));
        oldTokenBalances.addAll(List.of(
                domainBuilder
                        .tokenBalance()
                        .customize(tb -> tb.balance(30).id(new TokenBalance.Id(timestamp.get(), account3, token)))
                        .get(),
                domainBuilder
                        .tokenBalance()
                        .customize(tb -> tb.balance(40).id(new TokenBalance.Id(timestamp.get(), account4, token)))
                        .get()));
        // no balance change, there should be only one row for treasury
        expectedAccountBalances.add(domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(100).id(new AccountBalance.Id(timestamp.get(), treasury)))
                .get());

        // The next snapshot, 3 hours later, skipped by the migration
        timestamp.addAndGet(Duration.ofHours(3).toNanos());
        domainBuilder
                .accountBalanceFile()
                .customize(abf -> abf.consensusTimestamp(timestamp.get()))
                .persist();
        oldAccountBalances.addAll(List.of(
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(100).id(new AccountBalance.Id(timestamp.get(), treasury)))
                        .get(),
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(301).id(new AccountBalance.Id(timestamp.get(), account3)))
                        .get(),
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(400).id(new AccountBalance.Id(timestamp.get(), account4)))
                        .get()));
        oldTokenBalances.addAll(List.of(
                domainBuilder
                        .tokenBalance()
                        .customize(tb -> tb.balance(30).id(new TokenBalance.Id(timestamp.get(), account3, token)))
                        .get(),
                domainBuilder
                        .tokenBalance()
                        .customize(tb -> tb.balance(40).id(new TokenBalance.Id(timestamp.get(), account4, token)))
                        .get()));

        // The next snapshot, one hour later
        timestamp.addAndGet(Duration.ofHours(1).toNanos());
        domainBuilder
                .accountBalanceFile()
                .customize(abf -> abf.consensusTimestamp(timestamp.get()))
                .persist();
        oldAccountBalances.addAll(List.of(
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(100).id(new AccountBalance.Id(timestamp.get(), treasury)))
                        .get(),
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(301).id(new AccountBalance.Id(timestamp.get(), account3)))
                        .get(),
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(400).id(new AccountBalance.Id(timestamp.get(), account4)))
                        .get()));
        oldTokenBalances.addAll(List.of(
                domainBuilder
                        .tokenBalance()
                        .customize(tb -> tb.balance(30).id(new TokenBalance.Id(timestamp.get(), account3, token)))
                        .get(),
                // account4's token balance has changed
                domainBuilder
                        .tokenBalance()
                        .customize(tb -> tb.balance(41).id(new TokenBalance.Id(timestamp.get(), account4, token)))
                        .get()));
        expectedAccountBalances.add(domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(100).id(new AccountBalance.Id(timestamp.get(), treasury)))
                .get());
        expectedTokenBalances.add(domainBuilder
                .tokenBalance()
                .customize(tb -> tb.balance(41).id(new TokenBalance.Id(timestamp.get(), account4, token)))
                .get());

        // The snapshot at sentinelTimestamp. account3 was deleted right before the snapshot
        setSentinelTimestamp(sentinelTimestamp);
        domainBuilder
                .accountBalanceFile()
                .customize(abf -> abf.consensusTimestamp(sentinelTimestamp))
                .persist();
        oldAccountBalances.addAll(List.of(
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(100).id(new AccountBalance.Id(sentinelTimestamp, treasury)))
                        .get(),
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(400).id(new AccountBalance.Id(sentinelTimestamp, account4)))
                        .get()));
        oldTokenBalances.add(domainBuilder
                .tokenBalance()
                .customize(tb -> tb.balance(41).id(new TokenBalance.Id(sentinelTimestamp, account4, token)))
                .get());
        expectedAccountBalances.addAll(List.of(
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(100).id(new AccountBalance.Id(sentinelTimestamp, treasury)))
                        .persist(),
                // account3's 0 balance row is not added to the existing snapshot, the migration should patch it
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(0).id(new AccountBalance.Id(sentinelTimestamp, account3)))
                        .get(),
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(400).id(new AccountBalance.Id(sentinelTimestamp, account4)))
                        .persist()));
        expectedTokenBalances.addAll(List.of(
                // account3's 0 token balance row is not added to the existing snapshot, the migration should patch it
                domainBuilder
                        .tokenBalance()
                        .customize(tb -> tb.balance(0).id(new TokenBalance.Id(sentinelTimestamp, account3, token)))
                        .get(),
                domainBuilder
                        .tokenBalance()
                        .customize(tb -> tb.balance(41).id(new TokenBalance.Id(sentinelTimestamp, account4, token)))
                        .persist()));

        persistOldAccountBalances(oldAccountBalances);
        persistOldTokenBalances(oldTokenBalances);

        // when
        runMigration();

        // then
        waitForCompletion();
        assertSchema();
        assertThat(accountBalanceRepository.findAll()).containsExactlyInAnyOrderElementsOf(expectedAccountBalances);
        assertThat(tokenBalanceRepository.findAll()).containsExactlyInAnyOrderElementsOf(expectedTokenBalances);
    }

    @Test
    void migrationNoChange() {
        // given
        // there is no balance info before the portion already handled by V1.89.2, for simplicity, no data is populated
        // for account_balance_old and token_balance_old
        var treasury = EntityId.of(TREASURY);
        var account = EntityId.of(domainBuilder.id() + TREASURY);
        var token = EntityId.of(domainBuilder.id() + TREASURY);
        long timestamp = domainBuilder.timestamp();
        setSentinelTimestamp(timestamp);
        domainBuilder
                .accountBalanceFile()
                .customize(abf -> abf.consensusTimestamp(timestamp))
                .persist();
        var expectedAccountBalances = List.of(
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(100).id(new AccountBalance.Id(timestamp, treasury)))
                        .persist(),
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(200).id(new AccountBalance.Id(timestamp, account)))
                        .persist());
        var expectedTokenBalance = domainBuilder
                .tokenBalance()
                .customize(tb -> tb.balance(20).id(new TokenBalance.Id(timestamp, account, token)))
                .persist();

        // when
        runMigration();

        // then
        waitForCompletion();
        assertSchema();
        assertThat(accountBalanceRepository.findAll()).containsExactlyInAnyOrderElementsOf(expectedAccountBalances);
        assertThat(tokenBalanceRepository.findAll()).containsExactly(expectedTokenBalance);
    }

    @Test
    void minFrequency() {
        // given min frequency is set to 6 minutes
        getMigrationProperties().getParams().put("minFrequency", "6m");

        var treasury = EntityId.of(TREASURY);
        var account = EntityId.of(domainBuilder.id() + TREASURY);
        var token = EntityId.of(domainBuilder.id() + TREASURY);
        long sentinelTimestamp = domainBuilder.timestamp();
        setSentinelTimestamp(sentinelTimestamp);

        var timestamp = new AtomicLong(sentinelTimestamp - Duration.ofHours(1).toNanos());
        long interval = Duration.ofMinutes(3).toNanos();
        // first account balance file
        domainBuilder
                .accountBalanceFile()
                .customize(abf -> abf.consensusTimestamp(timestamp.get()))
                .persist();
        var oldAccountBalances = Lists.newArrayList(
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(100).id(new AccountBalance.Id(timestamp.get(), treasury)))
                        .get(),
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(200).id(new AccountBalance.Id(timestamp.get(), account)))
                        .get());
        var oldTokenBalances = Lists.newArrayList(domainBuilder
                .tokenBalance()
                .customize(tb -> tb.balance(20).id(new TokenBalance.Id(timestamp.get(), account, token)))
                .get());
        var expectedAccountBalances = new ArrayList<>(oldAccountBalances);
        var expectedTokenBalances = new ArrayList<>(oldTokenBalances);
        // second account balance file, skipped since it's less than 6 minutes after the first
        timestamp.addAndGet(interval);
        domainBuilder
                .accountBalanceFile()
                .customize(abf -> abf.consensusTimestamp(timestamp.get()))
                .persist();
        oldAccountBalances.addAll(List.of(
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(100).id(new AccountBalance.Id(timestamp.get(), treasury)))
                        .get(),
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(210).id(new AccountBalance.Id(timestamp.get(), account)))
                        .get()));
        oldTokenBalances.add(domainBuilder
                .tokenBalance()
                .customize(tb -> tb.balance(30).id(new TokenBalance.Id(timestamp.get(), account, token)))
                .get());
        // third account balance file, a snapshot should be generated
        timestamp.addAndGet(interval);
        domainBuilder
                .accountBalanceFile()
                .customize(abf -> abf.consensusTimestamp(timestamp.get()))
                .persist();
        var accountBalances = List.of(
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(100).id(new AccountBalance.Id(timestamp.get(), treasury)))
                        .get(),
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(210).id(new AccountBalance.Id(timestamp.get(), account)))
                        .get());
        var tokenBalance = domainBuilder
                .tokenBalance()
                .customize(tb -> tb.balance(30).id(new TokenBalance.Id(timestamp.get(), account, token)))
                .get();
        oldAccountBalances.addAll(accountBalances);
        oldTokenBalances.add(tokenBalance);
        expectedAccountBalances.addAll(accountBalances);
        expectedTokenBalances.add(tokenBalance);
        // the snapshot at sentinelTimestamp
        domainBuilder
                .accountBalanceFile()
                .customize(abf -> abf.consensusTimestamp(sentinelTimestamp))
                .persist();
        accountBalances = List.of(
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(100).id(new AccountBalance.Id(sentinelTimestamp, treasury)))
                        .persist(),
                domainBuilder
                        .accountBalance()
                        .customize(ab -> ab.balance(210).id(new AccountBalance.Id(sentinelTimestamp, account)))
                        .persist());
        tokenBalance = domainBuilder
                .tokenBalance()
                .customize(tb -> tb.balance(30).id(new TokenBalance.Id(sentinelTimestamp, account, token)))
                .persist();
        oldAccountBalances.addAll(accountBalances);
        oldTokenBalances.add(tokenBalance);
        expectedAccountBalances.addAll(accountBalances);
        expectedTokenBalances.add(tokenBalance);

        persistOldAccountBalances(oldAccountBalances);
        persistOldTokenBalances(oldTokenBalances);

        // when
        runMigration();

        // then
        waitForCompletion();
        assertSchema();
        assertThat(accountBalanceRepository.findAll()).containsExactlyInAnyOrderElementsOf(expectedAccountBalances);
        assertThat(tokenBalanceRepository.findAll()).containsExactlyInAnyOrderElementsOf(expectedTokenBalances);
    }

    @SneakyThrows
    private void addBalanceDeduplicateFunctions() {
        try (var is = migrationSql.getInputStream()) {
            jdbcTemplate.execute(StreamUtils.copyToString(is, StandardCharsets.UTF_8));
        }
    }

    private void assertSchema() {
        assertThat(tableExists("account_balance_old")).isFalse();
        assertThat(tableExists("token_balance_old")).isFalse();
    }

    private void persistOldAccountBalances(Collection<AccountBalance> accountBalances) {
        jdbcTemplate.batchUpdate(
                """
                        insert into account_balance_old (account_id, balance, consensus_timestamp)
                        values (?, ?, ?)
                        """,
                accountBalances,
                accountBalances.size(),
                (ps, accountBalance) -> {
                    ps.setLong(1, accountBalance.getId().getAccountId().getId());
                    ps.setLong(2, accountBalance.getBalance());
                    ps.setLong(3, accountBalance.getId().getConsensusTimestamp());
                });
    }

    private void persistOldTokenBalances(Collection<TokenBalance> tokenBalances) {
        jdbcTemplate.batchUpdate(
                """
                        insert into token_balance_old (account_id, balance, consensus_timestamp, token_id)
                        values (?, ?, ?, ?)
                        """,
                tokenBalances,
                tokenBalances.size(),
                (ps, tokenBalance) -> {
                    ps.setLong(1, tokenBalance.getId().getAccountId().getId());
                    ps.setLong(2, tokenBalance.getBalance());
                    ps.setLong(3, tokenBalance.getId().getConsensusTimestamp());
                    ps.setLong(4, tokenBalance.getId().getTokenId().getId());
                });
    }

    private MigrationProperties getMigrationProperties() {
        return mirrorProperties.getMigration().get("backfillAndDeduplicateBalanceMigration");
    }

    @SneakyThrows
    private void runMigration() {
        migration.doMigrate();
    }

    private void setSentinelTimestamp(long timestamp) {
        jdbcTemplate.update(
                "insert into account_balance_old (account_id, balance, consensus_timestamp) values (?, ?, ?)",
                -1L,
                timestamp,
                -1L);
    }
}
