package com.hedera.mirror.importer.migration;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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
import java.util.Map;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;

import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.MirrorProperties.HederaNetwork;

@Named
@RequiredArgsConstructor(onConstructor_ = {@Lazy})
public class RecalculatePendingRewardMigration extends MirrorBaseJavaMigration {

    static final Map<HederaNetwork, Long> FIRST_NONZERO_REWARD_RATE_TIMESTAMP = Map.of(
            HederaNetwork.MAINNET, 1666310400447390002L,
            HederaNetwork.TESTNET, 1659139200596847383L
    );

    // Recalculate pending reward since for some staking periods it's accumulated more than one time.
    private static final String MIGRATION_SQL = """
            with crypto_transfer as (
              select *
              from crypto_transfer
              where consensus_timestamp > :firstRewardTimestamp
            ), reward_rate as (
              select consensus_timestamp, epoch_day, node_id, reward_rate
              from node_stake
              where reward_rate <> 0 and epoch_day <= (select end_stake_period from entity_stake where id = 800)
            ), eligible_entity as (
              select id, staked_node_id, stake_period_start, r.consensus_timestamp as first_period_end_timestamp
              from entity
              left join reward_rate r on r.epoch_day = stake_period_start + 1 and r.node_id = staked_node_id
              where decline_reward is false and coalesce(staked_node_id, -1) <> -1 and coalesce(stake_period_start, -1) <> -1
                and coalesce(deleted, false) is false and type in ('ACCOUNT', 'CONTRACT')
            ), last_staking_reward_transfer as (
              select distinct on (account_id) account_id, consensus_timestamp
              from staking_reward_transfer
              order by account_id, consensus_timestamp desc
            ), first_period_stake_change as (
              select account_id, sum(amount) as amount
              from crypto_transfer cr
              join last_staking_reward_transfer l on l.account_id = cr.entity_id
              join eligible_entity e on e.id = l.account_id
              where cr.consensus_timestamp >= l.consensus_timestamp and cr.consensus_timestamp < first_period_end_timestamp
              group by account_id
            ), pending_reward as (
              select es.id as entity_id,
                ((stake_total_start - coalesce(f.amount,  0)) / 100000000)::bigint * coalesce((
                  select reward_rate
                  from reward_rate
                  where node_id = e.staked_node_id and epoch_day = e.stake_period_start + 1
                ), 0) +
                (stake_total_start / 100000000)::bigint * coalesce((
                  select sum(reward_rate)
                  from reward_rate
                  where node_id = e.staked_node_id and epoch_day > e.stake_period_start + 1
                ), 0) as amount
              from entity_stake es
              join eligible_entity e on e.id = es.id
              left join first_period_stake_change f on f.account_id = es.id
            )
            update entity_stake
            set pending_reward = amount
            from pending_reward
            where entity_id = id;
            """;
    private static final MigrationVersion VERSION = MigrationVersion.fromVersion("1.68.4");

    private final NamedParameterJdbcOperations jdbcOperations;
    private final MirrorProperties mirrorProperties;

    @Override
    public String getDescription() {
        return "Recalculate pending reward";
    }

    @Override
    public MigrationVersion getVersion() {
        return VERSION;
    }

    @Override
    protected void doMigrate() {
        var network = mirrorProperties.getNetwork();
        Long consensusTimestamp = FIRST_NONZERO_REWARD_RATE_TIMESTAMP.get(network);
        if (consensusTimestamp == null) {
            return;
        }

        var stopwatch = Stopwatch.createStarted();
        var params = new MapSqlParameterSource("firstRewardTimestamp", consensusTimestamp);
        int count = jdbcOperations.update(MIGRATION_SQL, params);
        log.info("Recalculated pending reward for {} {} entities in {}", count, network, stopwatch);
    }
}
