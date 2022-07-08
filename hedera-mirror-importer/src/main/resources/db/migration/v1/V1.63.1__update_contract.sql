alter table if exists contract
    add column if not exists runtime_bytecode bytea null;


