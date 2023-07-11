-- drop the materialized view entity_state_start
create or replace function change_access_privilege(grant_or_revoke boolean) returns void as
$$
begin
  if current_user <> '${db-user}' then
    if grant_or_revoke then
      grant create on schema public to ${db-user};
      grant ${db-user} to current_user;
    else
      revoke ${db-user} from current_user;
      revoke create on schema public from ${db-user};
    end if;
  end if;
end
$$ language plpgsql;

select change_access_privilege(true);

drop materialized view if exists entity_state_start;

select change_access_privilege(false);

drop function change_access_privilege(grant_or_revoke boolean);

-- add index to node_stake
create index if not exists node_stake__epoch_day on node_stake (epoch_day);

-- add timestamp_range to entity_stake
alter table if exists entity_stake
  add column if not exists timestamp_range int8range;

update entity_stake
set timestamp_range =
  int8range((select consensus_timestamp from node_stake where epoch_day = end_stake_period limit 1), null)
from node_stake;

alter table if exists entity_stake
  alter column timestamp_range set not null;

-- add entity_stake_history
create table if not exists entity_stake_history (like entity_stake including defaults);
create index if not exists entity_stake_history__id_lower_timestamp
  on entity_stake_history (id, lower(timestamp_range));
create index if not exists entity_stake_history__timestamp_range
  on entity_stake_history (timestamp_range);

-- trigger function to add history row
create function add_entity_stake_history() returns trigger as
$$
begin
  insert into entity_stake_history (
    decline_reward_start,
    end_stake_period,
    id,
    pending_reward,
    staked_node_id_start,
    staked_to_me,
    stake_total_start,
    timestamp_range)
  values (
    OLD.decline_reward_start,
    OLD.end_stake_period,
    OLD.id,
    OLD.pending_reward,
    OLD.staked_node_id_start,
    OLD.staked_to_me,
    OLD.stake_total_start,
    int8range(lower(OLD.timestamp_range), lower(NEW.timestamp_range)));
    return NULL; -- result is ignored since this is an AFTER trigger
end
$$ language plpgsql;

create trigger entity_stake_trigger
  after update on entity_stake
  for each row
  execute function add_entity_stake_history();
