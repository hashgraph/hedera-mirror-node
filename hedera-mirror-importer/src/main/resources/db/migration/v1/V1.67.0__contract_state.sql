create table if not exists contract_state
(
  contract_id        bigint not null,
  created_timestamp  bigint not null,
  modified_timestamp bigint not null,
  slot               bytea  not null,
  value              bytea  null,
  primary key (contract_id, slot)
);


-- migrate contract_state from contract_state_changes
insert into contract_state (contract_id, created_timestamp, modified_timestamp, slot, value)
select contract_id, consensus_timestamp, consensus_timestamp, slot, value_read
from contract_state_change
where contract_state_change.migration is true and value_read is not null;