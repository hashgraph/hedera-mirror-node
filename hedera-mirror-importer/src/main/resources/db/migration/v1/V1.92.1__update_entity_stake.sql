delete from entity_stake where decline_reward_start is true;

alter table if exists entity_stake
    drop column if exists decline_reward_start;
alter table if exists entity_stake_history
    drop column if exists decline_reward_start;