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
public class CustomFeesMigration extends RepeatableMigration {

    private static final String MIGRATION_SQL =
            """
            begin;
            create temp table custom_fee_temp (
              created_timestamp bigint    not null,
              fixed_fees        jsonb,
              fractional_fees   jsonb,
              royalty_fees      jsonb,
              timestamp_range   int8range not null,
              token_id          bigint    not null
            ) on commit drop;
            with altered_fees as (
              delete from custom_fee
              returning
              created_timestamp,
              token_id,
              case when denominating_token_id is not null and royalty_denominator is null then
                  jsonb_build_object(
                    'amount', amount,
                    'denominating_token_id', denominating_token_id,
                    'collector_account_id', collector_account_id,
                    'all_collectors_are_exempt', all_collectors_are_exempt) end as fixed_fees,
              case when amount_denominator is not null then
                  jsonb_build_object(
                    'amount', amount,
                    'amount_denominator', amount_denominator,
                    'maximum_amount', maximum_amount,
                    'minimum_amount', minimum_amount,
                    'net_of_transfers', net_of_transfers,
                    'collector_account_id', collector_account_id,
                    'all_collectors_are_exempt', all_collectors_are_exempt) end as fractional_fees,
              case when royalty_denominator is not null then
                jsonb_build_object(
                  'fallback_fee', jsonb_build_object(
                    'amount', amount,
                    'denominating_token_id', denominating_token_id,
                    'collector_account_id', collector_account_id,
                    'all_collectors_are_exempt', all_collectors_are_exempt),
                  'royalty_denominator', royalty_denominator,
                  'royalty_numerator', royalty_numerator,
                  'collector_account_id', collector_account_id,
                  'all_collectors_are_exempt', all_collectors_are_exempt) end as royalty_fees
            ), correct_timestamp_range as (
             select
               created_timestamp,
               token_id,
               fixed_fees,
               fractional_fees,
               royalty_fees,
               int8range(created_timestamp, (
                 select c.created_timestamp
                 from altered_fees c
                 where c.token_id = p.token_id
                   and c.created_timestamp > p.created_timestamp
                  order by c.created_timestamp
                  limit 1)) as timestamp_range
             from altered_fees p
            ), aggregated as (
              select
              created_timestamp,
              token_id,
              timestamp_range,
              jsonb_agg(fixed_fees) filter (where fixed_fees is not null) as fixed_fees,
              jsonb_agg(fractional_fees) filter (where fractional_fees is not null) as fractional_fees,
              jsonb_agg(royalty_fees) filter (where royalty_fees is not null) as royalty_fees
              from correct_timestamp_range
              group by created_timestamp, token_id, timestamp_range
            ) insert into custom_fee_temp (created_timestamp, token_id, timestamp_range, fixed_fees, fractional_fees, royalty_fees)
            select * from aggregated;

            with update_create_timestamp as (
              select min(created_timestamp) as created_timestamp,
              token_id
              from custom_fee_temp group by token_id
            ) update custom_fee_temp set created_timestamp = u.created_timestamp
            from update_create_timestamp u
            where custom_fee_temp.token_id = u.token_id;

            drop index if exists custom_fee__token_timestamp;
            alter table custom_fee
              drop column if exists all_collectors_are_exempt,
              drop column if exists amount,
              drop column if exists amount_denominator,
              drop column if exists collector_account_id,
              drop column if exists denominating_token_id,
              drop column if exists maximum_amount,
              drop column if exists minimum_amount,
              drop column if exists net_of_transfers,
              drop column if exists royalty_denominator,
              drop column if exists royalty_numerator,
              add column if not exists fixed_fees jsonb,
              add column if not exists fractional_fees jsonb,
              add column if not exists royalty_fees jsonb,
              add column if not exists timestamp_range int8range not null,
              add primary key (token_id);

            create table if not exists custom_fee_history
            (
              like custom_fee including constraints including defaults
            );

            insert into custom_fee_history (created_timestamp, fixed_fees, fractional_fees, royalty_fees, timestamp_range, token_id)
            select * from custom_fee_temp where upper(timestamp_range) is not null;

            insert into custom_fee (created_timestamp, fixed_fees, fractional_fees, royalty_fees, timestamp_range, token_id)
            select * from custom_fee_temp where upper(timestamp_range) is null;

            commit;
            """;

    private final JdbcTemplate jdbcTemplate;

    @Lazy
    public CustomFeesMigration(@Owner JdbcTemplate jdbcTemplate, MirrorProperties mirrorProperties) {
        super(mirrorProperties.getMigration());
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String getDescription() {
        return "Update the custom fee table to use aggregated fees";
    }

    @Override
    protected MigrationVersion getMinimumVersion() {
        return MigrationVersion.fromVersion("1.66.0");
    }

    @Override
    protected void doMigrate() {
        var stopwatch = Stopwatch.createStarted();
        jdbcTemplate.execute(MIGRATION_SQL);
        log.info("Updated custom fee and custom fee history in {}", stopwatch);
    }
}
