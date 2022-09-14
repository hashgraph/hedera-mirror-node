with entity_state as (
  select
    decline_reward,
    id,
    staked_account_id,
    staked_node_id,
    stake_period_start
  from entity
  where deleted is not true and type in ('ACCOUNT', 'CONTRACT') and timestamp_range @> 1663113600714587002
union all
  select
    decline_reward,
    id,
    staked_account_id,
    staked_node_id,
    stake_period_start
  from entity_history
  where deleted is not true and type in ('ACCOUNT', 'CONTRACT') and timestamp_range @> 1663113600714587002
), balance_timestamp as (
  select consensus_timestamp from account_balance_file where consensus_timestamp < 1663113600714587002 order by consensus_timestamp desc limit 1
), balance_snapshot as (
  select account_id, balance
  from account_balance
  join entity_state on account_id = id
  join balance_timestamp on balance_timestamp.consensus_timestamp = account_balance.consensus_timestamp
), balance_change as (
  select entity_id, sum(amount) as amount
  from crypto_transfer
  join balance_timestamp on crypto_transfer.consensus_timestamp > balance_timestamp.consensus_timestamp
  join entity_state on entity_id = id
  where crypto_transfer.consensus_timestamp < 1663113600714587002
  group by entity_id
)
select
  coalesce(balance, 0) + coalesce(amount, 0)
  decline_reward,
  id,
  coalesce(staked_account_id, 0)   as staked_account_id,
  coalesce(staked_node_id, -1)     as staked_node_id,
  coalesce(stake_period_start, -1) as stake_period_start
from entity_state
left join balance_snapshot on account_id = id
left join balance_change on entity_id = id
order by id limit 1000;