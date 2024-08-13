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

import com.google.common.base.Stopwatch;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.importer.ImporterProperties;
import com.hedera.mirror.importer.parser.record.ethereum.EthereumTransactionHashService;
import jakarta.inject.Named;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Data;
import org.apache.commons.lang3.ArrayUtils;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionOperations;

@Named
public class BackfillEthereumTransactionHashMigration extends RepeatableMigration {

    private static final RowMapper<MigrationEthereumTransaction> ROW_MAPPER =
            new DataClassRowMapper<>(MigrationEthereumTransaction.class);
    private static final String SELECT_ETHEREUM_TRANSACTION_SQL =
            """
            select call_data_id, consensus_timestamp, data, hash
            from ethereum_transaction
            where length(hash) = 0 and consensus_timestamp > ?
            order by consensus_timestamp
            limit 200
            """;
    private static final String UPDATE_ETHEREUM_HASH_SQL =
            """
            update ethereum_transaction
            set hash = ?
            where consensus_timestamp = ?
            """;

    private final EthereumTransactionHashService ethereumTransactionHashService;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionOperations transactionOperations;

    @Lazy
    public BackfillEthereumTransactionHashMigration(
            EthereumTransactionHashService ethereumTransactionHashService,
            ImporterProperties importerProperties,
            JdbcTemplate jdbcTemplate,
            TransactionOperations transactionOperations) {
        super(importerProperties.getMigration());
        this.ethereumTransactionHashService = ethereumTransactionHashService;
        this.jdbcTemplate = jdbcTemplate;
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
                            t.setHash(ethereumTransactionHashService.getHash(
                                    callDataId, t.getConsensusTimestamp(), t.getData()));
                            return t;
                        })
                        .filter(t -> ArrayUtils.isNotEmpty(t.getHash()))
                        .toList();
                patched.addAndGet(patchedTransactions.size());

                if (!patchedTransactions.isEmpty()) {
                    jdbcTemplate.batchUpdate(
                            UPDATE_ETHEREUM_HASH_SQL,
                            patchedTransactions,
                            patchedTransactions.size(),
                            (ps, transaction) -> {
                                ps.setBytes(1, transaction.getHash());
                                ps.setLong(2, transaction.getConsensusTimestamp());
                            });
                }
            }
        });

        log.info("Backfilled hash for {} out of {} ethereum transactions in {}", patched, found, stopwatch);
    }

    @Override
    protected MigrationVersion getMinimumVersion() {
        return MigrationVersion.fromVersion("1.59.0");
    }

    @Data
    private static class MigrationEthereumTransaction {
        private Long callDataId;
        private long consensusTimestamp;
        private byte[] data;
        private byte[] hash;
    }
}
