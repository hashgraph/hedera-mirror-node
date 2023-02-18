-- copy transaction_hash and transaction_index from contract_result to contract_log
alter table if exists contract_log
    add column if not exists transaction_hash   bytea    null,
    add column if not exists transaction_index  integer  null;

with results as (
    select cr.consensus_timestamp, cr.payer_account_id, cr.transaction_hash, cr.transaction_index
    from contract_result cr
    left join contract_log cl on cl.consensus_timestamp = cr.consensus_timestamp
    and cl.payer_account_id = cr.payer_account_id
)
update contract_log
set transaction_hash   = r.transaction_hash,
    transaction_index  = r.transaction_index
from results r
where r.consensus_timestamp = contract_log.consensus_timestamp
and r.payer_account_id = contract_log.payer_account_id;
