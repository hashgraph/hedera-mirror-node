-- Enhances the entity table to track the historical state of the entity over time and the period for which it is valid for.

alter table if exists entity
    add column if not exists timestamp_range int8range;

update entity
set timestamp_range = int8range(coalesce(modified_timestamp, created_timestamp, 0), null);

alter table if exists entity
    alter column timestamp_range set not null,
    drop column if exists modified_timestamp;

create table if not exists entity_history
(
    like entity
);

alter table if exists entity_history
    add primary key (id, timestamp_range);
create index if not exists entity_history__timestamp_range on entity_history using gist (timestamp_range);

create or replace function entity_history() returns trigger as
$entity_history$
begin
    OLD.timestamp_range := int8range(lower(OLD.timestamp_range), lower(NEW.timestamp_range));
    insert into entity_history select OLD.*;
    return NEW;
end;
$entity_history$ language plpgsql;

create trigger entity_history
    after update
    on entity
    for each row
execute procedure entity_history();
