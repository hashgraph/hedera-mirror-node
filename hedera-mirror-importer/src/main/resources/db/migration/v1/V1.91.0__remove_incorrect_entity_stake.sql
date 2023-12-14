create or replace procedure remove_incorrect_entity_stake()  as
$$
declare
  one_sec_in_ns constant bigint := 10^9;

  last_known_good_timestamp bigint;
begin
  -- The last known good entity stake's timestamp_range should contain the time when V1.89.2 was installed.
  select (extract(epoch from installed_on) * one_sec_in_ns)::bigint into last_known_good_timestamp
  from flyway_schema_history
  where version = '1.89.2';
  if not found then
    return;
  end if;

  raise notice 'last_known_good_timestamp: %', last_known_good_timestamp;

  select 1
  from entity_stake_history
  where id = 800 and timestamp_range @> last_known_good_timestamp;
  if not found then
    return;
  end if;

  truncate table entity_stake;
  insert into entity_stake (decline_reward_start, end_stake_period, id, pending_reward, staked_node_id_start,
    staked_to_me, stake_total_start, timestamp_range)
  select
    decline_reward_start,
    end_stake_period,
    id,
    pending_reward,
    staked_node_id_start,
    staked_to_me,
    stake_total_start,
    int8range(lower(timestamp_range), null)
  from entity_stake_history
  where timestamp_range @> last_known_good_timestamp;

  create index if not exists entity_stake_history__upper_timestamp
    on entity_stake_history (upper(timestamp_range));
  delete from entity_stake_history
  where upper(timestamp_range) > last_known_good_timestamp;
--   delete from entity_stake_history
--   where timestamp_range @> last_known_good_timestamp or lower(timestamp_range) > last_known_good_timestamp;
  drop index if exists entity_stake_history__upper_timestamp;
end;
$$ language plpgsql;

call remove_incorrect_entity_stake();

drop procedure if exists remove_incorrect_entity_stake();