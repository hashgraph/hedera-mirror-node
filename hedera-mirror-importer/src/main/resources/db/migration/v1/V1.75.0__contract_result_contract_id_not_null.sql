-- Update contract_result.contract_id: make it 0 if null; don't allow future nulls.
update contract_result set contract_id = 0 where contract_id is null;
alter table contract_result alter column contract_id set not null;
