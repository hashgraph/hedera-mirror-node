-------------------
-- HIP-786: Enriched staking metadata exports
-------------------

-- Add new columns to network_stake
alter table if exists network_stake
    add column if not exists max_stake_rewarded bigint default null;
alter table if exists network_stake
    add column if not exists max_total_reward bigint default null;
alter table if exists network_stake
    add column if not exists reserved_staking_rewards bigint default null;
alter table if exists network_stake
    add column if not exists reward_balance_threshold bigint default null;
alter table if exists network_stake
    add column if not exists unreserved_staking_reward_balance bigint default null;
