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
public class TokenAccountBalanceMigration extends RepeatableMigration {
    private static final String UPDATE_TOKEN_ACCOUNT_SQL =
            """
             with timestamp_range as (
                select consensus_timestamp as snapshot_timestamp,
                    consensus_timestamp + time_offset as from_timestamp,
                    consensus_end as to_timestamp
                from account_balance_file
                join (select consensus_end from record_file order by consensus_end desc limit 1) last_record_file
                  on consensus_timestamp + time_offset <= consensus_end
                order by consensus_timestamp desc
                limit 1
            ),
            token_balance_latest as (
                select *
                from token_balance
                join timestamp_range on snapshot_timestamp = consensus_timestamp
            ),
            token_transfer as (
                select account_id, token_id, sum(amount) as amount
                from token_transfer tt
                join timestamp_range on consensus_timestamp > from_timestamp and consensus_timestamp <= to_timestamp
                group by account_id, token_id
            ),
            initial_balance as (
              select tb.account_id, coalesce(ta.associated, true) as associated, tb.token_id,
                case
                    when ta.associated is false then 0
                    else coalesce(tt.amount + tb.balance, tb.balance, 0)
                end as balance
              from token_balance_latest tb
              left join token_account ta on ta.account_id = tb.account_id and ta.token_id = tb.token_id
              left join token_transfer tt on tt.token_id = tb.token_id and tt.account_id = tb.account_id
            )
            insert into token_account (account_id, associated, balance, created_timestamp, timestamp_range, token_id)
            select account_id, associated, balance, 0, '[0,)', token_id
            from initial_balance
            on conflict (account_id, token_id) do update
            set balance = excluded.balance;
            """;

    private final JdbcOperations jdbcOperations;

    @Lazy
    public TokenAccountBalanceMigration(JdbcOperations jdbcOperations, MirrorProperties mirrorProperties) {
        super(mirrorProperties.getMigration());
        this.jdbcOperations = jdbcOperations;
    }

    @Override
    public String getDescription() {
        return "Initialize token account balance";
    }

    @Override
    protected MigrationVersion getMinimumVersion() {
        return MigrationVersion.fromVersion("1.67.1");
    }

    @Override
    protected void doMigrate() {
        var stopwatch = Stopwatch.createStarted();
        int count = jdbcOperations.update(UPDATE_TOKEN_ACCOUNT_SQL);
        log.info("Migrated {} token account balances in {}", count, stopwatch);
    }
}
