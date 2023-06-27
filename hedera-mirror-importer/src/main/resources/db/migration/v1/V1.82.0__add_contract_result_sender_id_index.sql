-------------------
-- Add index to contract_result to improve rest api query performance
-------------------

create index if not exists contract_result__sender_timestamp
  on contract_result (sender_id, consensus_timestamp);
