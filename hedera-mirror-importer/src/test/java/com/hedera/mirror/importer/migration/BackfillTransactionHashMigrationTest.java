package com.hedera.mirror.importer.migration;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.jdbc.core.JdbcTemplate;

import com.hedera.mirror.common.domain.transaction.TransactionHash;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.EnabledIfV1;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.config.Owner;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import com.hedera.mirror.importer.repository.TransactionHashRepository;

@EnabledIfV1
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Tag("migration")
class BackfillTransactionHashMigrationTest extends IntegrationTest {

    private static final long DEFAULT_START_TIMESTAMP = 10_000_000_000L;
    private static final String MIGRATION_NAME = "backfillTransactionHashMigration";

    private final EntityProperties entityProperties;
    private final @Owner JdbcTemplate jdbcTemplate;
    private final MirrorProperties mirrorProperties;
    private final TransactionHashRepository transactionHashRepository;

    private Set<TransactionType> defaultTransactionHashTypes;
    private BackfillTransactionHashMigration migration;
    private MigrationProperties migrationProperties;

    @BeforeEach
    void setup() {
        defaultTransactionHashTypes = entityProperties.getPersist().getTransactionHashTypes();
        entityProperties.getPersist().setTransactionHash(true);
        migrationProperties = new MigrationProperties();
        migrationProperties.getParams().put("startTimestamp", Long.valueOf(DEFAULT_START_TIMESTAMP).toString());
        mirrorProperties.getMigration().put(MIGRATION_NAME, migrationProperties);
        migration = new BackfillTransactionHashMigration(entityProperties, jdbcTemplate, mirrorProperties);
    }

    @AfterEach
    void teardown() {
        entityProperties.getPersist().setTransactionHashTypes(defaultTransactionHashTypes);
    }

    @Test
    void empty() {
        runMigration();
        assertThat(transactionHashRepository.findAll()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void migrate(boolean persistTransactionHash) {
        // given
        domainBuilder.transaction().customize(t -> t.consensusTimestamp(DEFAULT_START_TIMESTAMP - 1)).persist();
        var expectedTransactionHashes = Stream.of(
                        domainBuilder.transaction().customize(t -> t.consensusTimestamp(DEFAULT_START_TIMESTAMP)).persist(),
                        domainBuilder.transaction().customize(t -> t.consensusTimestamp(DEFAULT_START_TIMESTAMP + 1)).persist()
                )
                .filter(t -> persistTransactionHash)
                .map(t -> TransactionHash.builder()
                        .consensusTimestamp(t.getConsensusTimestamp())
                        .hash(t.getTransactionHash())
                        .payerAccountId(t.getPayerAccountId().getId())
                        .build())
                .toList();
        entityProperties.getPersist().setTransactionHash(persistTransactionHash);

        // when
        runMigration();

        // then
        assertThat(transactionHashRepository.findAll()).containsExactlyInAnyOrderElementsOf(expectedTransactionHashes);
    }

    @Test
    void migrateWhenEmptyTransactionHashTypes() {
        // given
        entityProperties.getPersist().setTransactionHashTypes(Collections.emptySet());
        domainBuilder.transaction().customize(t -> t.consensusTimestamp(DEFAULT_START_TIMESTAMP)).persist();

        // when
        runMigration();

        // then
        assertThat(transactionHashRepository.findAll()).isEmpty();
    }

    @Test
    void migrateWhenSomeTransactionTypesExcluded() {
        // given
        var cryptoTransfer = domainBuilder.transaction().customize(t -> t.consensusTimestamp(DEFAULT_START_TIMESTAMP))
                .persist();
        domainBuilder.transaction().customize(t -> t.consensusTimestamp(DEFAULT_START_TIMESTAMP + 1)
                        .type(TransactionType.CONSENSUSSUBMITMESSAGE.getProtoId()))
                .persist();
        var expected = TransactionHash.builder()
                .consensusTimestamp(cryptoTransfer.getConsensusTimestamp())
                .hash(cryptoTransfer.getTransactionHash())
                .payerAccountId(cryptoTransfer.getPayerAccountId().getId())
                .build();

        // when
        runMigration();

        // then
        assertThat(transactionHashRepository.findAll()).containsExactly(expected);
    }

    @Test
    void migrateWhenTransactionTypesCustomized() {
        // given
        entityProperties.getPersist().setTransactionHashTypes(EnumSet.complementOf(
                EnumSet.of(TransactionType.CRYPTOTRANSFER)));
        domainBuilder.transaction().customize(t -> t.consensusTimestamp(DEFAULT_START_TIMESTAMP)).persist();
        var consensusSubmitMessage = domainBuilder.transaction()
                .customize(t -> t.consensusTimestamp(DEFAULT_START_TIMESTAMP + 1)
                        .type(TransactionType.CONSENSUSSUBMITMESSAGE.getProtoId()))
                .persist();
        var expected = TransactionHash.builder()
                .consensusTimestamp(consensusSubmitMessage.getConsensusTimestamp())
                .hash(consensusSubmitMessage.getTransactionHash())
                .payerAccountId(consensusSubmitMessage.getPayerAccountId().getId())
                .build();

        // when
        runMigration();

        // then
        assertThat(transactionHashRepository.findAll()).containsExactly(expected);
    }

    @Test
    void migrateWhenStartTimestampNotSet() {
        // given
        domainBuilder.transaction().persist();
        migrationProperties.getParams().clear();

        // when
        runMigration();

        // then
        assertThat(transactionHashRepository.findAll()).isEmpty();
    }

    @Test
    void migrateWithCaseInsensitiveStartTimestamp() {
        // given
        domainBuilder.transactionHash().persist();
        migrationProperties.getParams().remove("startTimestamp");
        migrationProperties.getParams().put("STARTTIMESTAMP", Long.valueOf(DEFAULT_START_TIMESTAMP).toString());
        var transaction = domainBuilder.transaction()
                .customize(t -> t.consensusTimestamp(DEFAULT_START_TIMESTAMP))
                .persist();
        var expected = TransactionHash.builder()
                .consensusTimestamp(transaction.getConsensusTimestamp())
                .hash(transaction.getTransactionHash())
                .payerAccountId(transaction.getPayerAccountId().getId())
                .build();

        // when
        runMigration();

        // then
        assertThat(transactionHashRepository.findAll()).containsExactly(expected);
    }

    @Test
    void migrateWhenTableNotEmpty() {
        // given
        domainBuilder.transactionHash().persist();
        var transaction = domainBuilder.transaction()
                .customize(t -> t.consensusTimestamp(DEFAULT_START_TIMESTAMP))
                .persist();
        var expected = TransactionHash.builder()
                .consensusTimestamp(transaction.getConsensusTimestamp())
                .hash(transaction.getTransactionHash())
                .payerAccountId(transaction.getPayerAccountId().getId())
                .build();

        // when
        runMigration();

        // then
        assertThat(transactionHashRepository.findAll()).containsExactly(expected);
    }

    @SneakyThrows
    private void runMigration() {
        migration.doMigrate();
    }
}
