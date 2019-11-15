---
--- Replace t_cryptotransferlists.account_id with account_realm_num, and account_num
---
alter table t_cryptotransferlists
    add column if not exists account_realm_num bigint null;

alter table t_cryptotransferlists
    add column if not exists account_num bigint null;

with dupe_entities as (
        select id, entity_realm, entity_num FROM t_entities
)
update t_cryptotransferlists c SET account_realm_num = de.entity_realm, account_num = de.entity_num FROM dupe_entities de WHERE c.account_id = de.id;

create index if not exists idx_t_cryptotransferlists_unq ON t_cryptotransferlists (account_realm_num, account_num, consensus_timestamp desc);
