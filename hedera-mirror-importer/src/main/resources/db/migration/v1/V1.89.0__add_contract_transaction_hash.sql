create table if not exists contract_transaction_hash
(
    consensus_timestamp bigint not null,
    hash                bytea  not null,
    payer_account_id    bigint not null,
    entity_id           bigint not null
);
comment on table contract_transaction_hash is 'First 32 bytes of network transaction hash (or ethereum hash) to transaction details mapping';

insert into contract_transaction_hash(consensus_timestamp,hash,payer_account_id,entity_id)
    (select distinct on (cr.transaction_hash) cr.consensus_timestamp, cr.transaction_hash, cr.payer_account_id, cr.contract_id
     from contract_result cr
     order by cr.transaction_hash, cr.consensus_timestamp asc);

create index if not exists contract_transaction_hash__hash
    on contract_transaction_hash using hash (hash);

drop index if exists ethereum_transaction__hash;
drop index if exists contract_result__hash;