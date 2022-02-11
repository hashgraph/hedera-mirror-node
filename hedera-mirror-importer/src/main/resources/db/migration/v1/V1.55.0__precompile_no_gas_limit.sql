-------------------
-- Update schema to support no gas limit on precompile results
-------------------

alter table contract_result
    alter column gas_limit drop not null;
