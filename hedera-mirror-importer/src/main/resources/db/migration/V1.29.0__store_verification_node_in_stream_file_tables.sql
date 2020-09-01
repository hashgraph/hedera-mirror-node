-- add node_account_id to record_file table
alter table if exists record_file
    add column node_account_id entity_id not null default 3;
alter table if exists record_file
    alter column node_account_id drop default;

-- account_balance_file table
create table if not exists account_balance_file (
    name                varchar(250) primary key,
    consensus_timestamp nanos_timestamp not null,
    load_start          bigint,
    load_end            bigint,
    file_hash           varchar(96),
    node_account_id     entity_id not null
);
