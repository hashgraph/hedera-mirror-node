-- Add parent_contract_id column to contract_log
alter table contract_log
    add column parent_contract_id bigint;

-- Backfill parent_contract_id based on index
update contract_log child
set parent_contract_id = parent.contract_id
from contract_log parent
where parent.consensus_timestamp = child.consensus_timestamp
  and parent.index = 0
  and child.index <> 0;

-- Create index on contract_id and timestamp for contract logs REST API
create index if not exists contract_log__contract_id_timestamp
    on contract_log (contract_id, consensus_timestamp desc, index);

-- Create index on parent_contract_id and timestamp for contract logs REST API
create index if not exists contract_log__parent_contract_id_timestamp
    on contract_log (parent_contract_id, consensus_timestamp desc, index);
