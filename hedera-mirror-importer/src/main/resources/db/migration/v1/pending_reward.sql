with last_period_node_stake as (
  select node_id, reward_rate
  from node_stake
  where reward_rate <> 0 and consensus_timestamp = (select max(consensus_timestamp from node_stake))
), entity_reward as (
  select
    id,
    (pending_reward + (reward_rate * (stake_total_previous / 100000000))) as pending_reward
  from entity
  join last_period_node_stake on node_id = staked_node_id_previous
  where stake_total_previous <> 0 and decline_reward is false and deleted is not true
)
update entity e
set pending_reward = er.pending_reward
from entity_reward er
where e.id = er.id;


with proxy_staking as (
  select staked_account_id, sum(balance) as staked_to_me
  from entity
  where deleted is not true and staked_account_id is not null and staked_account_id <> 0
  group by staked_account_id
)
update entity
set staked_to_me = staked_to_me,
    decline_reward_previous = decline_reward,
    staked_node_id_previous = staked_node_id,
    stake_total_previous = coalesce(staked_to_me, 0) + balance
from entity e
left join proxy_staking on staked_account_id = e.id
where id = e.id and deleted is not true and type in ('ACCOUNT', 'CONTRACT');


with ending_period_node_stake as (
  select node_id, epoch_day, reward_rate
  from node_stake
  where reward_rate <> 0 and consensus_timestamp = (select max(consensus_timestamp) from node_stake)
), proxy_staking as (
  select staked_account_id, sum(balance) as staked_to_me
  from entity_state_start
  where staked_account_id <> 0
  group by staked_account_id
), updated as (
  select
    ess.decline_reward as decline_reward_start,
    ess.id,
    (case
       when ess.deleted is false
         or coalesce(es.decline_reward_previous, true) is true
         or coalesce(es.staked_node_id_previous, -1) = -1
         or ess.stake_period_start = -1
         or ess.stake_period_start >= (select epoch_day from ending_period_node_stake limit 1)
         then 0
       else coalesce(es.pending_reward, 0) + (reward_rate * (es.stake_total_previous / 100000000))
      end) as pending_reward,
    ess.staked_node_id as staked_node_id_start,
    coalesce(ps.staked_to_me, 0) as staked_to_me,
    (case when ess.decline_reward is true or ess.staked_node_id = -1 then 0
          else ess.balance + coalesce(ps.staked_to_me, 0)
      end) as stake_total_start
  from entity_state_start ess
  left join entity_stake es on es.id = ess.id
  left join ending_period_node_stake on node_id = es.staked_node_id_previous
  left join proxy_staking ps on ps.staked_account_id = ess.id
)
insert into entity_stake
table updated
on conflict (id) do update
  set decline_reward_previous = excluded.decline_reward_previous,
      pending_reward          = excluded.pending_reward,
      staked_node_id_previous = excluded.staked_node_id_previous,
      staked_to_me            = excluded.staked_to_me,
      stake_total_previous    = excluded.stake_total_previous;


update entity_stake
set pending_reward = case when (deleted is not null and deleted is true)
  or decline_reward_previous is true
  or staked_node_id_previous = -1
                            then 0
                          when stake_period_start >= epoch_day
                            then reward_rate * (stake_total_previous / 100000000)
                          else pending_reward + reward_rate * (stake_total_previous / 100000000)
  end
from entity_stake_reward_rate
       right outer join entity_state_start ess on ess.id = entity_id
where entity_id = id
