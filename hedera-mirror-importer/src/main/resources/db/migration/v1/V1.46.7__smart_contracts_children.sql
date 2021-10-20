-- Fixes up the smart contract tables after normalizing the contract result raw protobuf into discrete columns

alter table if exists contract
    add column if not exists parent_id bigint null;

alter table if exists contract_history
    add column if not exists parent_id bigint null;

-- Update the contract_result.initial_balance from the contract create transaction.initial_balance
with contract_transaction as (
    select consensus_ns, initial_balance
    from transaction
    where type = 8
)
update contract_result cr
set amount = ct.initial_balance
from contract_transaction ct
where cr.consensus_timestamp = ct.consensus_ns;

-- Enumerate the list of created child contract IDs
create temporary table if not exists contract_relationship on commit drop as
select consensus_timestamp, contract_id as parent_contract_id, created_contract_ids[i] as child_contract_id
from (
         select consensus_timestamp,
                contract_id,
                created_contract_ids,
                generate_subscripts(created_contract_ids, 1) as i
         from contract_result
         where array_length(created_contract_ids, 1) > 0
     ) children;

-- Move child contract IDs still marked as accounts in entity table to the contract table
with deleted_entity as (
    delete from entity e using contract_relationship cr where e.id = cr.child_contract_id returning *
)
insert
into contract (auto_renew_period,
               created_timestamp,
               deleted,
               expiration_timestamp,
               id,
               key,
               memo,
               num,
               proxy_account_id,
               public_key,
               realm,
               shard,
               timestamp_range,
               type)
select auto_renew_period,
       created_timestamp,
       deleted,
       expiration_timestamp,
       id,
       key,
       memo,
       num,
       proxy_account_id,
       public_key,
       realm,
       shard,
       timestamp_range,
       2
from deleted_entity;

-- Upsert the child contract. If the child ID doesn't exist copy the fields from the parent. If it does, update using
-- the merged parent and child fields.
with parent_contract as (
    select *
    from contract c,
         contract_relationship cr
    where c.id = cr.parent_contract_id
),
     child_contract as (
         select *
         from contract c,
              contract_relationship cr
         where c.id = cr.child_contract_id
     )
insert
into contract (auto_renew_period,
               created_timestamp,
               deleted,
               expiration_timestamp,
               id,
               key,
               memo,
               num,
               parent_id,
               proxy_account_id,
               public_key,
               realm,
               shard,
               timestamp_range,
               type)
select coalesce(child.auto_renew_period, parent.auto_renew_period),
       coalesce(child.created_timestamp, child.consensus_timestamp, parent.created_timestamp),
       coalesce(child.deleted, parent.deleted),
       coalesce(child.expiration_timestamp, parent.expiration_timestamp),
       child.child_contract_id,
       coalesce(child.key, parent.key),
       coalesce(child.memo, parent.memo, ''),
       child.child_contract_id & 4294967295,
       parent.id,
       coalesce(child.proxy_account_id, parent.proxy_account_id),
       coalesce(child.public_key, parent.public_key),
       coalesce(child.realm, parent.realm, 0),
       coalesce(child.shard, parent.shard, 0),
       coalesce(child.timestamp_range, int8range(child.consensus_timestamp, null), parent.timestamp_range, '[0,)'),
       parent.type
from child_contract child
         left join parent_contract parent on parent.id = child.id
on conflict (id)
    do update set auto_renew_period    = excluded.auto_renew_period,
                  created_timestamp    = excluded.created_timestamp,
                  deleted              = excluded.deleted,
                  expiration_timestamp = excluded.expiration_timestamp,
                  key                  = excluded.key,
                  memo                 = excluded.memo,
                  num                  = excluded.num,
                  parent_id            = excluded.parent_id,
                  proxy_account_id     = excluded.proxy_account_id,
                  public_key           = excluded.public_key,
                  realm                = excluded.realm,
                  shard                = excluded.shard,
                  timestamp_range      = excluded.timestamp_range;

alter table if exists contract_result
    alter column bloom set not null,
    alter column call_result set not null,
    alter column created_contract_ids set default '{}',
    alter column created_contract_ids set not null,
    alter column error_message set default '',
    alter column error_message set not null;

alter table if exists contract_log
    alter column contract_id drop not null;
