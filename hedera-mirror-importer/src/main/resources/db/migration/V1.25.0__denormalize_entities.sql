create domain entity_id as bigint;

create function encodeEntityId(shard bigint, realm bigint, num bigint)
returns entity_id as $$
begin
    -- Encoding: 16 bits for shard, followed by 16 bits for realm, followed by 32 bits for num
    return (num & 4294967295) | ((realm & 65535) << 32) | ((shard & 65535) << 48);
end
$$ language plpgsql;

-------------------
-- t_entities table
-------------------

alter table if exists t_entities
    add column if not exists encoded_id entity_id null,
    add column if not exists proxy_account_id entity_id null;

-- Compute encoded ids once
update t_entities e
set encoded_id = encodeEntityId(entity_shard, entity_realm, entity_num);

alter table if exists t_entities
    alter column encoded_id set not null;

-- Migrate auto renew accounts and proxy accounts
update t_entities e
set auto_renew_account_id = e2.encoded_id
from t_entities e2
where e.auto_renew_account_id = e2.id
  and e.auto_renew_account_id is not null;

update t_entities e
set proxy_account_id = e2.encoded_id
from t_entities e2
where e.fk_prox_acc_id = e2.id
  and e.fk_prox_acc_id is not null;

-------------------
-- t_transactions table
-------------------

alter table if exists t_transactions
    add column if not exists node_account_id entity_id null,
    add column if not exists payer_account_id entity_id null,
    add column if not exists entity_id entity_id null;

-- Migrate data

update t_transactions t
set payer_account_id = payer.encoded_id,
    node_account_id = node.encoded_id
from t_transactions t2
    join t_entities payer on t2.fk_payer_acc_id = payer.id
    join t_entities node on t2.fk_node_acc_id = node.id
where t.consensus_ns = t2.consensus_ns;

update t_transactions
set entity_id = e.encoded_id
from t_entities e
where t_transactions.fk_cud_entity_id = e.id
  and t_transactions.fk_cud_entity_id is not null;

-- Drop indexes and columns

drop index if exists idx__t_transactions__transaction_id;
drop index if exists idx_t_transactions_node_account;
drop index if exists idx_t_transactions_payer_id;

alter table if exists t_transactions
    drop column if exists fk_node_acc_id,
    drop column if exists fk_payer_acc_id,
    drop column if exists fk_cud_entity_id;

-- Add column constraints and create indexes

alter table if exists t_transactions
    alter column node_account_id set not null,
    alter column payer_account_id set not null;

create index if not exists t_transactions__transaction_id
    on t_transactions (valid_start_ns, payer_account_id);
create index if not exists t_transactions__node_account_id
    on t_transactions (node_account_id);
create index if not exists t_transactions__payer_account_id
    on t_transactions (payer_account_id);

-------------------
-- t_entities table
-------------------

-- Drop indexes, columns and sequence

alter table if exists t_entities drop constraint t_entities_pkey;
drop index if exists idx_t_entities_id_num_id; -- useless index

alter table if exists t_entities
    drop column if exists id,
    drop column if exists fk_prox_acc_id;

drop sequence if exists s_entities_seq;

alter table if exists t_entities
    rename column encoded_id to id;

alter table if exists t_entities
    add primary key (id);
