alter table if exists contract add column if not exists max_automatic_token_associations integer not null default 0;
alter table if exists contract_history
  add column if not exists max_automatic_token_associations integer not null default 0;

alter table if exists contract alter column max_automatic_token_associations drop default;
alter table if exists contract_history alter column max_automatic_token_associations drop default;
