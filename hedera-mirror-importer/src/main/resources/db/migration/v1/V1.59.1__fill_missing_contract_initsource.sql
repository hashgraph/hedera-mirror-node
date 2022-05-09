-------------------
-- Fill missing contract file_id / initcode from its parent contract
-------------------

with missing as (
  select id, created_timestamp, parent_consensus_timestamp
  from contract
  join transaction
    on consensus_timestamp = created_timestamp
  where contract.created_timestamp is not null and file_id is null and initcode is null
), contract_initsource as (
  select m.id, c.file_id, c.initcode
  from missing m
  join contract_result cr
    on cr.consensus_timestamp = coalesce(parent_consensus_timestamp, created_timestamp) and cr.contract_id <> m.id
  join contract c
    on c.id = cr.contract_id
  where c.file_id is not null or c.initcode is not null
), updated as (
  update contract
  set file_id = ci.file_id, initcode = ci.initcode
  from contract_initsource ci
  where contract.id = ci.id
  returning contract.id, contract.file_id, contract.initcode
)
update contract_history
set file_id = updated.file_id, initcode = updated.initcode
from updated
where contract_history.id = updated.id
