--- contract_result's contract_id should no longer be nullable
update contract_result set contract_id = 0 where contract_id is null;

alter table contract_result
    alter column contract_id set not null;
