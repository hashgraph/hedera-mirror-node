-------------------
-- Create hyper tables for tables that have mostly insert logic
-- Use default of 604800000000000 ns (7 days) as chunk time interval
-- add TIMESTAMPTZ data type column to tables where no monotonically increasing id exists
-------------------

-- account_balance
select create_hypertable('account_balance', 'consensus_timestamp',
                         chunk_time_interval => ${chunkTimeInterval}, if_not_exists => true);

-- account_balance_file
select create_hypertable('account_balance_file', 'consensus_timestamp',
                         chunk_time_interval => ${chunkTimeInterval}, if_not_exists => true);

-- account_balance_sets
select create_hypertable('account_balance_sets', 'consensus_timestamp',
                         chunk_time_interval => ${chunkTimeInterval}, if_not_exists => true);

-- address_book
select create_hypertable('address_book', 'start_consensus_timestamp',
                         chunk_time_interval => ${chunkTimeInterval}, if_not_exists => true);

-- address_book_entry
select create_hypertable('address_book_entry', 'consensus_timestamp',
                         chunk_time_interval => ${chunkTimeInterval}, if_not_exists => true);

-- contract_result
select create_hypertable('contract_result', 'consensus_timestamp',
                         chunk_time_interval => ${chunkTimeInterval}, if_not_exists => true);

-- crypto_transfer
select create_hypertable('crypto_transfer', 'consensus_timestamp',
                         chunk_time_interval => ${chunkTimeInterval}, if_not_exists => true);

-- file_data
select create_hypertable('file_data', 'consensus_timestamp',
                         chunk_time_interval => ${chunkTimeInterval}, if_not_exists => true);

-- live_hash
select create_hypertable('live_hash', 'consensus_timestamp',
                         chunk_time_interval => ${chunkTimeInterval}, if_not_exists => true);

-- non_fee_transfer
select create_hypertable('non_fee_transfer', 'consensus_timestamp',
                         chunk_time_interval => ${chunkTimeInterval}, if_not_exists => true);

-- record_file
select create_hypertable('record_file', 'consensus_start',
                         chunk_time_interval => ${chunkTimeInterval}, if_not_exists => true);

-- t_application_status

-- t_entities
select create_hypertable('t_entities', 'id',
                         chunk_time_interval => ${chunkIdInterval}, if_not_exists => true);

-- t_entity_types hyper table skipped as table rarely gets updated

-- t_transaction_results hyper table skipped as table rarely gets updated

-- t_transaction_types hyper table skipped as table rarely gets updated

-- token
select create_hypertable('token', 'created_timestamp',
                         chunk_time_interval => ${chunkTimeInterval}, if_not_exists => true);

-- token_account
select create_hypertable('token_account', 'created_timestamp',
                         chunk_time_interval => ${chunkTimeInterval}, if_not_exists => true);

-- token_balance
select create_hypertable('token_balance', 'consensus_timestamp',
                         chunk_time_interval => ${chunkTimeInterval}, if_not_exists => true);

-- token_transfer
select create_hypertable('token_transfer', 'consensus_timestamp',
                         chunk_time_interval => ${chunkTimeInterval}, if_not_exists => true);

-- topic_message
select create_hypertable('topic_message', 'consensus_timestamp',
                         chunk_time_interval => ${chunkTimeInterval}, if_not_exists => true);

-- transaction
select create_hypertable('transaction', 'consensus_ns',
                         chunk_time_interval => ${chunkTimeInterval}, if_not_exists => true);
