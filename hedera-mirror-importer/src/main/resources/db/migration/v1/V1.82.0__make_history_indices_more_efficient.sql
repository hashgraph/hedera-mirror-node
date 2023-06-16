-- drop primary key constraints
alter table crypto_allowance_history  drop constraint crypto_allowance_history_pkey;
alter table entity_history            drop constraint entity_history_pkey;
alter table nft_allowance_history     drop constraint nft_allowance_history_pkey;
alter table token_account_history     drop constraint token_account_history_pkey;
alter table token_allowance_history   drop constraint token_allowance_history_pkey;

-- create new btree index on loswer(timestamp_range) for each of the above, and also for token_account_history
create index if not exists crypto_allowance_history__owner_spender__lower_timestamp
  on crypto_allowance_history (owner, spender, lower(timestamp_range));
create index if not exists entity_history__lower_timestamp
  on entity_history (id, lower(timestamp_range));
create index if not exists nft_allowance_history__owner_spender__lower_timestamp
  on nft_allowance_history (owner, spender, lower(timestamp_range));
create index if not exists token_allowance_history__owner_spender__lower_timestamp
  on token_allowance_history (owner, spender, lower(timestamp_range));

create index if not exists token_account_history__account_token_lower_timestamp
  on token_account_history (account_id, token_id, lower(timestamp_range));
create index if not exists token_account_history__timestamp_range
  on token_account_history using gist (timestamp_range);
