-- Add index for query by consensus_timestamp followed by token_id
create index if not exists token_balance__timestamp_token_account
    on token_balance (consensus_timestamp desc, token_id desc, account_id desc);
