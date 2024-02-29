/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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
import com.hedera.mirror.importer.ImporterProperties;
import com.hedera.mirror.importer.repository.AccountBalanceFileRepository;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import jakarta.inject.Named;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@Named
public class TokenAccountBalanceMigration extends AbstractTimestampInfoMigration {

    private static final String UPDATE_TOKEN_ACCOUNT_SQL =
            """
            with token_balance_snapshot as (
              select distinct on (account_id, token_id) *
              from token_balance
              where consensus_timestamp <= :snapshotTimestamp and consensus_timestamp > :snapshotTimestamp - 2592000000000000
              order by account_id, token_id, consensus_timestamp desc
            ),
            token_transfer as (
                select account_id, token_id, sum(amount) as amount
                from token_transfer tt
                where consensus_timestamp > :fromTimestamp and consensus_timestamp <= :toTimestamp
                group by account_id, token_id
            ),
            initial_balance as (
              select tb.account_id, coalesce(ta.associated, true) as associated, tb.token_id,
                case
                    when ta.associated is false then 0
                    else coalesce(tt.amount + tb.balance, tb.balance, 0)
                end as balance,
                :toTimestamp as balance_timestamp
              from token_balance_snapshot tb
              left join token_account ta on ta.account_id = tb.account_id and ta.token_id = tb.token_id
              left join token_transfer tt on tt.token_id = tb.token_id and tt.account_id = tb.account_id
            )
            insert into token_account (account_id, associated, balance, balance_timestamp, created_timestamp, timestamp_range, token_id)
            select account_id, associated, balance, balance_timestamp, 0, '[0,)', token_id
            from initial_balance
            on conflict (account_id, token_id) do update
            set balance = excluded.balance,
              balance_timestamp = excluded.balance_timestamp;
            """;

    @Lazy
    public TokenAccountBalanceMigration(
            AccountBalanceFileRepository accountBalanceFileRepository,
            ImporterProperties importerProperties,
            NamedParameterJdbcTemplate jdbcTemplate,
            RecordFileRepository recordFileRepository,
            TransactionTemplate transactionTemplate) {
        super(
                accountBalanceFileRepository,
                importerProperties.getMigration(),
                jdbcTemplate,
                recordFileRepository,
                transactionTemplate);
    }

    @Override
    public String getDescription() {
        return "Initialize token account balance";
    }

    @Override
    protected MigrationVersion getMinimumVersion() {
        return MigrationVersion.fromVersion("1.89.2"); // The version which deduplicates balance tables
    }

    @Override
    protected void doMigrate() {
        var stopwatch = Stopwatch.createStarted();
        var count = doMigrate(UPDATE_TOKEN_ACCOUNT_SQL);
        log.info("Migrated {} token account balances in {}", count, stopwatch);
    }
}
