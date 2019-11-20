---
--- Replace t_cryptotransferlists.account_id with account_realm_num, and account_num
---

-- drop previous indexes
drop index if exists
    idx__t_cryptotransferlists__account_and_consensus;

drop index if exists
    idx__t_cryptotransferlists__consensus_and_account;

-- add account_realm_num, and account_num
alter table t_cryptotransferlists
    add column if not exists account_realm_num entity_realm_num null;

alter table t_cryptotransferlists
    add column if not exists account_num entity_num null;

-- populate account_realm_num, and account_num from t_entities table
with dupe_entities as (
        select id, entity_realm, entity_num from t_entities
)
update t_cryptotransferlists c set account_realm_num = de.entity_realm, account_num = de.entity_num from dupe_entities de where c.account_id = de.id;

alter table t_cryptotransferlists
    alter column account_realm_num set not null;

alter table t_cryptotransferlists
    alter column account_num set not null;

-- add additional indexes
create index if not exists idx__t_cryptotransferlists__consensus_and_realm_and_num
    on t_cryptotransferlists (consensus_timestamp, account_realm_num, account_num);

create index if not exists idx__t_cryptotransferlists__ts_then_acct
    on t_cryptotransferlists (consensus_timestamp, account_realm_num, account_num);

-- drop constraint and acount_id
alter table t_cryptotransferlists
   drop constraint if exists fk_ctl_account_id;

alter table t_cryptotransferlists
    drop column if exists account_id;
