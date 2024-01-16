alter table if exists entity_stake
    drop column if exists decline_reward_start;
alter table if exists entity_stake_history
    drop column if exists decline_reward_start;