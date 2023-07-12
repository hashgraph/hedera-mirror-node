-------------------
-- HIP-336 Track remaining crypto and fungible token allowances
-------------------

-- update crypto_allowance
alter table if exists crypto_allowance
    rename column amount to amount_granted;
alter table if exists crypto_allowance
    add column if not exists amount            bigint not null default 0,
    add column if not exists created_timestamp bigint not null default 0;

-- update crypto_allowance_history
alter table if exists crypto_allowance_history
    rename column amount to amount_granted;
alter table if exists crypto_allowance_history
    add column if not exists amount            bigint not null default 0,
    add column if not exists created_timestamp bigint not null default 0;

update crypto_allowance
set amount = amount_granted,  -- add all crypto_transfer for PK where is_approval==true
created_timestamp = lower(timestamp_range);  -- Is lower() equivalent to "min" in the issue?

update crypto_allowance_history
set amount = amount_granted,
created_timestamp = lower(timestamp_range);

-- update token_allowance
alter table if exists token_allowance
    rename column amount to amount_granted;
alter table if exists token_allowance
    add column if not exists amount            bigint not null default 0,
    add column if not exists created_timestamp bigint not null default 0;

-- update token_allowance_history
alter table if exists token_allowance_history
    rename column amount to amount_granted;
alter table if exists token_allowance_history
    add column if not exists amount            bigint not null default 0,
    add column if not exists created_timestamp bigint not null default 0;

update token_allowance
set amount = amount_granted,
created_timestamp = lower(timestamp_range);

update token_allowance_history
set amount = amount_granted,
created_timestamp = lower(timestamp_range);