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

with min_timestamp as (select min(timestamp) as created_timestamp, owner, spender
                       from (select lower(timestamp_range) as timestamp, owner, spender
                             from crypto_allowance
                             union all
                             select lower(timestamp_range) as timestamp, owner, spender
                             from crypto_allowance_history) ca
                       group by owner, spender),
     update_history as (
         update crypto_allowance_history cah
             set amount = cah.amount_granted, created_timestamp = mt.created_timestamp
             from min_timestamp mt
             where cah.owner = mt.owner and cah.spender = mt.spender)
update crypto_allowance ca
set amount            = ca.amount_granted,
    created_timestamp = mt.created_timestamp
from min_timestamp mt
where ca.owner = mt.owner
  and ca.spender = mt.spender;

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

with min_timestamp as (select min(timestamp) as created_timestamp, token_id, owner, spender
                       from (select lower(timestamp_range) as timestamp, token_id, owner, spender
                             from token_allowance
                             union all
                             select lower(timestamp_range) as timestamp, token_id, owner, spender
                             from token_allowance_history) ta
                       group by token_id, owner, spender),
     update_history as (
         update token_allowance_history tah
             set amount = tah.amount_granted, created_timestamp = mt.created_timestamp
             from min_timestamp mt
             where tah.token_id = mt.token_id and tah.owner = mt.owner and tah.spender = mt.spender)
update token_allowance ta
set amount            = ta.amount_granted,
    created_timestamp = mt.created_timestamp
from min_timestamp mt
where ta.token_id = mt.token_id
  and ta.owner = mt.owner
  and ta.spender = mt.spender;

with spent_allowance as (select coalesce(sum(ct.amount), 0) as amount, a.owner, a.spender
                         from crypto_transfer ct,
                              crypto_allowance a
                         where ct.is_approval = true
                           and a.amount_granted > 0
                           and a.owner = ct.entity_id
                           and a.spender = ct.payer_account_id
                           and ct.consensus_timestamp >= lower(a.timestamp_range)
                         group by a.owner, a.spender)
select a.amount + ca.amount_granted as amount_remaining, ca.*
from spent_allowance a,
     crypto_allowance ca
where a.owner = ca.owner
  and a.spender = ca.spender;
