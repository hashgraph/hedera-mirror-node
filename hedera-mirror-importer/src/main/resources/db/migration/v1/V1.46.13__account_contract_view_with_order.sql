-- add two account_contract views, with order by id asc and desc

create or replace view account_contract_asc as
(
  select
    auto_renew_period,
    created_timestamp,
    deleted,
    expiration_timestamp,
    id,
    key,
    max_automatic_token_associations,
    memo,
    num,
    public_key,
    proxy_account_id,
    realm,
    shard,
    timestamp_range,
    type,
    receiver_sig_required
  from entity
  where type = 1
  order by id asc
)
union all
(
  select auto_renew_period,
    created_timestamp,
    deleted,
    expiration_timestamp,
    id,
    key,
    null as max_automatic_token_associations,
    memo,
    num,
    public_key,
    proxy_account_id,
    realm,
    shard,
    timestamp_range,
    type,
    null as receiver_sig_required
  from contract
  order by id asc
);

create or replace view account_contract_desc as
(
  select
    auto_renew_period,
    created_timestamp,
    deleted,
    expiration_timestamp,
    id,
    key,
    max_automatic_token_associations,
    memo,
    num,
    public_key,
    proxy_account_id,
    realm,
    shard,
    timestamp_range,
    type,
    receiver_sig_required
  from entity
  where type = 1
  order by id desc
)
union all
(
  select auto_renew_period,
    created_timestamp,
    deleted,
    expiration_timestamp,
    id,
    key,
    null as max_automatic_token_associations,
    memo,
    num,
    public_key,
    proxy_account_id,
    realm,
    shard,
    timestamp_range,
    type,
    null as receiver_sig_required
  from contract
  order by id desc
);
