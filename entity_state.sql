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
), balance_snapshot as (
  select account_id, balance
  from account_balance
  where consensus_timestamp = (select consensus_timestamp from account_balance_file where consensus_timestamp < 1663113600714587002 order by consensus_timestamp desc limit 1)
), crypto_transfer_range as (
  select entity_id, amount
  from crypto_transfer
  where crypto_transfer.consensus_timestamp < 1663113600714587002 and
    consensus_timestamp > (select consensus_timestamp from account_balance_file where consensus_timestamp < 1663113600714587002 order by consensus_timestamp desc limit 1)
)
select
  coalesce((select balance from balance_snapshot where account_id = id), 0) +
    coalesce((select sum(amount) from crypto_transfer_range where entity_id = id), 0) as balance,
  decline_reward,
  id,
  coalesce(staked_account_id, 0)   as staked_account_id,
  coalesce(staked_node_id, -1)     as staked_node_id,
  coalesce(stake_period_start, -1) as stake_period_start
from entity_state;
