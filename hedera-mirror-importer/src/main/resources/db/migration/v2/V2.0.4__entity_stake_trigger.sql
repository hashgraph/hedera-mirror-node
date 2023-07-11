-- trigger function to add history row
create function add_entity_stake_history() returns trigger as
$$
begin
  execute format(
    'insert into %s (decline_reward_start, end_stake_period, id, pending_reward, staked_node_id_start,
       staked_to_me, stake_total_start, timestamp_range)
     values (($1).decline_reward_start, ($1).end_stake_period, ($1).id, ($1).pending_reward, ($1).staked_node_id_start,
       ($1).staked_to_me, ($1).stake_total_start, int8range(lower(($1).timestamp_range), lower(($2).timestamp_range)))', TG_ARGV[0])
       using OLD, NEW;
  return NULL; -- result is ignored since this is an AFTER trigger
end
$$ language plpgsql;
-- create trigger entity_stake_trigger
--   after update on entity_stake
--   for each row
--   execute function add_entity_stake_history();
