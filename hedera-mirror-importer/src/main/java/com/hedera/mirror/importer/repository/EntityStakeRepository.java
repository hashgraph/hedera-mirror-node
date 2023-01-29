package com.hedera.mirror.importer.repository;

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

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

import com.hedera.mirror.common.domain.entity.EntityStake;

public interface EntityStakeRepository extends CrudRepository<EntityStake, Long> {

    @Query(value = """
            with last_epoch_day as (
              select coalesce((select epoch_day from node_stake order by consensus_timestamp desc limit 1), -1) as epoch_day
            ), entity_stake_info as (
              select coalesce((select end_stake_period from entity_stake where id = 800), -1) as end_stake_period
            ), staking_reward_account as (
              select (select id from entity where id = 800) as account_id
            )
            select account_id is null or epoch_day = -1 or epoch_day = end_stake_period
            from last_epoch_day, entity_stake_info, staking_reward_account
            """, nativeQuery = true)
    boolean updated();

    /**
     * Updates entity stake state based on the current entity stake state, the ending period node reward rate and the
     * entity state snapshot at the beginning of the new staking period.
     * <p>
     * Algorithm to update pending reward:
     * <p>
     * 1. IF there is no such row in entity_stake (new entity created in the ending stake period), OR its
     * decline_reward_start is true (decline reward for the ending staking period), OR it didn't stake to a node for the
     * ending staking period, the new pending reward is 0
     * <p>
     * 2. IF there is no node stake info for the node the entity staked to, the pending reward keeps the same
     * <p>
     * 3. IF the current stake_period_start > last epochDay - 1, no reward's should be earned, set pending reward to 0
     * <p>
     * 4. IF the current stake_period_start equals to the last epochDay (either its staking metadata or balance changed
     * in the previous staking period), calculate the reward it has earned in the ending staking period as its pending
     * reward
     * <p>
     * 5. Otherwise, there's no staking metadata or balance change for the entity since the start of the ending staking
     * period, add the reward earned in the ending period to the current as the new pending reward
     *
     * @return Number of entity state inserted and updated
     */
    @Modifying
    @Query(value = """
            with ending_period as (
              select epoch_day, consensus_timestamp
              from node_stake
              where consensus_timestamp = (select max(consensus_timestamp) from node_stake)
              limit 1
            ), ending_period_stake_state as (
              select
                decline_reward_start,
                id as entity_id,
                pending_reward,
                staked_node_id_start,
                stake_total_start,
                reward_rate
              from entity_stake es
              left join (
                select node_id, reward_rate
                from node_stake ns, ending_period
                where ns.consensus_timestamp = ending_period.consensus_timestamp
              ) node_stake on es.staked_node_id_start = node_id
            ), proxy_staking as (
              select staked_account_id, sum(balance) as staked_to_me
              from entity_state_start
              where staked_account_id <> 0
              group by staked_account_id
            ), updated as (
              select
                ess.decline_reward as decline_reward_start,
                epoch_day as end_stake_period,
                ess.id,
                (case
                   when coalesce(decline_reward_start, true) is true
                        or coalesce(staked_node_id_start, -1) = -1
                        then 0
                   when reward_rate is null then pending_reward
                   when ess.stake_period_start > epoch_day - 1 then 0
                   when ess.stake_period_start = epoch_day - 1
                        then reward_rate * (stake_total_start / 100000000)
                   else pending_reward + reward_rate * (stake_total_start / 100000000)
                  end) as pending_reward,
                ess.staked_node_id as staked_node_id_start,
                coalesce(ps.staked_to_me, 0) as staked_to_me,
                (case when ess.decline_reward is true or ess.staked_node_id = -1 then 0
                      else ess.balance + coalesce(ps.staked_to_me, 0)
                  end) as stake_total_start
              from entity_state_start ess
                left join ending_period_stake_state on entity_id = ess.id
                left join proxy_staking ps on ps.staked_account_id = ess.id,
                ending_period
            )
            insert into entity_stake
            table updated
            on conflict (id) do update
              set decline_reward_start = excluded.decline_reward_start,
                  end_stake_period     = excluded.end_stake_period,
                  pending_reward       = excluded.pending_reward,
                  staked_node_id_start = excluded.staked_node_id_start,
                  staked_to_me         = excluded.staked_to_me,
                  stake_total_start    = excluded.stake_total_start;
            """, nativeQuery = true)
    @Transactional
    int updateEntityStake();
}
