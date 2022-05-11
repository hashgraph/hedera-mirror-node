alter table if exists record_file
    add column if not exists gas_used bigint null,
    add column if not exists logs_bloom bytea null;
