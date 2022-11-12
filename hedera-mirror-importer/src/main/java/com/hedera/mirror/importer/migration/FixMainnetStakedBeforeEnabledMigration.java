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
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;

import com.hedera.mirror.importer.MirrorProperties;

@Named
public class FixMainnetStakedBeforeEnabledMigration extends RepeatableMigration {

    private static final String MIGRATION_SQL = """
            --- The migration fixes the staking settings for accounts started to stake to a node before mainnet 0.27.x release.
            --- 1658419200981687000 is the consensus end of the last mainnet HAPI 0.26.0 record file
            with mainnet_last_26_file as (
              select index from record_file where consensus_end = 1658419200981687000
            ), possible as (
            -- find accounts / contracts whose stake period start is still on or before 2022-07-21 UTC
              select id,deleted,decline_reward,staked_node_id,stake_period_start,timestamp_range
              from entity
              where stake_period_start <= 19194 and stake_period_start <> -1 and staked_node_id <> -1 and type in ('ACCOUNT', 'CONTRACT')
            ), history as (
            --- if the staking setting first occurs in the history table, find the oldest matching history row
              select distinct on (h.id) h.id, h.stake_period_start, h.timestamp_range
              from entity_history h
              join possible p on p.id = h.id and p.stake_period_start = h.stake_period_start and p.staked_node_id = h.staked_node_id
              order by h.id, h.timestamp_range
            ), staked_before_alive as (
            --- mainnet 0.27.0 upgraded at round 2022-07-21 16:00:00 UTC, make sure only fix such settings
            --- set at or before the consensus end of the last HAPI 0.26.0 record file
              select p.id as entity_id
              from possible p
              left join history h on h.id = p.id
              where coalesce(lower(h.timestamp_range), lower(p.timestamp_range)) <= 1658419200981687000
            ), fix_entity_stake as (
              update entity_stake
              set pending_reward = 0,
                  staked_node_id_start = -1
              from staked_before_alive, mainnet_last_26_file
              where id = entity_id
            )
            update entity
            set staked_node_id = -1,
                stake_period_start = -1
            from staked_before_alive, mainnet_last_26_file
            where id = entity_id;
            """;

    private final JdbcTemplate jdbcTemplate;
    private final MirrorProperties mirrorProperties;

    @Lazy
    public FixMainnetStakedBeforeEnabledMigration(JdbcTemplate jdbcTemplate, MirrorProperties mirrorProperties) {
        super(mirrorProperties.getMigration());
        this.jdbcTemplate = jdbcTemplate;
        this.mirrorProperties = mirrorProperties;
    }

    @Override
    public String getDescription() {
        return "Fix the staking information for mainnet accounts which configured staking before the feature is enabled";
    }

    @Override
    protected void doMigrate() {
        if (mirrorProperties.getNetwork() != MirrorProperties.HederaNetwork.MAINNET) {
            return;
        }

        var stopwatch = Stopwatch.createStarted();
        int count = jdbcTemplate.update(MIGRATION_SQL);
        log.info("Fixed staking information for {} mainnet accounts in {}", count, stopwatch);
    }
}
