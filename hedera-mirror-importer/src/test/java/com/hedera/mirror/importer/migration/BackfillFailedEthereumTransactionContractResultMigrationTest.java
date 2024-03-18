/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.importer.migration.GasConsumedMigrationTest.createMigrationContractResult;
import static com.hedera.mirror.importer.migration.GasConsumedMigrationTest.persistMigrationContractResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONSENSUS_GAS_EXHAUSTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.WRONG_NONCE;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.DisableRepeatableSqlMigration;
import com.hedera.mirror.importer.EnabledIfV1;
import com.hedera.mirror.importer.ImporterIntegrationTest;
import com.hedera.mirror.importer.migration.GasConsumedMigrationTest.MigrationContractResult;
import com.hedera.mirror.importer.repository.ContractResultRepository;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.StreamUtils;

@EnabledIfV1
@Import(DisablePartitionMaintenanceConfiguration.class)
@RequiredArgsConstructor
@Tag("migration")
@TestPropertySource(properties = "spring.flyway.target=1.79.0")
@DisableRepeatableSqlMigration
class BackfillFailedEthereumTransactionContractResultMigrationTest extends ImporterIntegrationTest {

    private final ContractResultRepository contractResultRepository;

    private final JdbcTemplate jdbcTemplate;

    @Value("classpath:db/migration/v1/V1.80.0__backfill_ethereum_transaction_contract_result.sql")
    private final Resource sql;

    @Test
    void empty() {
        runMigration();
        assertThat(contractResultRepository.count()).isZero();
    }

    @Test
    void migrate() {
        // given
        var ethTx1 = domainBuilder.ethereumTransaction(true).persist();

        var migrationContractResult = createMigrationContractResult(
                ethTx1.getConsensusTimestamp(),
                domainBuilder.entityId(),
                domainBuilder.entityId().getId(),
                null,
                domainBuilder);
        persistMigrationContractResult(migrationContractResult, jdbcTemplate);

        var transaction1 = transaction(ethTx1.getConsensusTimestamp(), SUCCESS, ethTx1.getPayerAccountId(), 0, 0);
        var ethTx2 = domainBuilder.ethereumTransaction(false).persist();
        var transaction2 =
                transaction(ethTx2.getConsensusTimestamp(), DUPLICATE_TRANSACTION, ethTx2.getPayerAccountId(), 0, 0);
        var ethTx3 = domainBuilder.ethereumTransaction(true).persist();
        var transaction3 = transaction(ethTx3.getConsensusTimestamp(), WRONG_NONCE, ethTx3.getPayerAccountId(), 0, 0);
        var ethTx4 = domainBuilder.ethereumTransaction(false).persist();
        var transaction4 =
                transaction(ethTx4.getConsensusTimestamp(), CONSENSUS_GAS_EXHAUSTED, ethTx4.getPayerAccountId(), 4, 3);
        var ethTx5 = domainBuilder.ethereumTransaction(true).persist();
        var transaction5 =
                transaction(ethTx5.getConsensusTimestamp(), INVALID_ACCOUNT_ID, ethTx5.getPayerAccountId(), 5, 0);
        persistTransactions(List.of(transaction1, transaction2, transaction3, transaction4, transaction5));

        // when
        runMigration();

        // then
        var expectedContractResult4 = toMigrationContractResult(DomainUtils.EMPTY_BYTE_ARRAY, ethTx4, transaction4);
        var expectedContractResult5 = toMigrationContractResult(ethTx5.getCallData(), ethTx5, transaction5);
        assertThat(findAllContractResults())
                .containsExactlyInAnyOrder(migrationContractResult, expectedContractResult4, expectedContractResult5);
    }

    private MigrationContractResult toMigrationContractResult(
            byte[] callData, EthereumTransaction ethereumTransaction, Transaction transaction) {
        return MigrationContractResult.builder()
                .callResult(DomainUtils.EMPTY_BYTE_ARRAY)
                .contractId(0)
                .consensusTimestamp(ethereumTransaction.getConsensusTimestamp())
                .createdContractIds(null)
                .functionParameters(callData)
                .gasLimit(ethereumTransaction.getGasLimit())
                .gasUsed(0L)
                .payerAccountId(ethereumTransaction.getPayerAccountId())
                .transactionHash(ethereumTransaction.getHash())
                .transactionIndex(transaction.getIndex())
                .transactionNonce(transaction.getNonce())
                .transactionResult(transaction.getResult())
                .build();
    }

    private Transaction transaction(
            long consensusTimestamp, ResponseCodeEnum result, EntityId payerAccountId, int index, int nonce) {
        Transaction transaction = new Transaction();
        transaction.setConsensusTimestamp(consensusTimestamp);
        transaction.setIndex(index);
        transaction.setNonce(nonce);
        transaction.setPayerAccountId(payerAccountId);
        transaction.setResult(result.getNumber());
        transaction.setType(TransactionType.ETHEREUMTRANSACTION.getProtoId());
        transaction.setValidStartNs(consensusTimestamp - 10);
        return transaction;
    }

    private void persistTransactions(List<Transaction> transactions) {
        jdbcTemplate.batchUpdate(
                """
                insert into transaction (consensus_timestamp, index, nonce, payer_account_id, result, type,
                valid_start_ns) values (?, ?, ?, ?, ?, ?, ?)
                """,
                transactions,
                transactions.size(),
                (ps, transaction) -> {
                    ps.setLong(1, transaction.getConsensusTimestamp());
                    ps.setLong(2, transaction.getIndex());
                    ps.setLong(3, transaction.getNonce());
                    ps.setLong(4, transaction.getPayerAccountId().getId());
                    ps.setLong(5, transaction.getResult());
                    ps.setShort(6, transaction.getType().shortValue());
                    ps.setLong(7, transaction.getValidStartNs());
                });
    }

    private List<MigrationContractResult> findAllContractResults() {
        return jdbcTemplate.query(
                """
                        select amount, bloom, call_result, consensus_timestamp, contract_id, created_contract_ids,
                        error_message, failed_initcode, function_parameters, function_result, gas_limit, gas_used, payer_account_id,
                        sender_id, transaction_hash, transaction_index, transaction_nonce,
                        transaction_result from contract_result
                        """,
                (rs, rowNum) -> {
                    long payerAccountId = rs.getLong("payer_account_id");
                    Array createdContractIds = rs.getArray("created_contract_ids");

                    return MigrationContractResult.builder()
                            .amount(rs.getLong("amount") == 0 ? null : rs.getLong("amount"))
                            .bloom(rs.getBytes("bloom"))
                            .callResult(rs.getBytes("call_result"))
                            .consensusTimestamp(rs.getLong("consensus_timestamp"))
                            .contractId(rs.getLong("contract_id"))
                            .createdContractIds(
                                    createdContractIds == null ? null : List.of((Long[]) createdContractIds.getArray()))
                            .errorMessage(rs.getString("error_message"))
                            .failedInitcode(rs.getBytes("failed_initcode"))
                            .functionParameters(rs.getBytes("function_parameters"))
                            .functionResult(rs.getBytes("function_result"))
                            .gasLimit(rs.getLong("gas_limit"))
                            .gasUsed(rs.getLong("gas_used"))
                            .payerAccountId(EntityId.of(payerAccountId))
                            .senderId(rs.getLong("sender_id") == 0 ? null : EntityId.of(rs.getLong("sender_id")))
                            .transactionHash(rs.getBytes("transaction_hash"))
                            .transactionIndex(rs.getInt("transaction_index"))
                            .transactionNonce(rs.getInt("transaction_nonce"))
                            .transactionResult(rs.getInt("transaction_result"))
                            .build();
                });
    }

    @SneakyThrows
    private void runMigration() {
        try (var is = sql.getInputStream()) {
            jdbcTemplate.update(StreamUtils.copyToString(is, StandardCharsets.UTF_8));
        }
    }
}
