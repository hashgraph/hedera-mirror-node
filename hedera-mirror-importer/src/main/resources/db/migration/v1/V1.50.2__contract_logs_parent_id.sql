-- Add index for results retrieval by
alter table contract_log
    add column parent_contract_id bigint;
update contract_log child
set parent_contract_id = parent.contract_id
from contract_log parent
where parent.consensus_timestamp = child.consensus_timestamp
  and parent.index = 0
  and child.index <> 0;

