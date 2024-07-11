/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

import com.hedera.mirror.common.domain.transaction.TransactionHash;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.ImporterIntegrationTest;
import com.hedera.mirror.importer.ImporterProperties;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import com.hedera.mirror.importer.repository.TransactionHashRepository;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

@RequiredArgsConstructor
@Tag("migration")
class BackfillEthereumTransactionHashMigrationTest extends ImporterIntegrationTest {

    private final EntityProperties entityProperties;
    private final JdbcTemplate jdbcTemplate;
    private final ImporterProperties importerProperties;
    private final TransactionHashRepository transactionHashRepository;

    private boolean defaultPersistTransactionHash;
    private Set<TransactionType> defaultTransactionHashTypes;
    private BackfillEthereumTransactionHashMigration migration;

    @BeforeEach
    void setup() {
        defaultPersistTransactionHash = entityProperties.getPersist().isTransactionHash();
        defaultTransactionHashTypes = entityProperties.getPersist().getTransactionHashTypes();
        entityProperties.getPersist().setTransactionHash(true);
        migration = new BackfillEthereumTransactionHashMigration(entityProperties, jdbcTemplate, importerProperties);
    }

    @AfterEach
    void teardown() {
        entityProperties.getPersist().setTransactionHash(defaultPersistTransactionHash);
        entityProperties.getPersist().setTransactionHashTypes(defaultTransactionHashTypes);
    }

    @Test
    void disabled() {
        // given
        domainBuilder.ethereumTransaction(true).persist();
        domainBuilder.ethereumTransaction(false).persist();
        entityProperties.getPersist().setTransactionHash(false);

        // when
        runMigration();

        // then
        assertThat(transactionHashRepository.findAll()).isEmpty();
    }

    @Test
    void empty() {
        runMigration();
        assertThat(transactionHashRepository.findAll()).isEmpty();
    }

    @Test
    void ethererumTransactionExcluded() {
        // given
        domainBuilder.ethereumTransaction(true).persist();
        domainBuilder.ethereumTransaction(false).persist();
        entityProperties
                .getPersist()
                .setTransactionHashTypes(EnumSet.complementOf(EnumSet.of(TransactionType.ETHEREUMTRANSACTION)));

        // when
        runMigration();

        // then
        assertThat(transactionHashRepository.findAll()).isEmpty();
    }

    @Test
    void migrate() {
        // given
        var ethereumTransactions = List.of(
                domainBuilder.ethereumTransaction(true).persist(),
                domainBuilder.ethereumTransaction(false).persist());
        var expected = ethereumTransactions.stream()
                .map(t -> TransactionHash.builder()
                        .consensusTimestamp(t.getConsensusTimestamp())
                        .hash(t.getHash())
                        .payerAccountId(t.getPayerAccountId().getId())
                        .build())
                .toList();

        // when
        runMigration();

        // then
        assertThat(transactionHashRepository.findAll()).containsExactlyInAnyOrderElementsOf(expected);
    }

    @SneakyThrows
    private void runMigration() {
        migration.doMigrate();
    }
}
