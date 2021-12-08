-------------------
-- Add support for automatic account creation alias
-------------------

-- Add nullable alias column
alter table if exists entity
    add column if not exists alias bytea null;

alter table if exists entity_history
    add column if not exists alias bytea null;

-- support retrieval by alias
create index if not exists entity__alias_deleted
    on entity (alias, deleted);
create unique index if not exists entity__alias_id
    on entity (alias, id);
