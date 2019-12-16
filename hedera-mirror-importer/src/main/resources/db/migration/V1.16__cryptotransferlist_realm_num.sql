---
--- Replace t_cryptotransferlists.account_id with realm_num and entity_num
---

-- drop previous indexes
drop index if exists
    idx__t_cryptotransferlists__account_and_consensus;

drop index if exists
    idx__t_cryptotransferlists__consensus_and_account;

-- populate realm_num and entity_num from t_entities table
-- instead of in place update insert into a new table without index overhead for optimal performance
create table t_cryptotransferlists_migrate
(
    consensus_timestamp nanos_timestamp not null,
    realm_num           entity_num      not null,
    entity_num          entity_num      not null,
    amount              hbar_tinybars   not null
);

insert into t_cryptotransferlists_migrate (consensus_timestamp, realm_num, entity_num, amount)
select ctl.consensus_timestamp, ent.entity_realm, ent.entity_num, ctl.amount
from t_cryptotransferlists ctl
         join t_entities ent
              on ctl.account_id = ent.id;

-- swap tables
drop table t_cryptotransferlists;
alter table t_cryptotransferlists_migrate
    rename to t_cryptotransferlists;

    -- add indexes
create index if not exists idx__t_cryptotransferlist_amount ON t_cryptotransferlists (amount);

create index if not exists idx__t_cryptotransferlists__ts_then_acct
    on t_cryptotransferlists (consensus_timestamp, entity_num);

create index if not exists idx__t_cryptotransferlists__consensus_and_realm_and_num
    on t_cryptotransferlists (consensus_timestamp, realm_num, entity_num);

-- add foreign key
alter table t_cryptotransferlists
    add constraint fk__t_transactions foreign key (consensus_timestamp) references t_transactions (consensus_ns);
