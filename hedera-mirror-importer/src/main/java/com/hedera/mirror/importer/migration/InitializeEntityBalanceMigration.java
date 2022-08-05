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

import com.google.common.base.Stopwatch;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcOperations;

@Named
@RequiredArgsConstructor(onConstructor_ = {@Lazy})
public class InitializeEntityBalanceMigration extends MirrorBaseJavaMigration {

    private static final String INITIALIZE_ENTITY_BALANCE_SQL = """
            with latest as (
              select consensus_timestamp
              from account_balance_file
              where consensus_timestamp <= (select max(consensus_end) from record_file)
              order by consensus_timestamp desc
              limit 1
            )
            update entity
            set balance = coalesce((select balance from account_balance
              where consensus_timestamp = latest.consensus_timestamp and account_id = id), 0) +
              (select coalesce(sum(amount), 0) from crypto_transfer
               where entity_id = id and consensus_timestamp > latest.consensus_timestamp)
            from latest
            where type in ('ACCOUNT', 'CONTRACT') and deleted is not true;
            """;

    private final JdbcOperations jdbcOperations;

    @Override
    public Integer getChecksum() {
        return 1; // Change this if this migration should be rerun
    }

    @Override
    public String getDescription() {
        return "Initialize entity balance";
    }

    @Override
    protected MigrationVersion getMinimumVersion() {
        return MigrationVersion.fromVersion("1.65.0");
    }

    @Override
    public MigrationVersion getVersion() {
        return null;
    }

    @Override
    protected void doMigrate() {
        var stopwatch = Stopwatch.createStarted();
        int count = jdbcOperations.update(INITIALIZE_ENTITY_BALANCE_SQL);
        log.info("Initialized {} entities balance in {}", count, stopwatch);
    }
}
