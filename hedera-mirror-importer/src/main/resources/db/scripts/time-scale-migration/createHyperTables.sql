-------------------
-- Create hyper tables from v1 schema, inserts from old table, add indexes
-- Use default of 604800000000000 ns (7 days) as chunk time interval
-- update necessary tables with TIMESTAMPTZ data type where no monotonically increasing id exists
-------------------

-- move tables to new schema

-- account_balance
alter table account_balance set schema mirror_node;
alter table account_balance alter column consensus_timestamp type bigint;
select create_hypertable('account_balance', 'consensus_timestamp', chunk_time_interval => 604800000000000, if_not_exists => true);

-- account_balance_sets
alter table account_balance_sets set schema mirror_node;
alter table account_balance_sets alter column consensus_timestamp type bigint;
select create_hypertable('account_balance_sets', 'consensus_timestamp', chunk_time_interval => 604800000000000, if_not_exists => true);

-- address_book
alter table address_book set schema mirror_node;
alter table address_book alter column start_consensus_timestamp type bigint;
select create_hypertable('address_book', 'start_consensus_timestamp', chunk_time_interval => 604800000000000, if_not_exists => true);

-- consider adding TIMESTAMPTZ
-- address_book_entry
alter table address_book_entry set schema mirror_node;
alter table address_book_entry
    alter column consensus_timestamp type bigint,
    alter column consensus_timestamp set not null;
select create_hypertable('address_book_entry', 'consensus_timestamp', chunk_time_interval => 604800000000000, if_not_exists => true);

-- contract_result
alter table contract_result set schema mirror_node;
alter table contract_result alter column consensus_timestamp type bigint;
select create_hypertable('contract_result', 'consensus_timestamp', chunk_time_interval => 604800000000000, if_not_exists => true);

-- crypto_transfer
alter table crypto_transfer set schema mirror_node;
alter table crypto_transfer alter column consensus_timestamp type bigint;
select create_hypertable('crypto_transfer', 'consensus_timestamp', chunk_time_interval => 604800000000000, if_not_exists => true);

-- file_data
alter table file_data set schema mirror_node;
alter table file_data alter column consensus_timestamp type bigint;
select create_hypertable('file_data', 'consensus_timestamp', chunk_time_interval => 604800000000000, if_not_exists => true);

-- flyway_schema_history
alter table flyway_schema_history set schema mirror_node;

-- live_hash
alter table live_hash set schema mirror_node;
alter table live_hash alter column consensus_timestamp type bigint;
select create_hypertable('live_hash', 'consensus_timestamp', chunk_time_interval => 604800000000000, if_not_exists => true);

-- non_fee_transfer
alter table non_fee_transfer set schema mirror_node;
alter table non_fee_transfer alter column consensus_timestamp type bigint;
select create_hypertable('non_fee_transfer', 'consensus_timestamp', chunk_time_interval => 604800000000000, if_not_exists => true);

-- consider adding TIMESTAMPTZ
-- record_file
alter table record_file set schema mirror_node;
alter sequence s_record_files_seq set schema mirror_node;
select create_hypertable('record_file', 'id', chunk_time_interval => 1000, if_not_exists => true);

-- t_application_status
alter table t_application_status set schema mirror_node;

-- t_transaction_results
alter table t_transaction_results set schema mirror_node;

-- t_transaction_types
alter table t_transaction_types set schema mirror_node;

-- consider adding TIMESTAMPTZ
-- t_entities
alter table t_entities set schema mirror_node;
alter table t_entities alter column id type bigint;
select create_hypertable('t_entities', 'id', chunk_time_interval => 1000000, if_not_exists => true);

-- t_entity_types
alter table t_entity_types set schema mirror_node;

-- consider adding TIMESTAMPTZ
-- topic_message
alter table topic_message set schema mirror_node;
alter table topic_message alter column consensus_timestamp type bigint;
select create_hypertable('topic_message', 'consensus_timestamp', chunk_time_interval => 604800000000000, if_not_exists => true);

-- transaction
alter table transaction set schema mirror_node;
select create_hypertable('transaction', 'consensus_ns', chunk_time_interval => 604800000000000, if_not_exists => true);



