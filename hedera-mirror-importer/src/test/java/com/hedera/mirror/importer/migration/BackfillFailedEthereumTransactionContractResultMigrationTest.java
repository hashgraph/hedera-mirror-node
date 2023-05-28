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

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONSENSUS_GAS_EXHAUSTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.WRONG_NONCE;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.contract.ContractResult;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.EnabledIfV1;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.repository.ContractResultRepository;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.StreamUtils;

@EnabledIfV1
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Tag("migration")
@TestPropertySource(properties = "spring.flyway.target=1.79.0")
class BackfillFailedEthereumTransactionContractResultMigrationTest extends IntegrationTest {

    private final ContractResultRepository contractResultRepository;

    private final JdbcTemplate jdbcTemplate;

    @Value("classpath:db/migration/v1/V1.80.0__backfill_ethereum_transaction_contract_result.sql")
    private final Resource sql;

    private static final EntityId NODE_ACCOUNT_ID = EntityId.of(0, 0, 3, EntityType.ACCOUNT);
    private static final EntityId PAYER_ID = EntityId.of(0, 0, 10001, EntityType.ACCOUNT);

    @Test
    void empty() {
        runMigration();
        assertThat(contractResultRepository.count()).isZero();
    }

    @Test
    void migrate() {
        // given
        var ethTx1 = domainBuilder.ethereumTransaction(true).persist();
        var contractResult1 = domainBuilder
                .contractResult()
                .customize(cr -> cr.consensusTimestamp(ethTx1.getConsensusTimestamp()))
                .persist();
        var transaction1 = transaction(ethTx1.getConsensusTimestamp(), SUCCESS, ethTx1.getPayerAccountId(), 0, 0);
        var ethTx2 = domainBuilder.ethereumTransaction(false).persist();
        var transaction2 = transaction(
                ethTx2.getConsensusTimestamp(), DUPLICATE_TRANSACTION, ethTx2.getPayerAccountId(), 0, 0);
        var ethTx3 = domainBuilder.ethereumTransaction(true).persist();
        var transaction3 =
                transaction(ethTx3.getConsensusTimestamp(), WRONG_NONCE, ethTx3.getPayerAccountId(), 0, 0);
        var ethTx4 = domainBuilder.ethereumTransaction(false).persist();
        var transaction4 = transaction(
                ethTx4.getConsensusTimestamp(), CONSENSUS_GAS_EXHAUSTED, ethTx4.getPayerAccountId(), 4, 3);
        var ethTx5 = domainBuilder.ethereumTransaction(true).persist();
        var transaction5 =
                transaction(ethTx5.getConsensusTimestamp(), INVALID_ACCOUNT_ID, ethTx5.getPayerAccountId(), 5, 0);
        persistTransactions(List.of(transaction1, transaction2, transaction3, transaction4, transaction5));

        // when
        runMigration();

        // then
        var expectedContractResult4 = toContractResult(DomainUtils.EMPTY_BYTE_ARRAY, ethTx4, transaction4);
        var expectedContractResult5 = toContractResult(ethTx5.getCallData(), ethTx5, transaction5);
        assertThat(contractResultRepository.findAll())
                .containsExactlyInAnyOrder(contractResult1, expectedContractResult4, expectedContractResult5);
    }

    private ContractResult toContractResult(
            byte[] callData, EthereumTransaction ethereumTransaction, Transaction transaction) {
        return ContractResult.builder()
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
            long consensusTimestamp,
            ResponseCodeEnum result,
            EntityId payerAccountId,
            int index,
            int nonce) {
        Transaction transaction = new Transaction();
        transaction.setConsensusTimestamp(consensusTimestamp);
        transaction.setIndex(index);
        transaction.setNodeAccountId(NODE_ACCOUNT_ID);
        transaction.setNonce(nonce);
        transaction.setPayerAccountId(payerAccountId);
        transaction.setResult(result.getNumber());
        transaction.setType(TransactionType.ETHEREUMTRANSACTION.getProtoId());
        transaction.setValidStartNs(consensusTimestamp - 10);
        return transaction;
    }

    private void persistTransactions(List<Transaction> transactions) {
        for (Transaction transaction : transactions) {
            jdbcTemplate.update(
                    "insert into transaction (consensus_timestamp, index, node_account_id, nonce, payer_account_id, "
                            + "result, scheduled, type, valid_start_ns)"
                            + " values"
                            + " (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    transaction.getConsensusTimestamp(),
                    transaction.getIndex(),
                    transaction.getNodeAccountId().getId(),
                    transaction.getNonce(),
                    transaction.getPayerAccountId().getId(),
                    transaction.getResult(),
                    transaction.isScheduled(),
                    transaction.getType(),
                    transaction.getValidStartNs());
        }
    }

    @SneakyThrows
    private void runMigration() {
        // add nft_transfer column to transaction before running the backfill
        try (var is = sql.getInputStream()) {
            jdbcTemplate.update(StreamUtils.copyToString(is, StandardCharsets.UTF_8));
        }
    }
}
