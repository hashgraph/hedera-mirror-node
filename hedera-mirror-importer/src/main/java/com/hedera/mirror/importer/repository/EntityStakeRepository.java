package com.hedera.mirror.importer.repository;

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
              select coalesce((select end_stake_period from entity_stake limit 1), -1) as end_stake_period
            )
            select case when epoch_day = -1 then true
                        else epoch_day = end_stake_period
                    end
            from last_epoch_day, entity_stake_info
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
     * 2.IF there is no node stake info for the node the entity staked to, the pending reward keeps the same
     * <p>
     * 3. IF the current stake_period_start >= the last epochDay from node stake update (either its staking metadata
     * or balance changed in the ending staking period), calculate the reward it has earned in the ending staking period
     * as its pending reward
     * <p>
     * 4. Otherwise, there's no staking metadata or balance change for the entity since the start of the ending staking
     * period, add the reward earned in the ending period to the current as the new pending reward
     *
     * @return Number of entity state inserted and updated
     */
    @Modifying
    @Query(value = """
            with ending_period_node_stake as (
              select node_id, epoch_day, reward_rate
              from node_stake
              where consensus_timestamp = (select max(consensus_timestamp) from node_stake)
            ), proxy_staking as (
              select staked_account_id, sum(balance) as staked_to_me
              from entity_state_start
              where staked_account_id <> 0
              group by staked_account_id
            ), updated as (
              select
                ess.decline_reward as decline_reward_start,
                (select epoch_day from ending_period_node_stake limit 1) as end_stake_period,
                ess.id,
                (case
                   when coalesce(es.decline_reward_start, true) is true
                        or coalesce(es.staked_node_id_start, -1) = -1
                        then 0
                   when node_id is null then es.pending_reward
                   when ess.stake_period_start >= epoch_day
                        then reward_rate * (es.stake_total_start / 100000000)
                   else es.pending_reward + reward_rate * (es.stake_total_start / 100000000)
                  end) as pending_reward,
                ess.staked_node_id as staked_node_id_start,
                coalesce(ps.staked_to_me, 0) as staked_to_me,
                (case when ess.decline_reward is true or ess.staked_node_id = -1 then 0
                      else ess.balance + coalesce(ps.staked_to_me, 0)
                  end) as stake_total_start
              from entity_state_start ess
              left join entity_stake es on es.id = ess.id
              left join ending_period_node_stake on node_id = es.staked_node_id_start
              left join proxy_staking ps on ps.staked_account_id = ess.id
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
