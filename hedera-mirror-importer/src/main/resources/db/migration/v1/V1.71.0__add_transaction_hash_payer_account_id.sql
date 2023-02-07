alter table if exists transaction_hash add column if not exists payer_account_id bigint;

update transaction_hash th
set payer_account_id = t.payer_account_id
from transaction t
where th.consensus_timestamp = t.consensus_timestamp;

alter table if exists transaction_hash alter column payer_account_id set not null;
