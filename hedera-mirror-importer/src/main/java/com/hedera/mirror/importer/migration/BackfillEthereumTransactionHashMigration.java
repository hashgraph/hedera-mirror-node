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

import static com.hedera.mirror.common.domain.transaction.TransactionType.ETHEREUMTRANSACTION;

import com.google.common.base.Stopwatch;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.TransactionHash;
import com.hedera.mirror.importer.ImporterProperties;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import com.hedera.mirror.importer.parser.record.ethereum.EthereumTransactionParser;
import com.hedera.mirror.importer.repository.TransactionHashRepository;
import jakarta.inject.Named;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Data;
import org.apache.commons.lang3.ArrayUtils;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionOperations;

@Named
public class BackfillEthereumTransactionHashMigration extends RepeatableMigration {

    private static final ParameterizedPreparedStatementSetter<MigrationEthereumTransaction> PSS = (ps, transaction) -> {
        ps.setBytes(1, transaction.getHash());
        ps.setLong(2, transaction.getConsensusTimestamp());
    };
    private static final RowMapper<MigrationEthereumTransaction> ROW_MAPPER =
            new DataClassRowMapper<>(MigrationEthereumTransaction.class);
    private static final String SELECT_ETHEREUM_TRANSACTION_SQL =
            """
            select call_data_id, consensus_timestamp, data, hash, payer_account_id
            from ethereum_transaction
            where hash = ''::bytea and consensus_timestamp > ?
            order by consensus_timestamp
            limit 200
            """;
    private static final String UPDATE_CONTRACT_RESULT_SQL =
            """
            update contract_result
            set transaction_hash = ?
            where consensus_timestamp = ?
            """;
    // Workaround for citus as changing the value of distribution column contract_transaction_hash.hash is not allowed
    private static final String UPDATE_CONTRACT_TRANSACTION_HASH_SQL =
            """
            with deleted as (
              delete from contract_transaction_hash
              where consensus_timestamp = ? and hash = ''::bytea
              returning *
            )
            insert into contract_transaction_hash (consensus_timestamp, entity_id, hash, payer_account_id, transaction_result)
            select consensus_timestamp, entity_id, ?, payer_account_id, transaction_result
            from deleted
            """;
    private static final String UPDATE_ETHEREUM_HASH_SQL =
            """
            update ethereum_transaction
            set hash = ?
            where consensus_timestamp = ?
            """;

    private final EntityProperties entityProperties;
    private final EthereumTransactionParser ethereumTransactionParser;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionHashRepository transactionHashRepository;
    private final TransactionOperations transactionOperations;

    @Lazy
    public BackfillEthereumTransactionHashMigration(
            EntityProperties entityProperties,
            EthereumTransactionParser ethereumTransactionParser,
            ImporterProperties importerProperties,
            JdbcTemplate jdbcTemplate,
            TransactionHashRepository transactionHashRepository,
            TransactionOperations transactionOperations) {
        super(importerProperties.getMigration());
        this.entityProperties = entityProperties;
        this.ethereumTransactionParser = ethereumTransactionParser;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionHashRepository = transactionHashRepository;
        this.transactionOperations = transactionOperations;
    }

    @Override
    public String getDescription() {
        return "Backfill ethereum transaction hash if it's empty";
    }

    @Override
    protected void doMigrate() throws IOException {
        var found = new AtomicInteger();
        var patched = new AtomicInteger();
        var stopwatch = Stopwatch.createStarted();

        transactionOperations.executeWithoutResult(s -> {
            long consensusTimestamp = -1;
            for (; ; ) {
                var transactions = jdbcTemplate.query(SELECT_ETHEREUM_TRANSACTION_SQL, ROW_MAPPER, consensusTimestamp);
                found.addAndGet(transactions.size());
                if (transactions.isEmpty()) {
                    break;
                }

                consensusTimestamp = transactions.getLast().getConsensusTimestamp();
                var patchedTransactions = transactions.stream()
                        .map(t -> {
                            var callDataId = t.getCallDataId() == null ? null : EntityId.of(t.getCallDataId());
                            t.setHash(ethereumTransactionParser.getHash(
                                    t.getCallData(), callDataId, t.getConsensusTimestamp(), t.getData()));
                            return t;
                        })
                        .filter(t -> ArrayUtils.isNotEmpty(t.getHash()))
                        .toList();
                patched.addAndGet(patchedTransactions.size());
                backfillTables(patchedTransactions);
            }
        });

        log.info("Backfilled hash for {} out of {} ethereum transactions in {}", patched, found, stopwatch);
    }

    @Override
    protected MigrationVersion getMinimumVersion() {
        // Version in which transaction_hash.distribution_id is added
        return MigrationVersion.fromVersion("1.99.1");
    }

    private void backfillTables(List<MigrationEthereumTransaction> patchedTransactions) {
        if (patchedTransactions.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate(UPDATE_CONTRACT_RESULT_SQL, patchedTransactions, patchedTransactions.size(), PSS);

        jdbcTemplate.batchUpdate(
                UPDATE_CONTRACT_TRANSACTION_HASH_SQL,
                patchedTransactions,
                patchedTransactions.size(),
                (ps, transaction) -> {
                    ps.setLong(1, transaction.getConsensusTimestamp());
                    ps.setBytes(2, transaction.getHash());
                });

        jdbcTemplate.batchUpdate(UPDATE_ETHEREUM_HASH_SQL, patchedTransactions, patchedTransactions.size(), PSS);

        if (entityProperties.getPersist().shouldPersistTransactionHash(ETHEREUMTRANSACTION)) {
            var transactionHashes = patchedTransactions.stream()
                    .map(t -> TransactionHash.builder()
                            .consensusTimestamp(t.getConsensusTimestamp())
                            .hash(t.getHash())
                            .payerAccountId(t.getPayerAccountId())
                            .build())
                    .toList();
            transactionHashRepository.saveAll(transactionHashes);
        }
    }

    @Data
    private static class MigrationEthereumTransaction {
        private byte[] callData;
        private Long callDataId;
        private long consensusTimestamp;
        private byte[] data;
        private byte[] hash;
        private long payerAccountId;
    }
}
