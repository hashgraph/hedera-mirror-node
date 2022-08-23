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

    String UPDATE_ENTITY_STAKE_SQL = """
            with ending_period_node_stake as (
              select node_id, epoch_day, reward_rate
              from node_stake
              where consensus_timestamp = (select max(consensus_timestamp) from node_stake)
            ), proxy_staking as (
              select staked_account_id, sum(balance) as staked_to_me
              from entity_state_start
              where deleted is false and staked_account_id <> 0
              group by staked_account_id
            ), updated as (
              select
                ess.decline_reward as decline_reward_start,
                (select epoch_day from ending_period_node_stake limit 1) as end_stake_period,
                ess.id,
                (case
                   when ess.deleted is true
                        or coalesce(es.decline_reward_start, true) is true
                        or coalesce(es.staked_node_id_start, -1) = -1
                        then 0
                   when ess.stake_period_start >= (select epoch_day from ending_period_node_stake limit 1)
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
            """;

    @Modifying
    @Query(value = UPDATE_ENTITY_STAKE_SQL, nativeQuery = true)
    @Transactional
    int updateEntityStake();
}
