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

import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.contract.ContractResult;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import com.hedera.mirror.importer.EnabledIfV1;
import com.hedera.mirror.importer.ImporterIntegrationTest;
import com.hedera.mirror.importer.repository.ContractActionRepository;
import com.hedera.mirror.importer.repository.ContractRepository;
import com.hedera.mirror.importer.repository.ContractResultRepository;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
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
class GasConsumedMigrationTest extends ImporterIntegrationTest {

    private static final String REVERT_DDL = "alter table contract_result drop column gas_consumed";

    private final JdbcTemplate jdbcTemplate;

    @Value("classpath:db/migration/v1/V1.94.1__add_gas_consumed_field.sql")
    private final Resource sql;

    private final TransactionTemplate transactionTemplate;

    private final ContractActionRepository contractActionRepository;
    private final ContractRepository contractRepository;
    private final ContractResultRepository contractResultRepository;
    private final EntityRepository entityRepository;

    @AfterEach
    void teardown() {
        jdbcOperations.update(REVERT_DDL);
    }

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

        // run migration to create gas_consumed column
        runMigration();

        persistData(ethTxCreate, true);
        persistData(ethTxCall, false);

        // run migration to populate gas_consumed column
        runMigration();

        // then
        assertThat(contractResultRepository.findAll())
                .extracting(ContractResult::getGasConsumed)
                .containsExactly(53296L, 22224L);
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
        var migrateContractResult = createMigrationContractResult(
                ethTx.getConsensusTimestamp(), domainBuilder.entityId(), contract.getId(), domainBuilder);
        persistMigrationContractResult(migrateContractResult, jdbcTemplate);
        domainBuilder
                .contractAction()
                .customize(ca -> ca.consensusTimestamp(ethTx.getConsensusTimestamp()))
                .customize(ca -> ca.gasUsed(200L))
                .customize(ca -> ca.callDepth(0))
                .persist();
        domainBuilder
                .contractAction()
                .customize(ca -> ca.consensusTimestamp(ethTx.getConsensusTimestamp()))
                .customize(ca -> ca.callDepth(1))
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
        private Long amount;
        private byte[] bloom;
        private byte[] callResult;
        private Long consensusTimestamp;
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

    public static MigrationContractResult createMigrationContractResult(
            long timestamp, EntityId senderId, long contractId, DomainBuilder domainBuilder) {
        return MigrationContractResult.builder()
                .amount(1000L)
                .bloom(domainBuilder.bytes(256))
                .callResult(domainBuilder.bytes(512))
                .consensusTimestamp(timestamp)
                .contractId(contractId)
                .createdContractIds(List.of(domainBuilder.entityId().getId()))
                .errorMessage("")
                .functionParameters(domainBuilder.bytes(64))
                .functionResult(domainBuilder.bytes(128))
                .gasLimit(200L)
                .gasUsed(100L)
                .payerAccountId(domainBuilder.entityId())
                .senderId(senderId)
                .transactionHash(domainBuilder.bytes(32))
                .transactionIndex(1)
                .transactionNonce(0)
                .transactionResult(ResponseCodeEnum.SUCCESS_VALUE)
                .build();
    }

    public static void persistMigrationContractResult(final MigrationContractResult result, JdbcTemplate jdbcTemplate) {
        final String sql =
                """
                insert into contract_result
                (amount, bloom, call_result, consensus_timestamp, contract_id, created_contract_ids,
                error_message, function_parameters, function_result, gas_limit, gas_used,
                payer_account_id, sender_id, transaction_hash, transaction_index, transaction_nonce,
                transaction_result) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        jdbcTemplate.update(sql, ps -> {
            ps.setLong(1, result.getAmount());
            ps.setBytes(2, result.getBloom());
            ps.setBytes(3, result.getCallResult());
            ps.setLong(4, result.getConsensusTimestamp());
            ps.setLong(5, result.getContractId());
            final Long[] createdContractIdsArray =
                    result.getCreatedContractIds().toArray(new Long[0]);
            final Array createdContractIdsSqlArray =
                    ps.getConnection().createArrayOf("bigint", createdContractIdsArray);
            ps.setArray(6, createdContractIdsSqlArray);
            ps.setString(7, result.getErrorMessage());
            ps.setBytes(8, result.getFunctionParameters());
            ps.setBytes(9, result.getFunctionResult());
            ps.setLong(10, result.getGasLimit());
            ps.setLong(11, result.getGasUsed());
            ps.setObject(12, result.getPayerAccountId().getId());
            ps.setObject(13, result.getSenderId().getId());
            ps.setBytes(14, result.getTransactionHash());
            ps.setInt(15, result.getTransactionIndex());
            ps.setInt(16, result.getTransactionNonce());
            ps.setInt(17, result.getTransactionResult());
        });
    }
}
