-- drop primary key constraints and replace with new ones based on lower(timetamp_range)
alter table crypto_allowance_history
    drop constraint crypto_allowance_history_pkey,
    add constraint crypto_allowance_history__pk primary key (owner, spender, lower(timestamp_range));
alter table entity_history
    drop constraint entity_history_pkey,
    add constraint entity_history__pk primary key (id, lower(timestamp_range));
alter table nft_allowance_history
    drop constraint nft_allowance_history_pkey,
    add constraint nft_allowance_history__pk primary key (owner, spender, token_id, lower(timestamp_range));
alter table token_account_history
    drop constraint token_account_history_pkey,
    add constraint token_account_history__pk primary key (account_id, token_id, lower(timestamp_range));
alter table token_allowance_history
    drop constraint token_allowance_history_pkey,
    add constraint token_allowance_history__pk primary key (owner, spender, token_id, lower(timestamp_range));
