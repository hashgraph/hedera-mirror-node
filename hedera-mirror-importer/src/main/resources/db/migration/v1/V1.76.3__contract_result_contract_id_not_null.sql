--- contract_action
update contract_result cr
  set contract_id = 0 where contract_id is null;

alter table if exists contract_result
  alter column contract_id set not null default 0;
