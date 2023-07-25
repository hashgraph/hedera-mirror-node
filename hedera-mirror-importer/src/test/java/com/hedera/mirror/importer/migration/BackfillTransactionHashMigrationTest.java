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

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionHash;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.config.Owner;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import com.hedera.mirror.importer.repository.TransactionHashRepository;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Tag("migration")
class BackfillTransactionHashMigrationTest extends IntegrationTest {

    private static final long DEFAULT_START_TIMESTAMP = 10_000_000_000L;
    private static final String MIGRATION_NAME = "backfillTransactionHashMigration";

    private final EntityProperties entityProperties;
    private final @Owner JdbcTemplate jdbcTemplate;
    private final MirrorProperties mirrorProperties;
    private final TransactionHashRepository transactionHashRepository;
    private final Environment environment;

    private Set<TransactionType> defaultTransactionHashTypes;
    private BackfillTransactionHashMigration migration;
    private MigrationProperties migrationProperties;

    @Value("#{environment.acceptsProfiles('v2')}")
    private boolean isV2;

    @BeforeEach
    void setup() {
        defaultTransactionHashTypes = entityProperties.getPersist().getTransactionHashTypes();
        entityProperties.getPersist().setTransactionHash(true);
        migrationProperties = new MigrationProperties();
        migrationProperties
                .getParams()
                .put("startTimestamp", Long.valueOf(DEFAULT_START_TIMESTAMP).toString());
        mirrorProperties.getMigration().put(MIGRATION_NAME, migrationProperties);
        migration = new BackfillTransactionHashMigration(entityProperties, jdbcTemplate, mirrorProperties, environment);
    }

    @AfterEach
    void teardown() {
        entityProperties.getPersist().setTransactionHashTypes(defaultTransactionHashTypes);
    }

    @Test
    void empty() {
        runMigration();
        assertTransactionHashes(Collections.emptyList());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void migrate(boolean persistTransactionHash) {
        // given
        domainBuilder
                .transaction()
                .customize(
                        t -> t.consensusTimestamp(DEFAULT_START_TIMESTAMP - 1).itemizedTransfer(null))
                .persist();
        var expectedTransactionHashes = Stream.of(
                        domainBuilder
                                .transaction()
                                .customize(t -> t.consensusTimestamp(DEFAULT_START_TIMESTAMP)
                                        .itemizedTransfer(null))
                                .persist(),
                        domainBuilder
                                .transaction()
                                .customize(t -> t.consensusTimestamp(DEFAULT_START_TIMESTAMP + 1)
                                        .itemizedTransfer(null))
                                .persist())
                .filter(t -> persistTransactionHash)
                .map(Transaction::toTransactionHash)
                .toList();
        entityProperties.getPersist().setTransactionHash(persistTransactionHash);

        // when
        runMigration();

        // then
        assertTransactionHashes(expectedTransactionHashes);
    }

    @Test
    void migrateWhenTransactionHashTypesEmpty() {
        // given
        entityProperties.getPersist().setTransactionHashTypes(Collections.emptySet());
        var expected = Stream.of(
                        domainBuilder
                                .transaction()
                                .customize(t -> t.consensusTimestamp(DEFAULT_START_TIMESTAMP)
                                        .itemizedTransfer(null))
                                .persist(),
                        domainBuilder
                                .transaction()
                                .customize(t -> t.consensusTimestamp(DEFAULT_START_TIMESTAMP + 1)
                                        .type(TransactionType.CONSENSUSSUBMITMESSAGE.getProtoId())
                                        .itemizedTransfer(null))
                                .persist())
                .map(Transaction::toTransactionHash)
                .toList();

        // when
        runMigration();

        // then
        assertThat(transactionHashRepository.findAll()).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void migrateWhenSomeTransactionTypesExcluded() {
        // given
        var cryptoTransfer = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(DEFAULT_START_TIMESTAMP).itemizedTransfer(null))
                .persist();
        domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(DEFAULT_START_TIMESTAMP + 1)
                        .type(TransactionType.CONSENSUSSUBMITMESSAGE.getProtoId())
                        .itemizedTransfer(null))
                .persist();
        var expected = cryptoTransfer.toTransactionHash();

        // when
        runMigration();

        // then
        assertThat(transactionHashRepository.findAll()).containsExactly(expected);
    }

    @Test
    void migrateWhenTransactionTypesCustomized() {
        // given
        entityProperties
                .getPersist()
                .setTransactionHashTypes(EnumSet.complementOf(EnumSet.of(TransactionType.CRYPTOTRANSFER)));
        domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(DEFAULT_START_TIMESTAMP).itemizedTransfer(null))
                .persist();
        var consensusSubmitMessage = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(DEFAULT_START_TIMESTAMP + 1)
                        .type(TransactionType.CONSENSUSSUBMITMESSAGE.getProtoId())
                        .itemizedTransfer(null))
                .persist();
        var expected = consensusSubmitMessage.toTransactionHash();

        // when
        runMigration();

        // then
        assertThat(transactionHashRepository.findAll()).containsExactly(expected);
    }

    @Test
    void migrateWhenStartTimestampNotSet() {
        // given
        domainBuilder.transaction().customize(t -> t.itemizedTransfer(null)).persist();
        migrationProperties.getParams().clear();

        // when
        runMigration();

        // then
        assertThat(transactionHashRepository.findAll()).isEmpty();
    }

    @Test
    void migrateWithCaseInsensitiveStartTimestamp() {
        // given
        persistTransactionHash();
        migrationProperties.getParams().remove("startTimestamp");
        migrationProperties
                .getParams()
                .put("STARTTIMESTAMP", Long.valueOf(DEFAULT_START_TIMESTAMP).toString());
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(DEFAULT_START_TIMESTAMP).itemizedTransfer(null))
                .persist();
        var expected = transaction.toTransactionHash();

        // when
        runMigration();

        // then
        assertTransactionHashes(Collections.singleton(expected));
    }

    @Test
    void migrateWhenTableNotEmpty() {
        // given
        persistTransactionHash();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(DEFAULT_START_TIMESTAMP).itemizedTransfer(null))
                .persist();
        var expected = transaction.toTransactionHash();

        // when
        runMigration();

        // then
        assertTransactionHashes(Collections.singleton(expected));
    }

    private void persistTransactionHash() {
        var hashWrapper = domainBuilder.transactionHash();
        if (isV2) {
            hashWrapper.persist();
        } else {
            var hash = hashWrapper.get();
            TestUtils.insertIntoTransactionHashSharded(jdbcTemplate, hash);
        }
    }

    private void assertTransactionHashes(Collection<TransactionHash> expected) {
        assertThat(transactionHashRepository.findAll()).containsExactlyInAnyOrderElementsOf(expected);
    }

    @SneakyThrows
    private void runMigration() {
        migration.doMigrate();
    }
}
