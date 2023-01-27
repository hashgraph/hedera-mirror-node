package com.hedera.mirror.importer.migration;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import com.google.common.base.Stopwatch;
import java.io.IOException;
import javax.inject.Named;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;

import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.config.Owner;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;

@Named
public class BackfillTransactionHashMigration extends RepeatableMigration {

    private static final String BACKFILL_TRANSACTION_HASH_SQL = """
            begin;
            truncate transaction_hash;
            insert into transaction_hash (consensus_timestamp, hash)
            select consensus_timestamp, transaction_hash
            from transaction
            where consensus_timestamp >= ?;
            commit;
            """;

    private static final String START_TIMESTAMP_KEY = "startTimestamp";

    private final EntityProperties entityProperties;

    private final JdbcTemplate jdbcTemplate;

    @Lazy
    public BackfillTransactionHashMigration(EntityProperties entityProperties,
                                            @Owner JdbcTemplate jdbcTemplate,
                                            MirrorProperties mirrorProperties) {
        super(mirrorProperties.getMigration());
        this.entityProperties = entityProperties;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    protected void doMigrate() throws IOException {
        if (!entityProperties.getPersist().isTransactionHash()) {
            log.info("Skipping migration since transaction hash persistence is disabled");
            return;
        }

        long startTimestamp = Long.parseLong(migrationProperties.getParams().getOrDefault(START_TIMESTAMP_KEY, "-1"));
        if (startTimestamp == -1) {
            log.info("Skipping migration since startTimestamp is not set");
            return;
        }

        var stopwatch = Stopwatch.createStarted();
        jdbcTemplate.update(BACKFILL_TRANSACTION_HASH_SQL, startTimestamp);
        log.info("Backfilled transaction hash for transactions at or after {} in {}", startTimestamp, stopwatch);
    }

    @Override
    public String getDescription() {
        return "Backfill transaction hash to consensus timestamp mapping";
    }
}
