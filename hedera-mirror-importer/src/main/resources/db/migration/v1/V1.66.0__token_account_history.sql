
alter table if exists token_account
    add column if not exists timestamp_range int8range;

update token_account
    set timestamp_range = int8range(coalesce(modified_timestamp, created_timestamp, 0), null);

alter table if exists token_account
    alter column timestamp_range set not null,
    drop constraint if exists token_account_pkey,
    drop column if exists modified_timestamp,
    add primary key (account_id, token_id);

create table if not exists token_account_history
(
    like token_account,
    primary key (account_id, token_id, timestamp_range)
);
comment on table token_account_history is 'History of token_account';

create index if not exists token_account_history__timestamp_range on token_account_history using gist (timestamp_range);
