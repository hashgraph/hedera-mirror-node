-- Add index for state retrieval by
create index if not exists contract_state_index
    on contract_state_change (contract_id, slot, consensus_timestamp);
