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
import javax.inject.Named;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;

@Named
public class SyntheticNftAllowanceOwnerMigration extends RepeatableMigration {

    private static final String UPDATE_NFT_ALLOWANCE_OWNER_SQL =
            """
            begin;

            create temp table nft_allowance_temp (
              approved_for_all  boolean not null,
              created_timestamp bigint  not null,
              owner             bigint  not null,
              payer_account_id  bigint  not null,
              spender           bigint  not null,
              token_id          bigint  not null,
              primary key (owner, spender, token_id, created_timestamp)
            ) on commit drop;

            with affected as (
              select nfta.*, cr.consensus_timestamp, cr.sender_id
              from (
                select * from nft_allowance
                union all
                select * from nft_allowance_history
              ) nfta
              join contract_result cr on cr.consensus_timestamp = lower(nfta.timestamp_range)
            ), delete_nft_allowance as (
              delete from nft_allowance nfta
              using affected a
              where nfta.owner in (a.owner, a.sender_id) and nfta.spender = a.spender and nfta.token_id = a.token_id
              returning
                nfta.approved_for_all,
                lower(nfta.timestamp_range) as created_timestamp,
                a.sender_id as owner,
                nfta.payer_account_id,
                nfta.spender,
                nfta.token_id
            ), delete_nft_allowance_history as (
              delete from nft_allowance_history nfta
              using affected a
              where (nfta.owner = a.owner and nfta.spender = a.spender and nfta.token_id = a.token_id and nfta.timestamp_range = a.timestamp_range) or
                (nfta.owner = a.sender_id and nfta.spender = a.spender and nfta.token_id = a.token_id)
              returning
                nfta.approved_for_all,
                lower(nfta.timestamp_range) as created_timestamp,
                a.sender_id as owner,
                nfta.payer_account_id,
                nfta.spender,
                nfta.token_id
            )
            insert into nft_allowance_temp (approved_for_all, created_timestamp, owner, payer_account_id, spender, token_id)
            select approved_for_all, created_timestamp, owner, payer_account_id, spender, token_id from delete_nft_allowance
            union all
            select approved_for_all, created_timestamp, owner, payer_account_id, spender, token_id from delete_nft_allowance_history;

            with correct_timestamp_range as (
              select
                approved_for_all,
                owner,
                payer_account_id,
                spender,
                int8range(created_timestamp, (
                  select c.created_timestamp
                  from nft_allowance_temp c
                  where c.owner = p.owner and c.spender = p.spender and c.token_id = p.token_id
                    and c.created_timestamp > p.created_timestamp
                  order by c.created_timestamp
                  limit 1)) as timestamp_range,
                token_id
              from nft_allowance_temp p
            ), history as (
              insert into nft_allowance_history (approved_for_all, owner, payer_account_id, spender, timestamp_range, token_id)
              select * from correct_timestamp_range where upper(timestamp_range) is not null
            )
            insert into nft_allowance (approved_for_all, owner, payer_account_id, spender, timestamp_range, token_id)
            select * from correct_timestamp_range where upper(timestamp_range) is null;

            commit;
            """;

    private final JdbcTemplate jdbcTemplate;

    @Lazy
    public SyntheticNftAllowanceOwnerMigration(@Owner JdbcTemplate jdbcTemplate, MirrorProperties mirrorProperties) {
        super(mirrorProperties.getMigration());
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String getDescription() {
        return "Updates the owner for synthetic nft allowances to the corresponding contract result sender";
    }

    @Override
    protected MigrationVersion getMinimumVersion() {
        // The version where contract_result sender_id was added
        return MigrationVersion.fromVersion("1.58.4");
    }

    @Override
    protected void doMigrate() {
        var stopwatch = Stopwatch.createStarted();
        jdbcTemplate.execute(UPDATE_NFT_ALLOWANCE_OWNER_SQL);
        log.info("Updated nft allowance owners in {}", stopwatch);
    }
}
