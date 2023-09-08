alter table if exists entity
  add column if not exists balance_timestamp bigint null;

alter table if exists entity_history
    add column if not exists balance_timestamp bigint null;

alter table if exists token_account
  add column if not exists balance_timestamp bigint not null;

alter table if exists token_account_history
    add column if not exists balance_timestamp bigint not null;