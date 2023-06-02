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

import com.hedera.mirror.common.domain.contract.ContractResult;
import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.EnabledIfV1;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.repository.ContractResultRepository;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.nio.charset.StandardCharsets;
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
        domainBuilder
                .transaction()
                .customize(t ->
                        t.consensusTimestamp(ethTx1.getConsensusTimestamp()).payerAccountId(ethTx1.getPayerAccountId()))
                .persist();
        var ethTx2 = domainBuilder.ethereumTransaction(false).persist();
        domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(ethTx2.getConsensusTimestamp())
                        .payerAccountId(ethTx2.getPayerAccountId())
                        .result(ResponseCodeEnum.DUPLICATE_TRANSACTION_VALUE))
                .persist();
        var ethTx3 = domainBuilder.ethereumTransaction(true).persist();
        domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(ethTx3.getConsensusTimestamp())
                        .payerAccountId(ethTx3.getPayerAccountId())
                        .result(ResponseCodeEnum.WRONG_NONCE_VALUE))
                .persist();
        var ethTx4 = domainBuilder.ethereumTransaction(false).persist();
        var transaction4 = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(ethTx4.getConsensusTimestamp())
                        .index(4)
                        .nonce(3)
                        .payerAccountId(ethTx4.getPayerAccountId())
                        .result(ResponseCodeEnum.CONSENSUS_GAS_EXHAUSTED_VALUE))
                .persist();
        var ethTx5 = domainBuilder.ethereumTransaction(true).persist();
        var transaction5 = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(ethTx5.getConsensusTimestamp())
                        .index(5)
                        .payerAccountId(ethTx5.getPayerAccountId())
                        .result(ResponseCodeEnum.INVALID_ACCOUNT_ID_VALUE))
                .persist();

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

    @SneakyThrows
    private void runMigration() {
        try (var is = sql.getInputStream()) {
            jdbcTemplate.update(StreamUtils.copyToString(is, StandardCharsets.UTF_8));
        }
    }
}
