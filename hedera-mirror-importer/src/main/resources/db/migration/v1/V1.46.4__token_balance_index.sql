-- Adds an index to speed up token balances query

create index if not exists token_balance__timestamp_token on token_balance (consensus_timestamp desc, token_id);
