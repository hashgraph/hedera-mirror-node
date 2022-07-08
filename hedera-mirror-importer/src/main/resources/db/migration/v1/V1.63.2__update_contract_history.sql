alter table if exists contract_history
    add column if not exists runtime_bytecode bytea null;


