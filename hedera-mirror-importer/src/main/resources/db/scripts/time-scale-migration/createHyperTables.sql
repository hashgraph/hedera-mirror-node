-------------------
-- alter tables by removing domains
-- update to custom schema
-- Create hyper tables from v1 schema, inserts from old table, add indexes
-- Use default of 604800000000000 ns (7 days) as chunk time interval
-------------------

\set chunkIdInterval 10000
\set chunkTimeInterval 604800000000000
\set newSchema mirrornode

-- alter schemas by replacing domains
alter table account_balance
    alter column account_id type bigint,
    alter column balance type bigint,
    alter column consensus_timestamp type bigint;

-- account_balance_file
alter table account_balance_file
    alter column node_account_id type bigint;

-- account_balance_sets
alter table account_balance_sets
    alter column consensus_timestamp type bigint;

-- address_book
alter table address_book
    alter column start_consensus_timestamp type bigint,
    alter column end_consensus_timestamp type bigint,
    alter column file_id type bigint;

-- address_book_entry
alter table address_book_entry
    alter column consensus_timestamp type bigint,
    alter column consensus_timestamp set not null,
    alter column node_account_id type bigint;

-- contract_result
alter table contract_result
    alter column consensus_timestamp type bigint;

-- crypto_transfer
alter table crypto_transfer
    alter column entity_id type bigint,
    alter column consensus_timestamp type bigint,
    alter column amount type bigint;

-- file_data
alter table file_data
    alter column consensus_timestamp type bigint,
    alter column entity_id type bigint;

-- live_hash
alter table live_hash
    alter column consensus_timestamp type bigint;

-- non_fee_transfer
alter table non_fee_transfer
    alter column entity_id type bigint,
    alter column consensus_timestamp type bigint,
    alter column amount type bigint;

-- record_file
alter table record_file
    alter column node_account_id type bigint;

-- t_entities
alter table t_entities
    alter column id type bigint,
    alter column proxy_account_id type bigint;

-- token
alter table token
    alter column token_id type bigint,
    alter column treasury_account_id type bigint;

-- token_account
alter table token_account
    alter column account_id type bigint,
    alter column token_id type bigint;

-- token_balance
alter table token_balance
    alter column account_id type bigint,
    alter column token_id type bigint;

-- token_transfer
alter table token_transfer
    alter column token_id type bigint,
    alter column account_id type bigint,
    alter column amount type bigint;

-- topic_message
alter table topic_message
    alter column consensus_timestamp type bigint;

-- transaction
alter table transaction
    alter column payer_account_id type bigint,
    alter column node_account_id type bigint,
    alter column entity_id type bigint,
    alter column max_fee type bigint;


-- Update schema from public to mirror_node
-- account_balance
alter table account_balance
    set schema :newSchema;

-- account_balance_file
alter table account_balance_file
    set schema :newSchema;

-- account_balance_sets
alter table account_balance_sets
    set schema :newSchema;

-- address_book
alter sequence address_book_entry_id_seq
    set schema :newSchema;
alter table address_book
    set schema :newSchema;

-- address_book_entry
alter table address_book_entry
    set schema :newSchema;

-- contract_result
alter table contract_result
    set schema :newSchema;

-- crypto_transfer
alter table crypto_transfer
    set schema :newSchema;

-- file_data
alter table file_data
    set schema :newSchema;

-- live_hash
alter table live_hash
    set schema :newSchema;

-- non_fee_transfer
alter table non_fee_transfer
    set schema :newSchema;

-- record_file
alter sequence s_record_files_seq
    set schema :newSchema;
alter table record_file
    set schema :newSchema;

-- t_application_status
alter table t_application_status
    set schema :newSchema;

-- t_entities
alter table t_entities
    set schema :newSchema;

-- t_entity_types
alter table t_entity_types
    set schema :newSchema;

-- t_transaction_results
alter table t_transaction_results
    set schema :newSchema;

-- t_transaction_types
alter table t_transaction_types
    set schema :newSchema;

-- token
alter table token
    set schema :newSchema;

-- token_account
alter table token_account
    set schema :newSchema;

-- token_balance
alter table token_balance
    set schema :newSchema;

-- token_transfer
alter table token_transfer
    set schema :newSchema;

-- topic_message
alter table topic_message
    set schema :newSchema;

-- transaction
alter table transaction
    set schema :newSchema;


-- create hyper tables
-- account_balance
select create_hypertable('account_balance', 'consensus_timestamp',
                         chunk_time_interval => :chunkTimeInterval, if_not_exists => true);

-- account_balance_file
select create_hypertable('account_balance_file', 'consensus_timestamp',
                         chunk_time_interval => :chunkTimeInterval, if_not_exists => true);

-- account_balance_sets
select create_hypertable('account_balance_sets', 'consensus_timestamp',
                         chunk_time_interval => :chunkTimeInterval, if_not_exists => true);

-- address_book
select create_hypertable('address_book', 'start_consensus_timestamp',
                         chunk_time_interval => :chunkTimeInterval, if_not_exists => true);

-- address_book_entry
select create_hypertable('address_book_entry', 'consensus_timestamp',
                         chunk_time_interval => :chunkTimeInterval, if_not_exists => true);

-- contract_result
select create_hypertable('contract_result', 'consensus_timestamp',
                         chunk_time_interval => :chunkTimeInterval, if_not_exists => true);

-- crypto_transfer
select create_hypertable('crypto_transfer', 'consensus_timestamp',
                         chunk_time_interval => :chunkTimeInterval, if_not_exists => true);

-- file_data
select create_hypertable('file_data', 'consensus_timestamp',
                         chunk_time_interval => :chunkTimeInterval, if_not_exists => true);

-- live_hash
select create_hypertable('live_hash', 'consensus_timestamp',
                         chunk_time_interval => :chunkTimeInterval, if_not_exists => true);

-- non_fee_transfer
select create_hypertable('non_fee_transfer', 'consensus_timestamp',
                         chunk_time_interval => :chunkTimeInterval, if_not_exists => true);

-- record_file
select create_hypertable('record_file', 'consensus_start',
                         chunk_time_interval => :chunkTimeInterval, if_not_exists => true);

-- t_application_status hyper table creation skipped as it serves only as a reference table

-- t_entities
select create_hypertable('t_entities', 'id',
                         chunk_time_interval => :chunkIdInterval, if_not_exists => true);

-- t_entity_types hyper table creation skipped as it serves only as a reference table and rarely gets updated

-- t_transaction_results hyper table creation skipped as it serves only as a reference table and rarely gets updated

-- t_transaction_types hyper table creation skipped as it serves only as a reference table and rarely gets updated

-- token
select create_hypertable('token', 'created_timestamp',
                         chunk_time_interval => :chunkTimeInterval, if_not_exists => true);

-- token_account
select create_hypertable('token_account', 'created_timestamp',
                         chunk_time_interval => :chunkTimeInterval, if_not_exists => true);

-- token_balance
select create_hypertable('token_balance', 'consensus_timestamp',
                         chunk_time_interval => :chunkTimeInterval, if_not_exists => true);

-- token_transfer
select create_hypertable('token_transfer', 'consensus_timestamp',
                         chunk_time_interval => :chunkTimeInterval, if_not_exists => true);

-- topic_message
select create_hypertable('topic_message', 'consensus_timestamp',
                         chunk_time_interval => :chunkTimeInterval, if_not_exists => true);

-- transaction
select create_hypertable('transaction', 'consensus_ns',
                         chunk_time_interval => :chunkTimeInterval, if_not_exists => true);

