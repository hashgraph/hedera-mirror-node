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

import static com.hedera.mirror.common.util.DomainUtils.EMPTY_BYTE_ARRAY;
import static com.hedera.mirror.importer.parser.domain.RecordItemBuilder.RAW_TX_TYPE_1_CALL_DATA_OFFLOADED;
import static com.hedera.mirror.importer.parser.record.ethereum.EthereumTransactionIntegrationTestUtility.loadEthereumTransactions;
import static com.hedera.mirror.importer.parser.record.ethereum.EthereumTransactionIntegrationTestUtility.populateFileData;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.importer.ImporterIntegrationTest;
import com.hedera.mirror.importer.repository.EthereumTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
@Tag("migration")
class BackfillEthereumTransactionHashMigrationTest extends ImporterIntegrationTest {

    private final EthereumTransactionRepository ethereumTransactionRepository;
    private final BackfillEthereumTransactionHashMigration migration;

    @Test
    void empty() {
        runMigration();
        assertThat(ethereumTransactionRepository.findAll()).isEmpty();
    }

    @Test
    void migrate() {
        // given
        populateFileData(jdbcOperations);
        var ethereumTransactions = loadEthereumTransactions();
        ethereumTransactions.forEach(t -> t.setHash(EMPTY_BYTE_ARRAY));
        ethereumTransactionRepository.saveAll(ethereumTransactions);
        var expected = loadEthereumTransactions();

        // Add one with missing file so the service cannot calculate its hash
        var ethereumTransaction = domainBuilder
                .ethereumTransaction(false)
                .customize(t -> t.data(RAW_TX_TYPE_1_CALL_DATA_OFFLOADED).hash(EMPTY_BYTE_ARRAY))
                .persist();
        expected.add(ethereumTransaction);

        // when
        runMigration();

        // then
        assertThat(ethereumTransactionRepository.findAll()).containsExactlyInAnyOrderElementsOf(expected);
    }

    @SneakyThrows
    private void runMigration() {
        migration.doMigrate();
    }
}
