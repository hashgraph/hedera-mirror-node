/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import static java.util.stream.Collectors.joining;

import com.google.common.base.Stopwatch;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.config.Owner;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import jakarta.inject.Named;
import java.io.IOException;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.JdbcTemplate;

@Named
public class BackfillTransactionHashMigration extends RepeatableMigration {

    private static final String BACKFILL_TRANSACTION_HASH_SQL =
            """
            begin;
            truncate %1$s;
            insert into %1$s (consensus_timestamp, hash, payer_account_id)
            select consensus_timestamp, transaction_hash, payer_account_id
            from transaction
            where consensus_timestamp >= ? %2$s;
            commit;
            """;

    private static final String START_TIMESTAMP_KEY = "startTimestamp";

    private final EntityProperties entityProperties;

    private final JdbcTemplate jdbcTemplate;
    private final boolean isV2;

    @Lazy
    public BackfillTransactionHashMigration(
            EntityProperties entityProperties,
            @Owner JdbcTemplate jdbcTemplate,
            MirrorProperties mirrorProperties,
            Environment environment) {
        super(mirrorProperties.getMigration());
        this.entityProperties = entityProperties;
        this.jdbcTemplate = jdbcTemplate;
        this.isV2 = environment.acceptsProfiles(Profiles.of("v2"));
    }

    @Override
    protected void doMigrate() throws IOException {
        var persist = entityProperties.getPersist();
        if (!persist.isTransactionHash()) {
            log.info("Skipping migration since transaction hash persistence is disabled");
            return;
        }

        long startTimestamp = Long.parseLong(migrationProperties.getParams().getOrDefault(START_TIMESTAMP_KEY, "-1"));
        if (startTimestamp == -1) {
            log.info("Skipping migration since startTimestamp is not set");
            return;
        }

        var stopwatch = Stopwatch.createStarted();
        var transactionHashTypes = persist.getTransactionHashTypes();
        String transactionTypesCondition = transactionHashTypes.isEmpty()
                ? ""
                : String.format(
                        "and type in (%s)",
                        transactionHashTypes.stream()
                                .map(TransactionType::getProtoId)
                                .map(Object::toString)
                                .collect(joining(",")));
        String sql = String.format(
                BACKFILL_TRANSACTION_HASH_SQL,
                isV2 ? "transaction_hash" : "transaction_hash_sharded",
                transactionTypesCondition);
        jdbcTemplate.update(sql, startTimestamp);

        log.info("Backfilled transaction hash for transactions at or after {} in {}", startTimestamp, stopwatch);
    }

    @Override
    public String getDescription() {
        return "Backfill transaction hash to consensus timestamp mapping";
    }
}
