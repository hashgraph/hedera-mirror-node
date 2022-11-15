-- fix stake_period_start after a reward payout
with last_paid_staking_reward as (
  select distinct on(account_id) account_id, consensus_timestamp
  from staking_reward_transfer
  order by account_id, consensus_timestamp desc
), incorrect as (
  select id as entity_id
  from entity e
  join last_paid_staking_reward l on l.account_id = e.id
  where lower(timestamp_range) < consensus_timestamp and coalesce(deleted, false) is false
    and coalesce(staked_node_id, -1) <> -1 and decline_reward is false
)
update entity
set stake_period_start = stake_period_start - 1
from incorrect
where entity_id = id;