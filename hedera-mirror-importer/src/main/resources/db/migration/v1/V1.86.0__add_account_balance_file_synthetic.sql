alter table if exists account_balance_file
  add column synthetic boolean not null default false;