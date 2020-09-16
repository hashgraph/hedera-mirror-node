-------------------
-- Create hyper tables for tables that have mostly insert logic
-- Use default of 604800000000000 ns (7 days) as chunk time interval
-- add TIMESTAMPTZ data type column to tables where no monotonically increasing id exists
-------------------

-- Extend the database with TimescaleDB
create extension if not exists timescaledb cascade;

-- account_balance
select create_hypertable('account_balance', 'consensus_timestamp', chunk_time_interval => 604800000000000, if_not_exists => true);

-- account_balance_file
select create_hypertable('account_balance_file', 'consensus_timestamp', chunk_time_interval => 604800000000000, if_not_exists => true);

-- account_balance_sets
select create_hypertable('account_balance_sets', 'consensus_timestamp', chunk_time_interval => 604800000000000, if_not_exists => true);

-- address_book skipped because foreign keys are supported with hyper tables, also we don't expect high traffic inserts

-- contract_result
select create_hypertable('contract_result', 'consensus_timestamp', chunk_time_interval => 604800000000000, if_not_exists => true);

-- crypto_transfer
select create_hypertable('crypto_transfer', 'consensus_timestamp', chunk_time_interval => 604800000000000, if_not_exists => true);

-- file_data
select create_hypertable('file_data', 'consensus_timestamp', chunk_time_interval => 604800000000000, if_not_exists => true);

-- live_hash
select create_hypertable('live_hash', 'consensus_timestamp', chunk_time_interval => 604800000000000, if_not_exists => true);

-- non_fee_transfer
select create_hypertable('non_fee_transfer', 'consensus_timestamp', chunk_time_interval => 604800000000000, if_not_exists => true);

-- record_file
select create_hypertable('record_file', 'id', chunk_time_interval => 1000, if_not_exists => true);

-- t_application_status

-- t_entities
select create_hypertable('t_entities', 'id', chunk_time_interval => 1000000, if_not_exists => true);

-- t_entity_types hyper table skipped as table rarely gets updated

-- t_transaction_results hyper table skipped as table rarely gets updated

-- t_transaction_types hyper table skipped as table rarely gets updated

-- topic_message
select create_hypertable('topic_message', 'consensus_timestamp', chunk_time_interval => 604800000000000, if_not_exists => true);

-- transaction
select create_hypertable('transaction', 'consensus_ns', chunk_time_interval => 604800000000000, if_not_exists => true);
