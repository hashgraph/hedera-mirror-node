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
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcOperations;

import com.hedera.mirror.importer.MirrorProperties;

@Named
public class TokenAccountBalanceMigration extends RepeatableMigration {
    private static final String UPDATE_TOKEN_ACCOUNT_SQL = """
                with account_balance as (
                    select consensus_timestamp from account_balance_file
                    order by consensus_timestamp desc limit 1
                ),
                token_balance as (
                    select distinct tb.account_id, tb.consensus_timestamp, tb.token_id, tb.balance 
                    from token_balance tb
                    join account_balance ab on ab.consensus_timestamp <= tb.consensus_timestamp
                    where tb.consensus_timestamp >= ab.consensus_timestamp
                    order by tb.consensus_timestamp asc
                ) 
                update token_account t set balance = token_balance.balance
                from token_balance
                where t.account_id = token_balance.account_id and t.token_id = token_balance.token_id
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
