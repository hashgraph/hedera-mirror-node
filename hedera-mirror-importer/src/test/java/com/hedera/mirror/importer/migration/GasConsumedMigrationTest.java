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

import com.hedera.mirror.common.domain.contract.ContractResult;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import com.hedera.mirror.importer.EnabledIfV1;
import com.hedera.mirror.importer.ImporterIntegrationTest;
import com.hedera.mirror.importer.repository.ContractActionRepository;
import com.hedera.mirror.importer.repository.ContractRepository;
import com.hedera.mirror.importer.repository.ContractResultRepository;
import com.hedera.mirror.importer.repository.EntityRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StreamUtils;

@EnabledIfV1
@Import(DisablePartitionMaintenanceConfiguration.class)
@RequiredArgsConstructor
@Tag("migration")
@TestPropertySource(properties = "spring.flyway.target=1.94.0")
public class GasConsumedMigrationTest extends ImporterIntegrationTest {

    private final JdbcTemplate jdbcTemplate;

    @Value("classpath:db/migration/v1/V1.94.0__add_gas_consumed_field.sql")
    private final Resource sql;

    private final TransactionTemplate transactionTemplate;

    private final ContractActionRepository contractActionRepository;
    private final ContractRepository contractRepository;
    private final ContractResultRepository contractResultRepository;
    private final EntityRepository entityRepository;

    @Test
    void empty() {
        runMigration();
        assertThat(contractActionRepository.findAll()).isEmpty();
        assertThat(contractRepository.findAll()).isEmpty();
        assertThat(contractResultRepository.findAll()).isEmpty();
        assertThat(entityRepository.findAll()).isEmpty();
    }

    @Test
    void migrate() {
        // Given
        final var ethTxCreate = domainBuilder.ethereumTransaction(true).persist();
        final var ethTxCall = domainBuilder.ethereumTransaction(true).persist();

        persistData(ethTxCreate, true);
        persistData(ethTxCall, false);

        // when
        runMigration();

        // then
        assertThat(contractResultRepository.findAll())
                .extracting(ContractResult::getGasConsumed)
                .containsExactly(53196L, 21100L);
    }

    private void persistData(EthereumTransaction ethTx, boolean topLevelCreate) {
        final var contract = domainBuilder
                .contract()
                .customize(c -> c.initcode(new byte[] {1, 0, 0, 0, 0, 1, 1, 1, 1}))
                .persist();
        domainBuilder
                .entity()
                .customize(e -> e.createdTimestamp(
                        topLevelCreate ? ethTx.getConsensusTimestamp() : ethTx.getConsensusTimestamp() + 1))
                .customize(e -> e.id(contract.getId()))
                .persist();
        domainBuilder
                .contractResult()
                .customize(cr -> cr.consensusTimestamp(ethTx.getConsensusTimestamp()))
                .customize(cr -> cr.contractId(contract.getId()))
                .persist();
        domainBuilder
                .contractAction()
                .customize(ca -> ca.consensusTimestamp(ethTx.getConsensusTimestamp()))
                .persist();
        domainBuilder
                .contractAction()
                .customize(ca -> ca.consensusTimestamp(ethTx.getConsensusTimestamp()))
                .persist();
    }

    @SneakyThrows
    private void runMigration() {
        try (final var is = sql.getInputStream()) {
            transactionTemplate.executeWithoutResult(s -> {
                try {
                    jdbcTemplate.update(StreamUtils.copyToString(is, StandardCharsets.UTF_8));
                } catch (final IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    @Data
    @Builder
    public static class MigrationContractResult {
        private Long consensusTimestamp;
        private Long amount;
        private byte[] bloom;
        private byte[] callResult;
        private long contractId;
        private List<Long> createdContractIds;
        private String errorMessage;
        private byte[] functionParameters;
        private byte[] functionResult;
        private Long gasLimit;
        private Long gasUsed;
        private EntityId payerAccountId;
        private EntityId senderId;
        private byte[] transactionHash;
        private Integer transactionIndex;
        private int transactionNonce;
        private Integer transactionResult;
    }
}
