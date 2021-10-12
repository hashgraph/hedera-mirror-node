-- Fills missing max fee, transaction hash, and valid duration seconds for mainnet for the period from OA to 12/05/2019

begin;

create temporary table transaction_temp (
    consensus_ns           bigint,
    max_fee                bigint,
    transaction_hash       bytea,
    valid_duration_seconds bigint
) on commit drop;

\copy transaction_temp from 'transaction.csv' with csv;

update transaction
set
    max_fee = transaction_temp.max_fee,
    transaction_hash = transaction_temp.transaction_hash,
    valid_duration_seconds = transaction_temp.valid_duration_seconds
from transaction_temp
where transaction.consensus_ns = transaction_temp.consensus_ns and transaction.transaction_hash is null;

commit;
