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
import com.hedera.mirror.importer.config.Owner;
import jakarta.inject.Named;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;

@Named
public class SyntheticTokenAllowanceOwnerMigration extends RepeatableMigration {

    private static final String UPDATE_TOKEN_ALLOWANCE_OWNER_SQL =
            """
            begin;

            create temp table token_allowance_temp (
              amount            bigint not null,
              amount_granted    bigint not null,
              created_timestamp bigint not null,
              owner             bigint not null,
              payer_account_id  bigint not null,
              spender           bigint not null,
              token_id          bigint not null,
              primary key (owner, spender, token_id, created_timestamp)
            ) on commit drop;

            with affected as (
              select ta.*, cr.consensus_timestamp, cr.sender_id
              from (
                select * from token_allowance
                union all
                select * from token_allowance_history
              ) ta
              join contract_result cr on cr.consensus_timestamp = lower(ta.timestamp_range)
            ), delete_token_allowance as (
              delete from token_allowance ta
              using affected a
              where ta.owner in (a.owner, a.sender_id) and ta.spender = a.spender and ta.token_id = a.token_id
              returning
                ta.amount,
                ta.amount_granted,
                lower(ta.timestamp_range) as created_timestamp,
                a.sender_id as owner,
                ta.payer_account_id,
                ta.spender,
                ta.token_id
            ), delete_token_allowance_history as (
              delete from token_allowance_history ta
              using affected a
              where (ta.owner = a.owner and ta.spender = a.spender and ta.token_id = a.token_id and ta.timestamp_range = a.timestamp_range) or
                (ta.owner = a.sender_id and ta.spender = a.spender and ta.token_id = a.token_id)
              returning
                ta.amount,
                ta.amount_granted,
                lower(ta.timestamp_range) as created_timestamp,
                a.sender_id as owner,
                ta.payer_account_id,
                ta.spender,
                ta.token_id
            )
            insert into token_allowance_temp (amount, amount_granted, created_timestamp, owner, payer_account_id, spender, token_id)
            select amount, amount_granted, created_timestamp, owner, payer_account_id, spender, token_id from delete_token_allowance
            union all
            select amount, amount_granted, created_timestamp, owner, payer_account_id, spender, token_id from delete_token_allowance_history;

            with correct_timestamp_range as (
              select
                amount,
                amount_granted,
                owner,
                payer_account_id,
                spender,
                int8range(created_timestamp, (
                  select c.created_timestamp
                  from token_allowance_temp c
                  where c.owner = p.owner and c.spender = p.spender and c.token_id = p.token_id
                    and c.created_timestamp > p.created_timestamp
                  order by c.created_timestamp
                  limit 1)) as timestamp_range,
                token_id
              from token_allowance_temp p
            ), history as (
              insert into token_allowance_history (amount, amount_granted, owner, payer_account_id, spender, timestamp_range, token_id)
              select * from correct_timestamp_range where upper(timestamp_range) is not null
            )
            insert into token_allowance (amount, amount_granted, owner, payer_account_id, spender, timestamp_range, token_id)
            select * from correct_timestamp_range where upper(timestamp_range) is null;

            commit;
            """;

    private final JdbcTemplate jdbcTemplate;

    @Lazy
    public SyntheticTokenAllowanceOwnerMigration(@Owner JdbcTemplate jdbcTemplate, MirrorProperties mirrorProperties) {
        super(mirrorProperties.getMigration());
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String getDescription() {
        return "Updates the owner for synthetic token allowances to the corresponding contract result sender";
    }

    @Override
    protected MigrationVersion getMinimumVersion() {
        // contract_result sender_id was added in 1.58.4, but SQL above is now compatible with 1.84.2+
        return MigrationVersion.fromVersion("1.84.2");
    }

    @Override
    protected void doMigrate() {
        var stopwatch = Stopwatch.createStarted();
        jdbcTemplate.execute(UPDATE_TOKEN_ALLOWANCE_OWNER_SQL);
        log.info("Updated token allowance owners in {}", stopwatch);
    }
}
