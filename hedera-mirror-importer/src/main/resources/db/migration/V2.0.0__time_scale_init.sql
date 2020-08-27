-------------------
-- Init mirror node db, defining table schema and creating hyper tables
-- Supports mirror nodes migrated from v1.0
-- Use default of 604800000000000 ns (7 days) as chunk time interval
-- add TIMESTAMPTZ data type column to tables where no monotonically increasing id exists
-------------------

-- Extend the database with TimescaleDB
create extension if not exists timescaledb cascade;

-- domains
create domain hbar_tinybars as bigint;
create domain entity_num as integer;
create domain entity_realm_num as smallint;
create domain entity_type_id as character(1);
create domain hbar_tinybars as bigint;
--create domain nanos_timestamp as bigint; // drop this domain as it's needed as a bigint for hyper table partitioning

-- sequences
--create sequence if not exists s_record_files_seq;

-- account_balance
create table if not exists account_balances (
    consensus_timestamp bigint not null,
    account_id entity_id not null,
    balance hbar_tinybars not null,
    constraint pk__account_balances primary key (consensus_timestamp, account_id)
);
comment on table account_balances is 'account balances (historical) in tinybars at different consensus timestamps';
select create_hypertable('account_balance', 'consensus_timestamp', chunk_time_interval => 604800000000000, if_not_exists => true);
--create index if not exists balances__account_then_timestamp
--    on mirror_node.account_balances (account_realm_num desc, account_num desc, consensus_timestamp desc);

-- account_balance_sets
create table if not exists account_balance_sets (
    consensus_timestamp bigint not null,
    is_complete boolean not null default false,
    processing_start_timestamp timestamp without time zone null default (now() at time zone 'utc'),
    processing_end_timestamp timestamp without time zone null,
    constraint pk__account_balance_sets primary key (consensus_timestamp)
);
comment on table account_balance_sets is 'processing state of snapshots of the entire set of account balances at different consensus timestamps';
select create_hypertable('account_balance_sets', 'consensus_timestamp', chunk_time_interval => 604800000000000, if_not_exists => true);
--create index if not exists balance_sets__completed
--    on account_balance_sets (is_complete, consensus_timestamp desc);

-- address_book
create table if not exists address_book
(
    start_consensus_timestamp   nanos_timestamp primary key,
    end_consensus_timestamp     nanos_timestamp null,
    file_id                     entity_id       not null,
    node_count                  int             null,
    file_data                   bytea           not null
);
select create_hypertable('address_book', 'start_consensus_timestamp', chunk_time_interval => 604800000000000, if_not_exists => true);


-- address_book_entry
create table if not exists address_book_entry
(
    id                      serial          primary key,
    consensus_timestamp     bigint          references address_book (start_consensus_timestamp) not null,
    ip                      varchar(128)    null,
    port                    integer         null,
    memo                    varchar(128)    null,
    public_key              varchar(1024)   null,
    node_id                 bigint          null,
    node_account_id         entity_id       null,
    node_cert_hash          bytea           null
);
select create_hypertable('address_book_entry', 'consensus_timestamp', chunk_time_interval => 604800000000000, if_not_exists => true);
--create index if not exists address_book_entry__timestamp
--    on address_book_entry (consensus_timestamp);

-- contract_result
create table if not exists contract_result
(
    function_parameters     bytea   null,
    gas_supplied            bigint  null,
    call_result             bytea   null,
    gas_used                bigint  null,
    consensus_timestamp     bigint  not null
);
select create_hypertable('contract_result', 'consensus_timestamp', chunk_time_interval => 604800000000000, if_not_exists => true);
--create index if not exists contract_result__consensus
--    on t_contract_result (consensus_timestamp desc);

-- crypto_transfer
create table if not exists crypto_transfer
(
    entity_id               entity_id       not null,
    consensus_timestamp     bigint          not null,
    amount                  hbar_tinybars   not null
);

select create_hypertable('crypto_transfer', 'consensus_timestamp', chunk_time_interval => 604800000000000, if_not_exists => true);

--create index if not exists crypto_transfer__consensus_timestamp
--    on crypto_transfer (consensus_timestamp);
--create index if not exists crypto_transfer__entity_id_consensus_timestamp
--    on crypto_transfer (entity_id, consensus_timestamp)
--    where entity_id != 98; -- id corresponding to treasury address 0.0.98

-- file_data
select create_hypertable('file_data', 'consensus_timestamp', chunk_time_interval => 604800000000000, if_not_exists => true);
--create index if not exists file_data__consensus
--    on t_file_data (consensus_timestamp desc);

-- flyway_schema_history


-- live_hash
select create_hypertable('live_hash', 'consensus_timestamp', chunk_time_interval => 604800000000000, if_not_exists => true);
--create index if not exists livehashes__consensus
--    on t_livehashes (consensus_timestamp desc);

-- non_fee_transfer
select create_hypertable('non_fee_transfer', 'consensus_timestamp', chunk_time_interval => 604800000000000, if_not_exists => true);
--create index if not exists non_fee_transfer__consensus_timestamp
--    on non_fee_transfer (consensus_timestamp);

-- record_file
-- id seq from v1.0 no longer explicitly created as s_record_files_seq
select create_hypertable('record_file', 'id', chunk_time_interval => 1000, if_not_exists => true);
--create unique index if not exists record_file_name ON t_record_files (name);
--create unique index if not exists record_file_hash ON t_record_files (file_hash);
--create index if not exists record_file__consensus_end on record_file (consensus_end);
--create index if not exists record_file__prev_hash on record_file (prev_hash);

-- t_application_status


-- t_entities

select create_hypertable('t_entities', 'id', chunk_time_interval => 1000000, if_not_exists => true);
--create index if not exists entities__ed25519_public_key_hex_natural_id
--    on t_entities (ed25519_public_key_hex, fk_entity_type_id, entity_shard, entity_realm, entity_num);
--create unique index if not exists t_entities_unq on t_entities (entity_shard, entity_realm, entity_num);

-- t_entity_types


-- t_transaction_results


-- t_transaction_types


-- topic_message
create table if not exists topic_message
(
    consensus_timestamp bigint              primary key not null,
    realm_num           entity_realm_num    not null,
    topic_num           entity_num          not null,
    message             bytea               not null,
    running_hash        bytea               not null,
    sequence_number     bigint              not null
);

select create_hypertable('topic_message', 'consensus_timestamp', chunk_time_interval => 604800000000000, if_not_exists => true);
--create index if not exists topic_message__realm_num_timestamp
--    on topic_message (realm_num, topic_num, consensus_timestamp);
--create unique index if not exists topic_message__topic_num_realm_num_seqnum
--    on topic_message (realm_num, topic_num, sequence_number);

-- transaction

select create_hypertable('transaction', 'consensus_ns', chunk_time_interval => 604800000000000, if_not_exists => true);
--create index transaction__transaction_id
--    on transaction (valid_start_ns, payer_account_id);
--create index transaction__payer_account_id
--    on transaction (payer_account_id);

-- is it necessary to explicitly grant the following?
--grant usage on SCHEMA :schema_name to :db_user;
--grant connect on database :db_name to :db_user;
--grant all privileges on database :db_name to db_user;
--grant all privileges on all tables in :schema_name public to db_user;
--grant all ON t_record_files to db_user;
