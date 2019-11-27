---
--- Replace t_cryptotransferlists.account_id with realm_num and entity_num
---

-- drop previous indexes
drop index if exists
    idx__t_cryptotransferlists__account_and_consensus;

drop index if exists
    idx__t_cryptotransferlists__consensus_and_account;

-- add realm_num and entity_num
alter table t_cryptotransferlists
    add column if not exists realm_num entity_realm_num null;

alter table t_cryptotransferlists
    add column if not exists entity_num entity_num null;

-- populate realm_num and entity_num from t_entities table
with dupe_entities as (
        select id, entity_realm, entity_num from t_entities
)
update t_cryptotransferlists c set realm_num = de.entity_realm, entity_num = de.entity_num from dupe_entities de where c.account_id = de.id;

alter table t_cryptotransferlists
    alter column realm_num set not null;

alter table t_cryptotransferlists
    alter column entity_num set not null;

-- add additional indexes
create index if not exists idx__t_cryptotransferlists__consensus_and_realm_and_num
    on t_cryptotransferlists (consensus_timestamp, realm_num, entity_num);

create index if not exists idx__t_cryptotransferlists__ts_then_acct
    on t_cryptotransferlists (consensus_timestamp, realm_num, entity_num);

-- drop constraint and acount_id
alter table t_cryptotransferlists
   drop constraint if exists fk_ctl_account_id;

alter table t_cryptotransferlists
    drop column if exists account_id;
