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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.CryptoAllowance;
import com.hedera.mirror.common.domain.entity.CryptoAllowanceHistory;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.transaction.CryptoTransfer;
import com.hedera.mirror.importer.EnabledIfV1;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.config.Owner;
import com.hedera.mirror.importer.db.DBProperties;
import com.hedera.mirror.importer.parser.record.entity.sql.SqlEntityListener;
import com.hedera.mirror.importer.repository.CryptoAllowanceRepository;
import io.hypersistence.utils.hibernate.type.range.guava.PostgreSQLGuavaRangeType;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@EnabledIfV1
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Tag("migration")
class FixCryptoAllowanceAmountMigrationTest extends IntegrationTest {

    private static final String CREATE_MIGRATION_TABLE_DDL =
            """
            create table if not exists crypto_allowance_migration (
              amount           bigint    not null default 0,
              amount_granted   bigint    not null,
              owner            bigint    not null,
              payer_account_id bigint    not null,
              spender          bigint    not null,
              timestamp_range  int8range not null,
              primary key (owner, spender)
            )
            """;

    private final CryptoAllowanceRepository cryptoAllowanceRepository;
    private final DBProperties dbProperties;
    private final @Owner JdbcTemplate jdbcTemplate;
    private final MirrorProperties mirrorProperties;
    private final SqlEntityListener sqlEntityListener;
    private final TransactionTemplate transactionTemplate;

    private FixCryptoAllowanceAmountMigration migration;

    @BeforeEach
    void setup() {
        // Create migration object for each test case due to the various stateful cached fields in it
        migration = new FixCryptoAllowanceAmountMigration(dbProperties, mirrorProperties, jdbcTemplate);
    }

    @AfterEach
    void teardown() {
        jdbcTemplate.execute(CREATE_MIGRATION_TABLE_DDL);
    }

    @Test
    void empty() {
        // given, when
        runMigration();

        // then
        waitForCompletion();
        assertThat(migrationTableExists()).isFalse();
        assertThat(cryptoAllowanceRepository.findAll()).isEmpty();
        assertThat(findHistory(CryptoAllowance.class)).isEmpty();
    }

    @Test
    void migrate() {
        // given
        var history = convert(domainBuilder.cryptoAllowanceHistory().persist());
        var current1 = domainBuilder
                .cryptoAllowance()
                .customize(ca -> ca.owner(history.getOwner())
                        .payerAccountId(history.getPayerAccountId())
                        .spender(history.getSpender())
                        .timestampRange(Range.atLeast(history.getTimestampUpper())))
                .persist();
        var current2 = domainBuilder.cryptoAllowance().persist();
        var spender = EntityId.of(current1.getSpender(), EntityType.ACCOUNT);
        // A transfer using allowance
        var approvedTransfer1 = domainBuilder
                .cryptoTransfer()
                .customize(cr -> cr.amount(-5)
                        .entityId(current1.getOwner())
                        .isApproval(true)
                        .payerAccountId(spender))
                .persist();
        domainBuilder
                .cryptoTransfer()
                .customize(cr -> cr.amount(-3).entityId(current1.getOwner()).isApproval(false))
                .persist();
        // Another transfer using allowance
        long transferTimestamp = approvedTransfer1.getConsensusTimestamp() + FixCryptoAllowanceAmountMigration.INTERVAL;
        domainBuilder
                .cryptoTransfer()
                .customize(cr -> cr.amount(-7)
                        .consensusTimestamp(transferTimestamp)
                        .entityId(current1.getOwner())
                        .isApproval(true)
                        .payerAccountId(spender))
                .persist();
        // Sentinel crypto allowance in migration table which keeps the consensus timestamp when 1.84.2 is applied
        long lastTimestamp = transferTimestamp + 1000L;
        var sentinalCryptoAllowance = CryptoAllowance.builder()
                .amountGranted(0L)
                .owner(0)
                .payerAccountId(EntityId.EMPTY)
                .spender(0)
                .timestampRange(Range.atLeast(lastTimestamp))
                .build();
        // Persist the crypto allowances into the migration table
        persistAllMigrationCryptoAllowances(List.of(current1, current2, sentinalCryptoAllowance));

        // After lastTimestamp
        // Spend 8 using allowance
        var cryptoTransferBuilder = CryptoTransfer.builder()
                .entityId(current1.getOwner())
                .isApproval(true)
                .payerAccountId(spender);
        var cryptoTransfer1 = cryptoTransferBuilder
                .amount(-8)
                .consensusTimestamp(lastTimestamp + 1)
                .build();
        sqlEntityListener.onCryptoTransfer(cryptoTransfer1);
        var cryptoAllowanceUpdateBuilder = CryptoAllowance.builder()
                .owner(current1.getOwner())
                .payerAccountId(spender)
                .spender(current1.getSpender());
        var cryptoAllowanceUpdate1 =
                cryptoAllowanceUpdateBuilder.amount(-8).timestampRange(null).build();
        sqlEntityListener.onCryptoAllowance(cryptoAllowanceUpdate1);

        // New approve allowance
        var newCryptoAllowance = cryptoAllowanceUpdateBuilder
                .amount(200)
                .amountGranted(200L)
                .timestampRange(Range.atLeast(lastTimestamp + 2))
                .build();
        sqlEntityListener.onCryptoAllowance(newCryptoAllowance);
        // Crypto transfer using the new allowance
        var cryptoTransfer2 = cryptoTransferBuilder
                .amount(-1)
                .consensusTimestamp(lastTimestamp + 3)
                .build();
        sqlEntityListener.onCryptoTransfer(cryptoTransfer2);
        var cryptoAllowanceUpdate2 =
                cryptoAllowanceUpdateBuilder.amount(-1).timestampRange(null).build();
        sqlEntityListener.onCryptoAllowance(cryptoAllowanceUpdate2);

        // when, run the async migration and ingesting changes concurrently
        runMigration();
        completeFileAndCommit();

        // then
        waitForCompletion();
        assertThat(migrationTableExists()).isFalse();

        current1.setAmount(current1.getAmount() - 20);
        current1.setTimestampUpper(newCryptoAllowance.getTimestampLower());
        newCryptoAllowance.setAmount(199);
        assertThat(cryptoAllowanceRepository.findAll()).containsExactlyInAnyOrder(newCryptoAllowance, current2);
        assertThat(findHistory(CryptoAllowance.class)).containsExactlyInAnyOrder(current1, history);
    }

    @Test
    void migrationTableDoesntExist() {
        // given
        jdbcTemplate.execute("drop table crypto_allowance_migration");

        // when
        runMigration();

        // then
        waitForCompletion();
        assertThat(migrationTableExists()).isFalse();
        assertThat(cryptoAllowanceRepository.findAll()).isEmpty();
        assertThat(findHistory(CryptoAllowance.class)).isEmpty();
    }

    @SneakyThrows
    private void runMigration() {
        migration.doMigrate();
    }

    private void completeFileAndCommit() {
        var recordFile =
                domainBuilder.recordFile().customize(r -> r.sidecars(List.of())).get();
        transactionTemplate.executeWithoutResult(status -> sqlEntityListener.onEnd(recordFile));
    }

    private boolean isMigrationCompleted() {
        var actual = jdbcTemplate.queryForObject(
                "select (select checksum from flyway_schema_history where script = ?)",
                Integer.class,
                FixCryptoAllowanceAmountMigration.class.getName());
        return Objects.equals(actual, migration.getSuccessChecksum());
    }

    private boolean migrationTableExists() {
        try {
            jdbcTemplate.execute("select 'crypto_allowance_migration'::regclass");
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private void persistAllMigrationCryptoAllowances(List<CryptoAllowance> cryptoAllowances) {
        jdbcTemplate.batchUpdate(
                """
                        insert into crypto_allowance_migration (amount_granted, owner, payer_account_id, spender, timestamp_range)
                        values (?, ?, ?, ?, ?::int8range)
                        """,
                cryptoAllowances,
                cryptoAllowances.size(),
                (ps, cryptoAllowance) -> {
                    ps.setLong(1, cryptoAllowance.getAmountGranted());
                    ps.setLong(2, cryptoAllowance.getOwner());
                    ps.setLong(3, cryptoAllowance.getPayerAccountId().getId());
                    ps.setLong(4, cryptoAllowance.getSpender());
                    ps.setString(5, PostgreSQLGuavaRangeType.INSTANCE.asString(cryptoAllowance.getTimestampRange()));
                });
    }

    private void waitForCompletion() {
        await().atMost(Duration.ofSeconds(5))
                .pollDelay(Duration.ofMillis(100))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> assertThat(isMigrationCompleted()).isTrue());
    }

    private static CryptoAllowance convert(CryptoAllowanceHistory history) {
        return CryptoAllowance.builder()
                .amount(history.getAmount())
                .amountGranted(history.getAmountGranted())
                .owner(history.getOwner())
                .payerAccountId(history.getPayerAccountId())
                .spender(history.getSpender())
                .timestampRange(history.getTimestampRange())
                .build();
    }
}
