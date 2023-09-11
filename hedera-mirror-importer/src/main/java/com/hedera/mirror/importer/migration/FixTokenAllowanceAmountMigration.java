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
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;

@Named
public class FixTokenAllowanceAmountMigration extends RepeatableMigration {

    private static final String FIX_TOKEN_ALLOWANCE_AMOUNT_SQL =
            """
       begin;

       create temp table token_allowance_remaining_amount (
         amount   bigint not null,
         owner    bigint not null,
         spender  bigint not null,
         token_id bigint not null
       ) on commit drop;

       with token_transfer_erc20_approved as (
         select tt.consensus_timestamp, tt.account_id, tt.token_id, cr.sender_id as spender, tt.payer_account_id as incorrect_spender
         from token_transfer tt
         join contract_result cr on cr.consensus_timestamp = tt.consensus_timestamp and cr.sender_id <> tt.payer_account_id
         where cr.sender_id is not null and tt.is_approval is true
       ), token_allowance_affected as (
         select distinct on (ta.owner, ta.spender, ta.token_id) ta.*
         from token_allowance ta
         join token_transfer_erc20_approved tt on
           ta.owner = tt.account_id and
           ta.spender in (tt.spender, tt.incorrect_spender) and
           ta.token_id = tt.token_id
         where tt.consensus_timestamp > lower(ta.timestamp_range)
       ), token_transfer_all_approved as (
         select tt.amount, tt.account_id as owner, coalesce(cr.sender_id, tt.payer_account_id) as spender, tt.token_id
         from token_transfer tt
         left join contract_result cr on cr.consensus_timestamp = tt.consensus_timestamp
         join token_allowance_affected ta on
           ta.owner = tt.account_id and
           ta.token_id = tt.token_id and
           ((cr.consensus_timestamp is not null and ta.spender = cr.sender_id) or
            (cr.consensus_timestamp is null and ta.spender = tt.payer_account_id ))
         where tt.consensus_timestamp > lower(ta.timestamp_range) and is_approval is true
       ), aggregated_amount as (
         select owner, spender, token_id, sum(amount) as total
         from token_transfer_all_approved
         group by owner, spender, token_id
       )
       insert into token_allowance_remaining_amount (amount, owner, spender, token_id)
       select greatest(0, (amount_granted + coalesce(total, 0))), ta.owner, ta.spender, ta.token_id
       from token_allowance_affected ta
       left join aggregated_amount a using (owner, spender, token_id);

       alter table token_allowance_remaining_amount add primary key (owner, spender, token_id);

       update token_allowance ta
       set amount = r.amount
       from token_allowance_remaining_amount r
       where r.owner = ta.owner and r.spender = ta.spender and r.token_id = ta.token_id;

       commit;
       """;

    private final JdbcTemplate jdbcTemplate;

    @Lazy
    public FixTokenAllowanceAmountMigration(JdbcTemplate jdbcTemplate, MirrorProperties mirrorProperties) {
        super(mirrorProperties.getMigration());
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    protected void doMigrate() throws IOException {
        var stopwatch = Stopwatch.createStarted();
        int count = jdbcTemplate.update(FIX_TOKEN_ALLOWANCE_AMOUNT_SQL);
        log.info("Fixed amount of {} token allowances in {}", count, stopwatch);
    }

    @Override
    public String getDescription() {
        return "Fix token allowance amount when allowance is used in transfers as ERC20 token";
    }

    @Override
    protected MigrationVersion getMinimumVersion() {
        return MigrationVersion.fromVersion("1.84.2"); // the version which adds token_allowance.amount_granted
    }
}
