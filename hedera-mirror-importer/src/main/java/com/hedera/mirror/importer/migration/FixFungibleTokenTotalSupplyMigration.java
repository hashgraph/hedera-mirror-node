/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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
import java.io.IOException;
import lombok.CustomLog;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;

@CustomLog
@Named
public class FixFungibleTokenTotalSupplyMigration extends RepeatableMigration {

    private static final String SQL =
            """
            with snapshot_timestamp as (
              select max(consensus_timestamp) as timestamp
              from account_balance_file
              where consensus_timestamp <= (select max(consensus_end) from record_file)
            ), token_balance_sum as (
              select token_id, sum(balance) amount
              from token_balance
              where consensus_timestamp = (select timestamp from snapshot_timestamp)
              group by token_id
            ), initial as (
              select tb.*
              from token_balance_sum tb
              join token tk on tk.token_id = tb.token_id
              where tk.type = 'FUNGIBLE_COMMON'
            ), change as (
              select token_id, sum(amount) amount
              from token_transfer
              where consensus_timestamp > (select timestamp from snapshot_timestamp)
              group by token_id
            ), final as (
              select coalesce(i.token_id, c.token_id) token_id, coalesce(i.amount, 0) + coalesce(c.amount, 0) amount
              from initial i
              full outer join change c on i.token_id = c.token_id
            )
            update token t
            set total_supply = amount
            from final f
            where t.token_id = f.token_id
            """;

    private final JdbcTemplate jdbcTemplate;

    @Lazy
    public FixFungibleTokenTotalSupplyMigration(JdbcTemplate jdbcTemplate, MirrorProperties mirrorProperties) {
        super(mirrorProperties.getMigration());
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    protected void doMigrate() throws IOException {
        var stopwatch = Stopwatch.createStarted();
        int count = jdbcTemplate.update(SQL);
        log.info("Fixed {} fungible tokens' total supply in {}", count, stopwatch);
    }

    @Override
    public String getDescription() {
        return "Fix fungible token total supply";
    }

    @Override
    protected MigrationVersion getMinimumVersion() {
        return MigrationVersion.fromVersion("1.39.0"); // the version which adds token table
    }
}
