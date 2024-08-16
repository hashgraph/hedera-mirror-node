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
import static com.hedera.mirror.importer.parser.record.ethereum.EthereumTransactionTestUtility.RAW_TX_TYPE_1_CALL_DATA_OFFLOADED;
import static com.hedera.mirror.importer.parser.record.ethereum.EthereumTransactionTestUtility.loadEthereumTransactions;
import static com.hedera.mirror.importer.parser.record.ethereum.EthereumTransactionTestUtility.populateFileData;

import com.google.common.collect.Lists;
import com.hedera.mirror.common.domain.contract.ContractResult;
import com.hedera.mirror.common.domain.contract.ContractTransactionHash;
import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import com.hedera.mirror.common.domain.transaction.TransactionHash;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.ImporterIntegrationTest;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import com.hedera.mirror.importer.repository.ContractResultRepository;
import com.hedera.mirror.importer.repository.ContractTransactionHashRepository;
import com.hedera.mirror.importer.repository.EthereumTransactionRepository;
import com.hedera.mirror.importer.repository.TransactionHashRepository;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.shaded.org.apache.commons.lang.ArrayUtils;

@RequiredArgsConstructor
@Tag("migration")
class BackfillEthereumTransactionHashMigrationTest extends ImporterIntegrationTest {

    private final ContractResultRepository contractResultRepository;
    private final ContractTransactionHashRepository contractTransactionHashRepository;
    private final EthereumTransactionRepository ethereumTransactionRepository;
    private final EntityProperties entityProperties;
    private final BackfillEthereumTransactionHashMigration migration;
    private final TransactionHashRepository transactionHashRepository;

    @AfterEach
    void teardown() {
        entityProperties.getPersist().setTransactionHash(true);
    }

    @Test
    void empty() {
        runMigration();
        softly.assertThat(contractResultRepository.findAll()).isEmpty();
        softly.assertThat(contractTransactionHashRepository.findAll()).isEmpty();
        softly.assertThat(ethereumTransactionRepository.findAll()).isEmpty();
        softly.assertThat(transactionHashRepository.findAll()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void migrate(boolean persistTransactionHash) {
        // given
        entityProperties.getPersist().setTransactionHash(persistTransactionHash);
        populateFileData(jdbcOperations);
        var ethereumTransactions = loadEthereumTransactions();
        ethereumTransactions.forEach(t -> t.setHash(EMPTY_BYTE_ARRAY));
        ethereumTransactionRepository.saveAll(ethereumTransactions);
        var expectedEthereumTransactions = loadEthereumTransactions();

        // Add one with missing file so the service cannot calculate its hash
        var ethereumTransaction = domainBuilder
                .ethereumTransaction(false)
                .customize(t -> t.data(RAW_TX_TYPE_1_CALL_DATA_OFFLOADED).hash(EMPTY_BYTE_ARRAY))
                .persist();
        expectedEthereumTransactions.add(ethereumTransaction);

        // Add one with non-empty hash
        expectedEthereumTransactions.add(domainBuilder.ethereumTransaction(true).persist());

        var expectedHashes = expectedEthereumTransactions.stream()
                .collect(Collectors.toMap(EthereumTransaction::getConsensusTimestamp, EthereumTransaction::getHash));
        var expectedContractResults = persistContractResults(expectedHashes);
        var expectedContractTransactionHashes = persistContractTransactionHashes(expectedHashes);
        persistTransactionHashes();

        // when
        runMigration();

        // then
        softly.assertThat(contractResultRepository.findAll())
                .containsExactlyInAnyOrderElementsOf(expectedContractResults);
        softly.assertThat(contractTransactionHashRepository.findAll())
                .containsExactlyInAnyOrderElementsOf(expectedContractTransactionHashes);
        softly.assertThat(ethereumTransactionRepository.findAll())
                .containsExactlyInAnyOrderElementsOf(expectedEthereumTransactions);

        if (persistTransactionHash) {
            var expectedTransactionHashes = expectedEthereumTransactions.stream()
                    .filter(e -> ArrayUtils.isNotEmpty(e.getHash()))
                    .map(e -> TransactionHash.builder()
                            .consensusTimestamp(e.getConsensusTimestamp())
                            .hash(e.getHash())
                            .payerAccountId(e.getPayerAccountId().getId())
                            .build())
                    .toList();
            softly.assertThat(transactionHashRepository.findAll())
                    .containsExactlyInAnyOrderElementsOf(expectedTransactionHashes);
        } else {
            softly.assertThat(transactionHashRepository.findAll()).isEmpty();
        }
    }

    @Test
    void migrateWithMissingCallDataFile() {
        // given
        var ethereumTransaction = domainBuilder
                .ethereumTransaction(false)
                .customize(t -> t.data(RAW_TX_TYPE_1_CALL_DATA_OFFLOADED).hash(EMPTY_BYTE_ARRAY))
                .persist();
        var expectedContractResults = persistContractResults(Collections.emptyMap());
        var expectedContractTransactionHashes = persistContractTransactionHashes(Collections.emptyMap());

        // when
        runMigration();

        // then
        softly.assertThat(contractResultRepository.findAll())
                .containsExactlyInAnyOrderElementsOf(expectedContractResults);
        softly.assertThat(contractTransactionHashRepository.findAll())
                .containsExactlyInAnyOrderElementsOf(expectedContractTransactionHashes);
        softly.assertThat(ethereumTransactionRepository.findAll()).containsExactly(ethereumTransaction);
        softly.assertThat(transactionHashRepository.findAll()).isEmpty();
    }

    private List<ContractResult> persistContractResults(Map<Long, byte[]> expected) {
        return Lists.newArrayList(ethereumTransactionRepository.findAll()).stream()
                .map(e -> {
                    // Create and persist contract result from ethereum transactions in database so
                    // the hash column is consistent
                    var contractResult = domainBuilder
                            .contractResult()
                            .customize(cr -> cr.consensusTimestamp(e.getConsensusTimestamp())
                                    .transactionHash(e.getHash())
                                    .payerAccountId(e.getPayerAccountId()))
                            .persist();
                    // Set hash to the expected value
                    contractResult.setTransactionHash(
                            expected.getOrDefault(e.getConsensusTimestamp(), EMPTY_BYTE_ARRAY));
                    return contractResult;
                })
                .toList();
    }

    private List<ContractTransactionHash> persistContractTransactionHashes(Map<Long, byte[]> expected) {
        return Lists.newArrayList(ethereumTransactionRepository.findAll()).stream()
                .map(e -> {
                    // Create and persist contract transaction hash from ethereum transactions in database so
                    // the hash column is consistent
                    var contractTransactionHash = domainBuilder
                            .contractTransactionHash()
                            .customize(cr -> cr.consensusTimestamp(e.getConsensusTimestamp())
                                    .hash(e.getHash())
                                    .payerAccountId(e.getPayerAccountId().getId()))
                            .persist();
                    // Set hash to the expected value
                    contractTransactionHash.setHash(expected.getOrDefault(e.getConsensusTimestamp(), EMPTY_BYTE_ARRAY));
                    return contractTransactionHash;
                })
                .toList();
    }

    private void persistTransactionHashes() {
        if (!entityProperties.getPersist().shouldPersistTransactionHash(TransactionType.ETHEREUMTRANSACTION)) {
            return;
        }

        ethereumTransactionRepository.findAll().forEach(e -> {
            if (ArrayUtils.isNotEmpty(e.getHash())) {
                domainBuilder
                        .transactionHash()
                        .customize(t -> t.consensusTimestamp(e.getConsensusTimestamp())
                                .hash(e.getHash())
                                .payerAccountId(e.getPayerAccountId().getId()))
                        .persist();
            }
        });
    }

    @SneakyThrows
    private void runMigration() {
        migration.doMigrate();
    }
}
