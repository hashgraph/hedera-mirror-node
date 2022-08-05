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

import static com.hedera.mirror.common.domain.entity.EntityType.CONTRACT;
import static com.hedera.mirror.common.domain.entity.EntityType.TOPIC;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.hedera.mirror.common.domain.balance.AccountBalance;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.repository.AccountBalanceFileRepository;
import com.hedera.mirror.importer.repository.AccountBalanceRepository;
import com.hedera.mirror.importer.repository.CryptoTransferRepository;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.mirror.importer.repository.RecordFileRepository;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Tag("migration")
public class InitializeEntityBalanceMigrationTest extends IntegrationTest {

    private final AccountBalanceFileRepository accountBalanceFileRepository;
    private final AccountBalanceRepository accountBalanceRepository;
    private final CryptoTransferRepository cryptoTransferRepository;
    private final EntityRepository entityRepository;
    private final InitializeEntityBalanceMigration initializeEntityBalanceMigration;
    private final RecordFileRepository recordFileRepository;

    private Entity account;
    private Entity accountDeleted;
    private Entity contract;
    private RecordFile recordFile2;
    private AtomicLong timestamp;
    private Entity topic;

    @BeforeEach
    void beforeEach() {
        timestamp = new AtomicLong(0L);
    }

    @Test
    void empty() {
        initializeEntityBalanceMigration.doMigrate();
        assertThat(entityRepository.count()).isZero();
    }

    @Test
    void migrate() {
        // given
        setup();

        // when
        initializeEntityBalanceMigration.doMigrate();

        // then
        assertThat(entityRepository.findAll()).containsExactlyInAnyOrder(account, accountDeleted, contract, topic);
    }

    @Test
    void migrateWhenNoAccountBalance() {
        // given
        setup();
        accountBalanceFileRepository.deleteAll();
        accountBalanceRepository.deleteAll();

        // when
        initializeEntityBalanceMigration.doMigrate();

        // then
        account.setBalance(0L);
        contract.setBalance(0L);
        assertThat(entityRepository.findAll()).containsExactlyInAnyOrder(account, accountDeleted, contract, topic);
    }

    @Test
    void migrateWhenNoAccountBalanceAtOrBeforeLastRecordFile() {
        // given
        setup();
        accountBalanceFileRepository.prune(recordFile2.getConsensusEnd());
        accountBalanceRepository.prune(recordFile2.getConsensusEnd());

        // when
        initializeEntityBalanceMigration.doMigrate();

        // then
        account.setBalance(0L);
        contract.setBalance(0L);
        assertThat(entityRepository.findAll()).containsExactlyInAnyOrder(account, accountDeleted, contract, topic);
    }

    @Test
    void migrateWhenNoRecordFile() {
        // given
        setup();
        cryptoTransferRepository.deleteAll();
        recordFileRepository.deleteAll();

        // when
        initializeEntityBalanceMigration.doMigrate();

        // then
        account.setBalance(0L);
        contract.setBalance(0L);
        assertThat(entityRepository.findAll()).containsExactlyInAnyOrder(account, accountDeleted, contract, topic);
    }

    private void persistAccountBalance(long balance, EntityId entityId, long timestamp) {
        domainBuilder.accountBalance()
                .customize(a -> a.balance(balance).id(new AccountBalance.Id(timestamp, entityId)))
                .persist();
    }

    private void persistCryptoTransfer(long amount, long entityId, long timestamp) {
        domainBuilder.cryptoTransfer()
                .customize(c -> c.amount(amount).consensusTimestamp(timestamp).entityId(entityId))
                .persist();
    }

    private void setup() {
        account = domainBuilder.entity()
                .customize(e -> e.balance(0L).deleted(null).createdTimestamp(timestamp(Duration.ofSeconds(1L))))
                .persist();
        accountDeleted = domainBuilder.entity()
                .customize(e -> e.balance(0L).deleted(true).createdTimestamp(timestamp(Duration.ofSeconds(1))))
                .persist();
        topic = domainBuilder.entity()
                .customize(e -> e.balance(null).createdTimestamp(timestamp(Duration.ofSeconds(1))).type(TOPIC))
                .persist();

        // First record file, it's before account balance files
        domainBuilder.recordFile()
                .customize(r -> r.consensusStart(timestamp(Duration.ofMinutes(10)))
                        .consensusEnd(timestamp(Duration.ofSeconds(2))))
                .persist();

        // First account balance file
        long accountBalanceTimestamp1 = timestamp(Duration.ofMinutes(10));
        domainBuilder.accountBalanceFile().customize(a -> a.consensusTimestamp(accountBalanceTimestamp1)).persist();
        persistAccountBalance(500L, account.toEntityId(), accountBalanceTimestamp1);

        // The contract is created after the first account balance file
        contract = domainBuilder.entity()
                .customize(e -> e.balance(0L).createdTimestamp(timestamp(Duration.ofSeconds(1))).type(CONTRACT))
                .persist();

        // Second record file
        recordFile2 = domainBuilder.recordFile()
                .customize(r -> r.consensusStart(timestamp(Duration.ofSeconds(5)))
                        .consensusEnd(timestamp(Duration.ofSeconds(2))))
                .persist();
        long consensusStart = recordFile2.getConsensusStart();
        persistCryptoTransfer(10L, account.getId(), consensusStart);
        persistCryptoTransfer(-5L, account.getId(), consensusStart + 5L);
        persistCryptoTransfer(25L, contract.getId(), consensusStart + 10L);
        persistCryptoTransfer(20L, accountDeleted.getId(), consensusStart + 15L);

        // Second account balance file
        long accountBalanceTimestamp2 = timestamp(Duration.ofMinutes(2));
        domainBuilder.accountBalanceFile().customize(a -> a.consensusTimestamp(accountBalanceTimestamp2)).persist();
        persistAccountBalance(750L, account.toEntityId(), accountBalanceTimestamp2);
        persistAccountBalance(450L, contract.toEntityId(), accountBalanceTimestamp2);

        // Set expected balance
        account.setBalance(505L);
        contract.setBalance(25L);
    }

    private long timestamp(Duration delta) {
        return timestamp.addAndGet(delta.toNanos());
    }
}
