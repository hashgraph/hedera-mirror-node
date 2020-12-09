-------------------
-- Create hyper tables for tables that have mostly insert logic
-- Use default of 604800000000000 ns (7 days) as chunk time interval
-- add TIMESTAMPTZ data type column to tables where no monotonically increasing id exists
-------------------

-- Extend the database with TimescaleDB - assumes super user permissions on database
--create extension if not exists timescaledb cascade;

-- account_balance, distribute across account_id
select create_distributed_hypertable('account_balance', 'consensus_timestamp', chunk_time_interval => ${default_chunk_time_interval},
                         if_not_exists => true, partitioning_column => 'account_id');

-- account_balance_file
select create_hypertable('account_balance_file', 'consensus_timestamp',
                         chunk_time_interval => ${default_chunk_time_interval}, if_not_exists => true);

-- account_balance_sets
select create_hypertable('account_balance_sets', 'consensus_timestamp',
                         chunk_time_interval => ${default_chunk_time_interval}, if_not_exists => true);

-- address_book skipped because foreign keys are not supported with hyper tables, also we don't expect high traffic inserts

-- contract_result
select create_hypertable('contract_result', 'consensus_timestamp', chunk_time_interval => ${default_chunk_time_interval},
                         if_not_exists => true);

-- crypto_transfer, distribute across entity_id
select create_distributed_hypertable('crypto_transfer', 'consensus_timestamp', chunk_time_interval => ${default_chunk_time_interval},
                         if_not_exists => true, partitioning_column => 'entity_id');

-- file_data, distribute across entity_id
select create_distributed_hypertable('file_data', 'consensus_timestamp', chunk_time_interval => ${default_chunk_time_interval},
                         if_not_exists => true, partitioning_column => 'entity_id');

-- live_hash
select create_hypertable('live_hash', 'consensus_timestamp', chunk_time_interval => ${default_chunk_time_interval},
                         if_not_exists => true);

-- non_fee_transfer
select create_hypertable('non_fee_transfer', 'consensus_timestamp', chunk_time_interval => ${default_chunk_time_interval},
                         if_not_exists => true);

-- record_file, distribute across node_account_id
select create_distributed_hypertable('record_file', 'consensus_start', chunk_time_interval => ${default_chunk_id_interval},
    if_not_exists => true, partitioning_column => 'node_account_id');

-- t_application_status

-- t_entities, distribute across entity_type
select create_distributed_hypertable('t_entities', 'id', chunk_time_interval => ${default_chunk_id_interval},
    if_not_exists => true, partitioning_column => 'entity_type');

-- t_entity_types hyper table skipped as table rarely gets updated

-- t_transaction_results hyper table skipped as table rarely gets updated

-- t_transaction_types hyper table skipped as table rarely gets updated

-- token, distribute across treasury_account_id
select create_distributed_hypertable('token', 'created_timestamp', chunk_time_interval => ${default_chunk_time_interval},
    if_not_exists => true, partitioning_column => 'treasury_account_id');

-- token_account, distribute across kyc_status
select create_distributed_hypertable('token_account', 'created_timestamp', chunk_time_interval => ${default_chunk_time_interval},
                         if_not_exists => true, partitioning_column => 'kyc_status');

-- token_balance, distribute across account_id
select create_distributed_hypertable('token_balance', 'consensus_timestamp', chunk_time_interval => ${default_chunk_time_interval},
                         if_not_exists => true, partitioning_column => 'account_id');

-- token_transfer, distribute across account_id
select create_distributed_hypertable('token_transfer', 'consensus_timestamp', chunk_time_interval => ${default_chunk_time_interval},
                         if_not_exists => true, partitioning_column => 'account_id');

-- topic_message, distribute across topic_num
select create_distributed_hypertable('topic_message', 'consensus_timestamp', chunk_time_interval => ${default_chunk_time_interval},
                         if_not_exists => true, partitioning_column => 'topic_num');

-- transaction, distribute across type
select create_distributed_hypertable('transaction', 'consensus_ns', chunk_time_interval => ${default_chunk_time_interval},
                         if_not_exists => true, partitioning_column => 'type');
