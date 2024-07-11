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
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.ImporterProperties;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import jakarta.inject.Named;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;

@Named
public class BackfillEthereumTransactionHashMigration extends RepeatableMigration {

    private static final String MIGRATION_SQL =
            """
            insert into transaction_hash (consensus_timestamp, hash, payer_account_id)
            select consensus_timestamp, hash, payer_account_id
            from ethereum_transaction
            """;

    private final EntityProperties entityProperties;
    private final JdbcTemplate jdbcTemplate;

    @Lazy
    public BackfillEthereumTransactionHashMigration(
            EntityProperties entityProperties, JdbcTemplate jdbcTemplate, ImporterProperties importerProperties) {
        super(importerProperties.getMigration());
        this.entityProperties = entityProperties;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    protected void doMigrate() {
        if (!entityProperties.getPersist().shouldPersistTransactionHash(TransactionType.ETHEREUMTRANSACTION)) {
            log.info("Skipping migration since transaction hash persistence for ethereum transaction is disabled");
            return;
        }

        var stopwatch = Stopwatch.createStarted();
        int count = jdbcTemplate.update(MIGRATION_SQL);
        log.info("Inserted {} ethereum transaction hashes to transaction_hash in {}", count, stopwatch);
    }

    @Override
    public String getDescription() {
        return "Backfill ethererum transaction hash info to transaction_hash table";
    }
}
