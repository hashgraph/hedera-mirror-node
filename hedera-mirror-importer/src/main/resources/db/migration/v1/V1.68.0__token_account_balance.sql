alter table if exists token_account add column if not exists balance bigint null;
alter table if exists token_account_history add column if not exists balance bigint null;
