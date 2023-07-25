-------------------
-- HIP-336 Track remaining crypto and fungible token allowances
-------------------

-- Alter crypto_allowance
alter table if exists crypto_allowance
    rename column amount to amount_granted;
alter table if exists crypto_allowance
    add column if not exists amount            bigint not null default 0,
    add column if not exists created_timestamp bigint not null default 0;

-- Alter crypto_allowance_history
alter table if exists crypto_allowance_history
    rename column amount to amount_granted;
alter table if exists crypto_allowance_history
    add column if not exists amount            bigint not null default 0,
    add column if not exists created_timestamp bigint not null default 0;

-- Backfill crypto_allowance.created_timestamp
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
set amount = ca.amount_granted, created_timestamp = mt.created_timestamp
from min_timestamp mt
where ca.owner = mt.owner
  and ca.spender = mt.spender;

-- Backfill crypto_allowance.amount
with spent_allowance as (select coalesce(sum(ct.amount), 0) as amount, ca.owner, ca.spender
                         from crypto_transfer ct,
                              crypto_allowance ca
                         where ca.amount_granted > 0
                           and ct.is_approval = true
                           and ct.amount < 0
                           and ct.entity_id = ca.owner
                           and ct.payer_account_id = ca.spender
                           and ct.consensus_timestamp >= lower(ca.timestamp_range)
                         group by ca.owner, ca.spender)
update crypto_allowance ca
set amount = ca.amount_granted + a.amount
from spent_allowance a
where a.owner = ca.owner
  and a.spender = ca.spender;

-- Alter token_allowance
alter table if exists token_allowance
    rename column amount to amount_granted;
alter table if exists token_allowance
    add column if not exists amount            bigint not null default 0,
    add column if not exists created_timestamp bigint not null default 0;

-- Alter token_allowance_history
alter table if exists token_allowance_history
    rename column amount to amount_granted;
alter table if exists token_allowance_history
    add column if not exists amount            bigint not null default 0,
    add column if not exists created_timestamp bigint not null default 0;

-- Backfill token_allowance.created_timestamp
with min_timestamp as (select min(timestamp) as created_timestamp, owner, spender, token_id
                       from (select lower(timestamp_range) as timestamp, owner, spender, token_id
                             from token_allowance
                             union all
                             select lower(timestamp_range) as timestamp, owner, spender, token_id
                             from token_allowance_history) ta
                       group by owner, spender, token_id),
     update_history as (
         update token_allowance_history tah
             set amount = tah.amount_granted, created_timestamp = mt.created_timestamp
             from min_timestamp mt
             where tah.owner = mt.owner and tah.spender = mt.spender and tah.token_id = mt.token_id)
update token_allowance ta
set amount = ta.amount_granted, created_timestamp = mt.created_timestamp
from min_timestamp mt
where ta.owner = mt.owner
  and ta.spender = mt.spender
  and ta.token_id = mt.token_id;

-- Backfill token_allowance.amount
with spent_allowance as (select coalesce(sum(tt.amount), 0) as amount, ta.owner, ta.spender, ta.token_id
                         from token_transfer tt,
                              token_allowance ta
                         where ta.amount_granted > 0
                           and tt.is_approval = true
                           and tt.amount < 0
                           and tt.account_id = ta.owner
                           and tt.payer_account_id = ta.spender
                           and tt.token_id = ta.token_id
                           and tt.consensus_timestamp >= lower(ta.timestamp_range)
                         group by ta.owner, ta.spender, ta.token_id)
update token_allowance ta
set amount = ta.amount_granted + a.amount
from spent_allowance a
where ta.owner = a.owner
  and ta.spender = a.spender
  and ta.token_id = a.token_id;
