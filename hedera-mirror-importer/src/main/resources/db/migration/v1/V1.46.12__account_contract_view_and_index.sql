-- create a view for accounts and contracts
create or replace view account_contract as
  select
    id,
    expiration_timestamp,
    auto_renew_period,
    key,
    deleted,
    type,
    public_key,
    max_automatic_token_associations,
    memo,
    receiver_sig_required
  from entity
  where type = 1
  union all
  select
    id,
    expiration_timestamp,
    auto_renew_period,
    key,
    deleted,
    type,
    public_key,
    null as max_automatic_token_associations,
    memo,
    null as receiver_sig_required
  from contract;

-- add the public key index for contract table
create index if not exists contract__public_key
    on contract (public_key) where public_key is not null;
