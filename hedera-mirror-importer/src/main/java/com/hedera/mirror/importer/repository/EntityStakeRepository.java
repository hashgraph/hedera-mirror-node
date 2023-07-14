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

package com.hedera.mirror.importer.repository;

import com.hedera.mirror.common.domain.entity.EntityStake;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

public interface EntityStakeRepository extends CrudRepository<EntityStake, Long> {

    @Modifying
    @Query(
            value =
                    """
        create temp table entity_state_start (
          balance            bigint not null,
          decline_reward     boolean not null,
          id                 bigint not null,
          staked_account_id  bigint not null,
          staked_node_id     bigint not null,
          stake_period_start bigint not null
        ) on commit drop;

        with end_period as (
          select epoch_day, consensus_timestamp
          from node_stake
          where epoch_day = coalesce(
            (select end_stake_period + 1 from entity_stake where id = 800),
            (select max(epoch_day) from node_stake)
          )
          limit 1
        ), balance_timestamp as (
             select abf.consensus_timestamp, (abf.consensus_timestamp + abf.time_offset) adjusted_consensus_timestamp
             from account_balance_file abf, end_period ep
             where abf.consensus_timestamp + abf.time_offset <= ep.consensus_timestamp
             order by abf.consensus_timestamp desc
             limit 1
        ), entity_state as (
          select
            decline_reward,
            id,
            staked_account_id,
            staked_node_id,
            stake_period_start
          from entity, end_period
          where deleted is not true and type in ('ACCOUNT', 'CONTRACT') and timestamp_range @> end_period.consensus_timestamp
          union all
          select *
          from (
            select
              distinct on (id)
              decline_reward,
              id,
              staked_account_id,
              staked_node_id,
              stake_period_start
            from entity_history, end_period
            where deleted is not true and type in ('ACCOUNT', 'CONTRACT') and timestamp_range @> end_period.consensus_timestamp
            order by id, timestamp_range desc
          ) as latest_history
        ), balance_snapshot as (
          select account_id, balance
          from account_balance ab
          join balance_timestamp bt on bt.consensus_timestamp = ab.consensus_timestamp
        )
        insert into entity_state_start (balance, decline_reward, id, staked_account_id, staked_node_id, stake_period_start)
        select
          coalesce(balance, 0) + coalesce(change, 0),
          decline_reward,
          id,
          coalesce(staked_account_id, 0),
          coalesce(staked_node_id, -1),
          coalesce(stake_period_start, -1)
        from entity_state
        left join balance_snapshot on account_id = id
        left join (
          select entity_id, sum(amount) as change
          from crypto_transfer ct, balance_timestamp bt, end_period ep
          where ct.consensus_timestamp <= ep.consensus_timestamp
            and ct.consensus_timestamp > bt.adjusted_consensus_timestamp
          group by entity_id
           order by entity_id
         ) balance_change on entity_id = id,
         balance_timestamp bt
         where bt.consensus_timestamp is not null;

        create index if not exists entity_state_start__id on entity_state_start (id);
        create index if not exists entity_state_start__staked_account_id
          on entity_state_start (staked_account_id) where staked_account_id <> 0;
        """,
            nativeQuery = true)
    @Transactional
    void createEntityStateStart();

    @Query(value = "select endStakePeriod from EntityStake where id = 800")
    Optional<Long> getEndStakePeriod();

    @Modifying
    @Query(value = "lock table entity_history in share row exclusive mode nowait", nativeQuery = true)
    @Transactional
    void lockFromConcurrentUpdates();

    @Query(
            value =
                    """
            with last_epoch_day as (
              select coalesce((select epoch_day from node_stake order by consensus_timestamp desc limit 1), -1) as epoch_day
            ), entity_stake_info as (
              select coalesce((select end_stake_period from entity_stake where id = 800), -1) as end_stake_period
            ), staking_reward_account as (
              select (select id from entity where id = 800) as account_id
            )
            select account_id is null or epoch_day = -1 or epoch_day = end_stake_period
            from last_epoch_day, entity_stake_info, staking_reward_account
            """,
            nativeQuery = true)
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
     * 3. IF the current stake_period_start > the day before last epochDay, no reward's should be earned, set pending
     * reward to 0
     * <p>
     * 4. IF the current stake_period_start equals to the day before last epochDay (either its staking metadata or
     * balance changed in the previous staking period), calculate the reward it has earned in the ending staking period
     * as its pending reward
     * <p>
     * 5. IF the current stake_period_start is more than 365 days before the last epochDay, deduct the reward earned
     * in staking period last epochDay - 365, and add the reward earned in last epochDay since staking reward is kept
     * for up to 365 days counting back from the last staking period.
     * <p>
     * 6. Otherwise, there's no staking metadata or balance change for the entity since the start of the ending staking
     * period, add the reward earned in the ending period to the current as the new pending reward
     */
    @Modifying
    @Query(
            value =
                    """
            create temp table entity_stake_temp (like entity_stake) on commit drop;
            with ending_period as (
              select epoch_day, consensus_timestamp
              from node_stake
              where epoch_day = coalesce(
                (select end_stake_period + 1 from entity_stake where id = 800),
                (select max(epoch_day) from node_stake)
              )
              limit 1
            ), ending_period_stake_state as (
              select
                decline_reward_start,
                id as entity_id,
                pending_reward,
                staked_node_id_start,
                (stake_total_start / 100000000) as stake_total_start_whole_bar,
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
            )
            insert into entity_stake_temp (decline_reward_start, end_stake_period, id, pending_reward,
              staked_node_id_start, staked_to_me, stake_total_start, timestamp_range)
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
                 when ess.stake_period_start = epoch_day - 1 then reward_rate * stake_total_start_whole_bar
                 when epoch_day - ess.stake_period_start > 365
                   then pending_reward + reward_rate * stake_total_start_whole_bar
                     - coalesce((select reward_rate from node_stake where epoch_day = ep.epoch_day - 365 and node_id = staked_node_id_start), 0) * stake_total_start_whole_bar
                 else pending_reward + reward_rate * stake_total_start_whole_bar
                end) as pending_reward,
              ess.staked_node_id as staked_node_id_start,
              coalesce(ps.staked_to_me, 0) as staked_to_me,
              (case when ess.decline_reward is true or ess.staked_node_id = -1 then 0
                    else ess.balance + coalesce(ps.staked_to_me, 0)
               end) as stake_total_start,
              int8range(ep.consensus_timestamp, null) as timestamp_range
            from entity_state_start ess
              left join ending_period_stake_state on entity_id = ess.id
              left join proxy_staking ps on ps.staked_account_id = ess.id,
              ending_period ep;
            create index on entity_stake_temp (id);

            -- history table
            insert into entity_stake_history (
                decline_reward_start,
                end_stake_period,
                id,
                pending_reward,
                staked_node_id_start,
                staked_to_me,
                stake_total_start,
                timestamp_range
              )
            select
              e.decline_reward_start,
              e.end_stake_period,
              e.id,
              e.pending_reward,
              e.staked_node_id_start,
              e.staked_to_me,
              e.stake_total_start,
              int8range(lower(e.timestamp_range), lower(t.timestamp_range))
            from entity_stake e
            join entity_stake_temp t on t.id = e.id;

            -- current table
            insert into entity_stake (
                decline_reward_start,
                end_stake_period,
                id,
                pending_reward,
                staked_node_id_start,
                staked_to_me,
                stake_total_start,
                timestamp_range
              )
            select
              t.decline_reward_start,
              t.end_stake_period,
              t.id,
              t.pending_reward,
              t.staked_node_id_start,
              t.staked_to_me,
              t.stake_total_start,
              t.timestamp_range
            from entity_stake_temp t
            on conflict (id) do update
            set
              decline_reward_start = excluded.decline_reward_start,
              end_stake_period = excluded.end_stake_period,
              pending_reward = excluded.pending_reward,
              staked_node_id_start = excluded.staked_node_id_start,
              staked_to_me = excluded.staked_to_me,
              stake_total_start = excluded.stake_total_start,
              timestamp_range = excluded.timestamp_range;
            """,
            nativeQuery = true)
    @Transactional
    void updateEntityStake();
}
