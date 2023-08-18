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

import com.google.common.base.Stopwatch;
import com.hedera.mirror.importer.MirrorProperties;
import jakarta.inject.Named;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcOperations;

@Named
public class InitializeEntityBalanceMigration extends RepeatableMigration {

    private static final String INITIALIZE_ENTITY_BALANCE_SQL =
            """
            with timestamp_range as (
              select
                consensus_timestamp as snapshot_timestamp,
                consensus_timestamp + time_offset as from_timestamp,
                consensus_end as to_timestamp
              from account_balance_file
              join (select consensus_end from record_file order by consensus_end desc limit 1) last_record_file
                on consensus_timestamp + time_offset <= consensus_end
              order by consensus_timestamp desc
              limit 1
            ), snapshot as (
              select account_id, balance
              from account_balance
              join timestamp_range on snapshot_timestamp = consensus_timestamp
            ), change as (
              select entity_id, sum(amount) as amount
              from crypto_transfer
              join timestamp_range on consensus_timestamp > from_timestamp and consensus_timestamp <= to_timestamp
              where errata is null or errata <> 'DELETE'
              group by entity_id
            ), state as (
              select
                coalesce(account_id, entity_id) as account_id,
                coalesce(balance, 0) + coalesce(amount, 0) as balance,
                case when balance is not null then false end as deleted
              from snapshot
              full outer join change on account_id = entity_id
            )
            insert into entity (balance, deleted, id, num, realm, shard, timestamp_range)
            select s.balance, s.deleted, s.account_id, (s.account_id & 4294967295), ((s.account_id >> 32) & 65535), (s.account_id  >> 48), '[0,)'
            from state s
            on conflict (id) do update
            set balance = excluded.balance;
            """;

    private final JdbcOperations jdbcOperations;

    @Lazy
    public InitializeEntityBalanceMigration(JdbcOperations jdbcOperations, MirrorProperties mirrorProperties) {
        super(mirrorProperties.getMigration());
        this.jdbcOperations = jdbcOperations;
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
    protected void doMigrate() {
        var stopwatch = Stopwatch.createStarted();
        int count = jdbcOperations.update(INITIALIZE_ENTITY_BALANCE_SQL);
        log.info("Initialized {} entities balance in {}", count, stopwatch);
    }
}
