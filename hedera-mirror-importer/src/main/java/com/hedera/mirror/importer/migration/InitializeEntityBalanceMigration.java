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
import org.apache.commons.lang3.StringUtils;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcOperations;

import com.hedera.mirror.importer.MirrorProperties;

@Named
public class InitializeEntityBalanceMigration extends MirrorBaseJavaMigration {

    private static final String INITIALIZE_ENTITY_BALANCE_SQL = """
            with timestamp_range as (
              select consensus_timestamp as from_timestamp, consensus_end as to_timestamp
              from account_balance_file
              join (select consensus_end from record_file order by consensus_end desc limit 1) last_record_file
                on consensus_timestamp <= consensus_end
              order by consensus_timestamp desc
              limit 1
            ), snapshot as (
              select account_id, balance
              from account_balance
              join timestamp_range on from_timestamp = consensus_timestamp
            ), balance_change as (
              select entity_id, sum(amount) as amount
              from crypto_transfer
              join timestamp_range on consensus_timestamp > from_timestamp and consensus_timestamp <= to_timestamp
              group by entity_id
            )
            update entity
            set balance = coalesce(snapshot.balance, 0) + coalesce(amount, 0)
            from snapshot
            full outer join balance_change on account_id = entity_id
            where coalesce(account_id, entity_id) = id and deleted is not true and type in ('ACCOUNT', 'CONTRACT');
            """;

    private final JdbcOperations jdbcOperations;
    private final MigrationProperties migrationProperties;

    @Lazy
    public InitializeEntityBalanceMigration(JdbcOperations jdbcOperations, MirrorProperties mirrorProperties) {
        this.jdbcOperations = jdbcOperations;
        String propertiesKey = StringUtils.uncapitalize(getClass().getSimpleName());
        this.migrationProperties = mirrorProperties.getMigration().get(propertiesKey);
    }

    @Override
    public Integer getChecksum() {
        // Change the value in the configuration file if this migration should be rerun
        return migrationProperties.getChecksum();
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
        if (!migrationProperties.isEnabled()) {
            log.info("Skip migration since it's disabled");
            return;
        }

        var stopwatch = Stopwatch.createStarted();
        int count = jdbcOperations.update(INITIALIZE_ENTITY_BALANCE_SQL);
        log.info("Initialized {} entities balance in {}", count, stopwatch);
    }
}
