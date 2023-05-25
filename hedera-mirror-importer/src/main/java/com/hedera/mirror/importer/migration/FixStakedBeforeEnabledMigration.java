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
import com.hedera.mirror.importer.util.Utility;
import jakarta.inject.Named;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;

@Named
@RequiredArgsConstructor(onConstructor_ = {@Lazy})
public class FixStakedBeforeEnabledMigration extends MirrorBaseJavaMigration {

    static final Map<MirrorProperties.HederaNetwork, Long> LAST_HAPI_26_RECORD_FILE_CONSENSUS_END = Map.of(
            MirrorProperties.HederaNetwork.MAINNET, 1658419200981687000L,
            MirrorProperties.HederaNetwork.TESTNET, 1656691197976341207L);

    private static final String MIGRATION_SQL =
            """
            --- The migration fixes the staking settings for accounts started to stake to a node before 0.27.x release.
            with last_26_file as (
              select index from record_file where consensus_end = :consensusEnd
            ), possible as (
            -- find accounts / contracts whose stake period start is still on or before the day 0.27.0 is deployed
              select id,decline_reward,staked_node_id,stake_period_start,timestamp_range
              from entity
              where stake_period_start <= :epochDay and stake_period_start <> -1 and staked_node_id <> -1 and type in ('ACCOUNT', 'CONTRACT')
            ), history as (
            --- if the staking setting first occurs in the history table, find the oldest matching history row
              select distinct on (h.id) h.id, h.stake_period_start, h.timestamp_range
              from entity_history h
              join possible p on p.id = h.id and p.stake_period_start = h.stake_period_start and p.staked_node_id = h.staked_node_id
              order by h.id, h.timestamp_range
            ), staked_before_alive as (
            --- network 0.27.0 upgrade happened during a UTC day, make sure only fix such settings set at or before
            --- the consensus end of the last HAPI 0.26.0 record file
              select p.id as entity_id
              from possible p
              left join history h on h.id = p.id
              where coalesce(lower(h.timestamp_range), lower(p.timestamp_range)) <= :consensusEnd
            ), fix_entity_stake as (
              update entity_stake
              set pending_reward = 0,
                  staked_node_id_start = -1
              from staked_before_alive, last_26_file
              where id = entity_id
            ), fix_entity_history as (
              update entity_history
              set staked_node_id = -1,
                  stake_period_start = -1
              from staked_before_alive, last_26_file
              where id = entity_id
            )
            update entity
            set staked_node_id = -1,
                stake_period_start = -1
            from staked_before_alive, last_26_file
            where id = entity_id;
            """;
    private static final MigrationVersion VERSION = MigrationVersion.fromVersion("1.68.3");

    private final NamedParameterJdbcOperations jdbcOperations;
    private final MirrorProperties mirrorProperties;

    @Override
    public MigrationVersion getVersion() {
        return VERSION;
    }

    @Override
    public String getDescription() {
        return "Fix the staking information for accounts which configured staking before the feature is enabled";
    }

    @Override
    protected void doMigrate() {
        var network = mirrorProperties.getNetwork();
        Long consensusEnd = LAST_HAPI_26_RECORD_FILE_CONSENSUS_END.get(network);
        if (consensusEnd == null) {
            return;
        }

        var stopwatch = Stopwatch.createStarted();
        long epochDay = Utility.getEpochDay(consensusEnd);
        var params = new MapSqlParameterSource()
                .addValue("consensusEnd", consensusEnd)
                .addValue("epochDay", epochDay);
        int count = jdbcOperations.update(MIGRATION_SQL, params);
        log.info("Fixed staking information for {} {} accounts in {}", count, network, stopwatch);
    }
}
