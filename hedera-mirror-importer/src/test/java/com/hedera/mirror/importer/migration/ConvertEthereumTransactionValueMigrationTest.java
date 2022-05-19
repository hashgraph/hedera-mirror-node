package com.hedera.mirror.importer.migration;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.common.converter.WeiBarTinyBarConverter.WEIBARS_TO_TINYBARS_BIGINT;
import static com.hedera.mirror.importer.migration.ConvertEthereumTransactionValueMigration.BATCH_SIZE;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import com.hedera.mirror.importer.EnabledIfV1;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.parser.record.ethereum.Eip1559EthereumTransactionParser;

@EnabledIfV1
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Tag("migration")
@TestPropertySource(properties = "spring.flyway.target=1.59.2")
class ConvertEthereumTransactionValueMigrationTest extends IntegrationTest {

    private static final String REVERT_TABLE_CHANGE_SQL = "alter table if exists ethereum_transaction " +
            "drop column value, " +
            "add column value bytea null";

    private final ConvertEthereumTransactionValueMigration convertEthereumTransactionValueMigration;

    private final JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanup() {
        jdbcTemplate.update(REVERT_TABLE_CHANGE_SQL);
    }

    @Test
    void empty() {
        convertEthereumTransactionValueMigration.doMigrate();
        assertThat(findAllMigratedEthereumTransactions()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = { BATCH_SIZE, BATCH_SIZE + 10 })
    void migrate(int totalTransactions) {
        // given
        var transactions = new ArrayList<EthereumTransaction>();
        var expectedTransactions = new ArrayList<MigratedEthereumTransaction>();
        // first transaction has null value
        transactions.add(ethereumTransaction(null));
        expectedTransactions.add(toMigratedEthereumTransaction(transactions.get(0), null));
        for (int i = 1; i < totalTransactions; i++) {
            var transaction = ethereumTransaction((long) i);
            transactions.add(transaction);
            expectedTransactions.add(toMigratedEthereumTransaction(transaction, (long) i));
        }
        persistEthereumTransactions(transactions);

        // when
        convertEthereumTransactionValueMigration.doMigrate();

        // then
        var actual = findAllMigratedEthereumTransactions();
        assertThat(actual).containsExactlyInAnyOrderElementsOf(expectedTransactions);
    }

    private EthereumTransaction ethereumTransaction(Long value) {
        var builder = EthereumTransaction.builder()
                .consensusTimestamp(domainBuilder.timestamp())
                .data(domainBuilder.bytes(32))
                .gasLimit(20_000_000L)
                .hash(domainBuilder.bytes(32))
                .maxGasAllowance(20_000_000L)
                .nonce(domainBuilder.id())
                .payerAccountId(domainBuilder.id())
                .signatureR(domainBuilder.bytes(32))
                .signatureS(domainBuilder.bytes(32))
                .type(Eip1559EthereumTransactionParser.EIP1559_TYPE_BYTE);
        if (value != null) {
            builder.value(BigInteger.valueOf(value).multiply(WEIBARS_TO_TINYBARS_BIGINT).toByteArray());
        }
        return builder.build();
    }

    private List<MigratedEthereumTransaction> findAllMigratedEthereumTransactions() {
        return jdbcTemplate.query("select * from ethereum_transaction",
                (rs, index) -> MigratedEthereumTransaction.builder()
                        .consensusTimestamp(rs.getLong("consensus_timestamp"))
                        .data(rs.getBytes("data"))
                        .gasLimit(rs.getLong("gas_limit"))
                        .hash(rs.getBytes("hash"))
                        .maxGasAllowance(rs.getLong("max_gas_allowance"))
                        .nonce(rs.getLong("nonce"))
                        .payerAccountId(rs.getLong("payer_account_id"))
                        .signatureR(rs.getBytes("signature_r"))
                        .signatureS(rs.getBytes("signature_s"))
                        .type(rs.getInt("type"))
                        .value(rs.getObject("value", Long.class))
                        .build());
    }

    private void persistEthereumTransactions(List<EthereumTransaction> transactions) {
        jdbcTemplate.batchUpdate(
                "insert into ethereum_transaction (consensus_timestamp, data, gas_limit, hash, max_gas_allowance, " +
                        "nonce, payer_account_id, signature_r, signature_s, type, value) " +
                        "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                transactions,
                transactions.size(),
                (ps, transaction) -> {
                    ps.setLong(1, transaction.getConsensusTimestamp());
                    ps.setBytes(2, transaction.getData());
                    ps.setLong(3, transaction.getGasLimit());
                    ps.setBytes(4, transaction.getHash());
                    ps.setLong(5, transaction.getMaxGasAllowance());
                    ps.setLong(6, transaction.getNonce());
                    ps.setLong(7, transaction.getPayerAccountId());
                    ps.setBytes(8, transaction.getSignatureR());
                    ps.setBytes(9, transaction.getSignatureS());
                    ps.setShort(10, transaction.getType().shortValue());
                    ps.setBytes(11, transaction.getValue());
                }
        );
    }

    private MigratedEthereumTransaction toMigratedEthereumTransaction(EthereumTransaction transaction, Long value) {
        return MigratedEthereumTransaction.builder()
                .consensusTimestamp(transaction.getConsensusTimestamp())
                .data(transaction.getData())
                .gasLimit(transaction.getGasLimit())
                .hash(transaction.getHash())
                .maxGasAllowance(transaction.getMaxGasAllowance())
                .nonce(transaction.getNonce())
                .payerAccountId(transaction.getPayerAccountId())
                .signatureR(transaction.getSignatureR())
                .signatureS(transaction.getSignatureS())
                .type(transaction.getType())
                .value(value)
                .build();
    }

    @Builder
    @Data
    private static class EthereumTransaction {
        private Long consensusTimestamp;
        private byte[] data;
        private Long gasLimit;
        private byte[] hash;
        private Long maxGasAllowance;
        private Long nonce;
        private Long payerAccountId;
        private byte[] signatureR;
        private byte[] signatureS;
        private Integer type;
        private byte[] value;
    }

    @Builder
    @Data
    private static class MigratedEthereumTransaction {
        private Long consensusTimestamp;
        private byte[] data;
        private Long gasLimit;
        private byte[] hash;
        private Long maxGasAllowance;
        private Long nonce;
        private Long payerAccountId;
        private byte[] signatureR;
        private byte[] signatureS;
        private Integer type;
        private Long value;
    }
}
