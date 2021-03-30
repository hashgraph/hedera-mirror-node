-------------------
-- Create hyper tables for tables that have mostly insert logic
-- Set chunk_time_interval using parameterized value, usually default of 604800000000000 ns (7 days)
-- Set create_default_indexes to false for tables where a primary key is needed or an index in ASC order is needed.
-- By default TimescaleDB adds an index in DESC order for partitioning column
-------------------

-- account_balance
select create_hypertable('account_balance', 'consensus_timestamp', chunk_time_interval => ${chunkTimeInterval},
                         create_default_indexes => false, if_not_exists => true);

-- account_balance_file
select create_hypertable('account_balance_file', 'consensus_timestamp', chunk_time_interval => ${chunkTimeInterval},
                         create_default_indexes => false, if_not_exists => true);

-- address_book
select create_hypertable('address_book', 'start_consensus_timestamp', chunk_time_interval => ${chunkTimeInterval},
                         create_default_indexes => false, if_not_exists => true);

-- address_book_entry
select create_hypertable('address_book_entry', 'consensus_timestamp', chunk_time_interval => ${chunkTimeInterval},
                         create_default_indexes => false, if_not_exists => true);

-- contract_result
select create_hypertable('contract_result', 'consensus_timestamp', chunk_time_interval => ${chunkTimeInterval},
                         create_default_indexes => false, if_not_exists => true);

-- crypto_transfer
select create_hypertable('crypto_transfer', 'consensus_timestamp', chunk_time_interval => ${chunkTimeInterval},
                         create_default_indexes => false, if_not_exists => true);

-- event_file
select create_hypertable('event_file', 'consensus_end', chunk_time_interval => ${chunkTimeInterval},
                         create_default_indexes => false, if_not_exists => true);

-- file_data
select create_hypertable('file_data', 'consensus_timestamp', chunk_time_interval => ${chunkTimeInterval},
                         create_default_indexes => false, if_not_exists => true);

-- live_hash
select create_hypertable('live_hash', 'consensus_timestamp', chunk_time_interval => ${chunkTimeInterval},
                         create_default_indexes => false, if_not_exists => true);

-- non_fee_transfer
select create_hypertable('non_fee_transfer', 'consensus_timestamp', chunk_time_interval => ${chunkTimeInterval},
                         create_default_indexes => false, if_not_exists => true);

-- record_file
select create_hypertable('record_file', 'consensus_end', chunk_time_interval => ${chunkTimeInterval},
                         create_default_indexes => false, if_not_exists => true);

-- schedule
select create_hypertable('schedule', 'consensus_timestamp', chunk_time_interval => ${chunkTimeInterval},
                         create_default_indexes => false, if_not_exists => true);

-- t_entities
select create_hypertable('t_entities', 'id', chunk_time_interval => ${chunkIdInterval},
                         create_default_indexes => false, if_not_exists => true);

-- t_entity_types hyper table creation skipped as it serves only as a reference table and rarely gets updated

-- t_transaction_results hyper table creation skipped as it serves only as a reference table and rarely gets updated

-- t_transaction_types hyper table creation skipped as it serves only as a reference table and rarely gets updated

-- token
select create_hypertable('token', 'created_timestamp', chunk_time_interval => ${chunkTimeInterval},
                         create_default_indexes => false, if_not_exists => true);

-- token_account
select create_hypertable('token_account', 'created_timestamp', chunk_time_interval => ${chunkTimeInterval},
                         create_default_indexes => false, if_not_exists => true);

-- token_balance
select create_hypertable('token_balance', 'consensus_timestamp', chunk_time_interval => ${chunkTimeInterval},
                         create_default_indexes => false, if_not_exists => true);

-- token_transfer
select create_hypertable('token_transfer', 'consensus_timestamp', chunk_time_interval => ${chunkTimeInterval},
                         create_default_indexes => false, if_not_exists => true);

-- topic_message
select create_hypertable('topic_message', 'consensus_timestamp', chunk_time_interval => ${chunkTimeInterval},
                         create_default_indexes => false, if_not_exists => true);

-- transaction
select create_hypertable('transaction', 'consensus_ns', chunk_time_interval => ${chunkTimeInterval},
                         create_default_indexes => false, if_not_exists => true);

-- transaction_signature
select create_hypertable('transaction_signature', 'consensus_timestamp', chunk_time_interval => ${chunkTimeInterval},
                         create_default_indexes => false, if_not_exists => true);
