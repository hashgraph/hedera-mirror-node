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

import static com.hedera.mirror.common.domain.entity.EntityType.CONTRACT;
import static com.hedera.mirror.common.domain.entity.EntityType.TOPIC;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.balance.AccountBalance;
import com.hedera.mirror.common.domain.balance.AccountBalanceFile;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.transaction.ErrataType;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.config.Owner;
import com.hedera.mirror.importer.repository.AccountBalanceFileRepository;
import com.hedera.mirror.importer.repository.AccountBalanceRepository;
import com.hedera.mirror.importer.repository.CryptoTransferRepository;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Tag("migration")
class InitializeEntityBalanceMigrationTest extends IntegrationTest {

    private static final String DELETE_ACCOUNT_BALANCE_SQL =
            "delete from account_balance where consensus_timestamp <= ?";
    private static final String DELETE_ACCOUNT_BALANCE_FILE_SQL =
            "delete from account_balance_file where consensus_timestamp <= ?";

    private final AccountBalanceFileRepository accountBalanceFileRepository;
    private final AccountBalanceRepository accountBalanceRepository;
    private final CryptoTransferRepository cryptoTransferRepository;
    private final EntityRepository entityRepository;
    private final @Owner JdbcTemplate jdbcTemplate;
    private final MirrorProperties mirrorProperties;
    private final RecordFileRepository recordFileRepository;
    private InitializeEntityBalanceMigration migration;
    private Entity account;
    private AccountBalanceFile accountBalanceFile1;
    private AccountBalanceFile accountBalanceFile2;
    private Entity accountDeleted;
    private Entity contract;
    private RecordFile recordFile2;
    private AtomicLong timestamp;
    private Entity topic;

    @BeforeEach
    void beforeEach() {
        timestamp = new AtomicLong(0L);
        migration = new InitializeEntityBalanceMigration(
                jdbcOperations, mirrorProperties, accountBalanceFileRepository, recordFileRepository);
    }

    @Test
    void checksum() {
        assertThat(migration.getChecksum()).isEqualTo(4);
    }

    @Test
    void empty() {
        migration.doMigrate();
        assertThat(entityRepository.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void migrate(boolean hasSyntheticAccountBalanceFile) {
        // given
        setup();
        if (hasSyntheticAccountBalanceFile) {
            // Remove the non-synthetic account balance file and add a synthetic file before latest record file's
            // consensus end
            accountBalanceFileRepository.deleteById(accountBalanceFile2.getConsensusTimestamp());
            // A synthetic account balance file
            long syntheticBalanceTimestamp = recordFile2.getConsensusEnd() - 1;
            domainBuilder
                    .accountBalanceFile()
                    .customize(
                            a -> a.consensusTimestamp(syntheticBalanceTimestamp).synthetic(true))
                    .persist();
        }

        // when
        migration.doMigrate();

        // then
        setExpectedBalance();
        assertThat(entityRepository.findAll()).containsExactlyInAnyOrder(account, accountDeleted, contract, topic);
    }

    @Test
    void migrateWhenAccountBalanceFileHasOffset() {
        // given
        setup();
        // The account balance file has a +5ns offset, so the crypto transfer to account at +2ns shouldn't be included
        // in the account's balance change
        // Note the delete then insert is a workaround for citus
        accountBalanceFileRepository.deleteById(accountBalanceFile1.getConsensusTimestamp());
        accountBalanceFile1.setTimeOffset(5);
        accountBalanceFileRepository.save(accountBalanceFile1);
        persistCryptoTransfer(300, account.getId(), null, accountBalanceFile1.getConsensusTimestamp() + 2L);

        // when
        migration.doMigrate();

        // then
        setExpectedBalance();
        assertThat(entityRepository.findAll()).containsExactlyInAnyOrder(account, accountDeleted, contract, topic);
    }

    @Test
    void migrateWhenNoAccountBalance() {
        // given
        setup();
        accountBalanceFileRepository.deleteAll();
        accountBalanceRepository.deleteAll();

        // when
        migration.doMigrate();

        // then
        assertThat(entityRepository.findAll()).containsExactlyInAnyOrder(account, accountDeleted, contract, topic);
    }

    @Test
    void migrateWhenNoAccountBalanceAtOrBeforeLastRecordFile() {
        // given
        setup();
        jdbcTemplate.update(DELETE_ACCOUNT_BALANCE_SQL, recordFile2.getConsensusEnd());
        jdbcTemplate.update(DELETE_ACCOUNT_BALANCE_FILE_SQL, recordFile2.getConsensusEnd());

        // when
        migration.doMigrate();

        // then
        assertThat(entityRepository.findAll()).containsExactlyInAnyOrder(account, accountDeleted, contract, topic);
    }

    @Test
    void migrateWhenNoRecordFile() {
        // given
        setup();
        cryptoTransferRepository.deleteAll();
        recordFileRepository.deleteAll();

        // when
        migration.doMigrate();

        // then
        assertThat(entityRepository.findAll()).containsExactlyInAnyOrder(account, accountDeleted, contract, topic);
    }

    @Test
    void migrateWhenNoEntities() {
        // given
        setup();
        entityRepository.deleteAll();

        // when
        migration.doMigrate();

        // then
        setExpectedBalance();
        account.setDeleted(false); // We only know for sure that entities in the balance file are not deleted
        accountDeleted.setDeleted(null);
        contract.setDeleted(null);
        assertThat(entityRepository.findAll())
                .usingRecursiveFieldByFieldElementComparatorOnFields(
                        "balance", "deleted", "id", "num", "realm", "shard")
                .containsExactlyInAnyOrder(account, accountDeleted, contract)
                .allSatisfy(e -> assertThat(e)
                        .returns(Range.atLeast(0L), Entity::getTimestampRange)
                        .returns(EntityType.UNKNOWN, Entity::getType));
    }

    @Test
    @Transactional
    void onEndWhenNotFirstFile() {
        // given
        setup();
        // when
        migration.onEnd(accountBalanceFile2);

        // then
        assertThat(entityRepository.findAll()).containsExactlyInAnyOrder(account, accountDeleted, contract, topic);
    }

    @Test
    @Transactional
    void onEndEarlyReturn() {
        // given
        var transactionManager = new DataSourceTransactionManager(Objects.requireNonNull(jdbcTemplate.getDataSource()));
        var transactionTemplate = new TransactionTemplate(transactionManager);
        setup();
        accountBalanceFileRepository.deleteById(accountBalanceFile2.getConsensusTimestamp());

        transactionTemplate.executeWithoutResult(s -> migration.onEnd(accountBalanceFile1));

        // then
        assertThat(entityRepository.findAll()).containsExactlyInAnyOrder(account, accountDeleted, contract, topic);

        // given
        long accountBalanceTimestamp3 = timestamp(Duration.ofMinutes(10));
        var accountBalanceFile3 = domainBuilder
                .accountBalanceFile()
                .customize(a -> a.consensusTimestamp(accountBalanceTimestamp3))
                .persist();
        // when
        migration.onEnd(accountBalanceFile3);

        // then
        assertThat(entityRepository.findAll()).containsExactlyInAnyOrder(account, accountDeleted, contract, topic);
    }

    @Test
    @Transactional
    void onEndWhenNoRecordFileAfterTimestamp() {
        // given
        setup();
        recordFileRepository.deleteById(recordFile2.getConsensusEnd());

        // when
        migration.onEnd(accountBalanceFile2);

        // then
        assertThat(entityRepository.findAll()).containsExactlyInAnyOrder(account, accountDeleted, contract, topic);
    }

    @Test
    @Transactional
    void onEnd() {
        // given
        setup();
        accountBalanceFileRepository.deleteById(accountBalanceFile2.getConsensusTimestamp());

        // when
        migration.onEnd(accountBalanceFile1);

        // then
        assertThat(entityRepository.findAll()).containsExactlyInAnyOrder(account, accountDeleted, contract, topic);
    }

    private void persistAccountBalance(long balance, EntityId entityId, long timestamp) {
        domainBuilder
                .accountBalance()
                .customize(a -> a.balance(balance).id(new AccountBalance.Id(timestamp, entityId)))
                .persist();
    }

    private void persistCryptoTransfer(long amount, long entityId, ErrataType errata, long timestamp) {
        domainBuilder
                .cryptoTransfer()
                .customize(c -> c.amount(amount)
                        .consensusTimestamp(timestamp)
                        .entityId(entityId)
                        .errata(errata))
                .persist();
    }

    private void setExpectedBalance() {
        account.setBalance(505L);
        account.setBalanceTimestamp(recordFile2.getConsensusEnd());
        accountDeleted.setBalance(20L);
        contract.setBalance(25L);
    }

    private void setup() {
        setupForFirstAccountBalanceFile();

        // Second record file
        recordFile2 = domainBuilder
                .recordFile()
                .customize(r -> r.consensusStart(timestamp(Duration.ofSeconds(5)))
                        .consensusEnd(timestamp(Duration.ofSeconds(2))))
                .persist();
        long consensusStart = recordFile2.getConsensusStart();
        persistCryptoTransfer(10L, account.getId(), null, consensusStart);
        persistCryptoTransfer(-5L, account.getId(), ErrataType.INSERT, consensusStart + 5L);
        persistCryptoTransfer(25L, contract.getId(), null, consensusStart + 10L);
        persistCryptoTransfer(10L, contract.getId(), ErrataType.DELETE, consensusStart + 10L);
        persistCryptoTransfer(20L, accountDeleted.getId(), null, consensusStart + 15L);

        // Second account balance file
        long accountBalanceTimestamp2 = timestamp(Duration.ofMinutes(2));
        accountBalanceFile2 = domainBuilder
                .accountBalanceFile()
                .customize(a -> a.consensusTimestamp(accountBalanceTimestamp2))
                .persist();
        persistAccountBalance(750L, account.toEntityId(), accountBalanceTimestamp2);
        persistAccountBalance(450L, contract.toEntityId(), accountBalanceTimestamp2);
    }

    private void setupForFirstAccountBalanceFile() {
        account = domainBuilder
                .entity()
                .customize(e -> e.balance(0L)
                        .balanceTimestamp(null)
                        .deleted(null)
                        .createdTimestamp(timestamp(Duration.ofSeconds(1L))))
                .persist();
        accountDeleted = domainBuilder
                .entity()
                .customize(e -> e.balance(0L).deleted(true).createdTimestamp(timestamp(Duration.ofSeconds(1))))
                .persist();
        topic = domainBuilder
                .entity()
                .customize(e -> e.balance(null)
                        .balanceTimestamp(null)
                        .createdTimestamp(timestamp(Duration.ofSeconds(1)))
                        .type(TOPIC))
                .persist();

        // First record file, it's before account balance files
        domainBuilder
                .recordFile()
                .customize(r -> r.consensusStart(timestamp(Duration.ofMinutes(10)))
                        .consensusEnd(timestamp(Duration.ofSeconds(2))))
                .persist();

        // First account balance file
        long accountBalanceTimestamp1 = timestamp(Duration.ofMinutes(10));
        accountBalanceFile1 = domainBuilder
                .accountBalanceFile()
                .customize(a -> a.consensusTimestamp(accountBalanceTimestamp1))
                .persist();
        persistAccountBalance(500L, account.toEntityId(), accountBalanceTimestamp1);

        // A crypto transfer already accounted in the first account balance file, so it shouldn't be included to
        // initialize entity balance
        persistCryptoTransfer(300L, account.getId(), null, accountBalanceTimestamp1);

        // The contract is created after the first account balance file
        contract = domainBuilder
                .entity()
                .customize(e -> e.balance(0L)
                        .balanceTimestamp(accountBalanceTimestamp1 + 10)
                        .createdTimestamp(timestamp(Duration.ofSeconds(1)))
                        .type(CONTRACT))
                .persist();
    }

    private long timestamp(Duration delta) {
        return timestamp.addAndGet(delta.toNanos());
    }
}
