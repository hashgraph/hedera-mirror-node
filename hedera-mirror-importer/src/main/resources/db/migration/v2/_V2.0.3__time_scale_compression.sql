-------------------
-- Add compression policies to larger tables
-------------------

/*
 completely disable compression until timescaledb release 2.1 which should support adding/renaming columns of compressed
 hypertables as well as updating compressed chunks
 */

-- hyper tables with integer based time_column_name require a function that returns the now() value (current time) in the units of the time column
-- This is needed for policies
create or replace function unix_now() returns bigint
    language sql
    stable as
$$
select extract(epoch from now())::bigint * 1000000000
$$;

-- set integer now functions for tables
select set_integer_now_func('account_balance', 'unix_now');
select set_integer_now_func('account_balance_file', 'unix_now');
select set_integer_now_func('account_balance_sets', 'unix_now');
select set_integer_now_func('address_book', 'unix_now');
select set_integer_now_func('address_book_entry', 'unix_now');
select set_integer_now_func('contract_result', 'unix_now');
select set_integer_now_func('crypto_transfer', 'unix_now');
select set_integer_now_func('event_file', 'unix_now');
select set_integer_now_func('file_data', 'unix_now');
select set_integer_now_func('live_hash', 'unix_now');
select set_integer_now_func('non_fee_transfer', 'unix_now');
select set_integer_now_func('record_file', 'unix_now');
select set_integer_now_func('schedule', 'unix_now');
select set_integer_now_func('schedule_signature', 'unix_now');
select set_integer_now_func('t_entities', 'unix_now');
select set_integer_now_func('token', 'unix_now');
select set_integer_now_func('token_account', 'unix_now');
select set_integer_now_func('token_balance', 'unix_now');
select set_integer_now_func('token_transfer', 'unix_now');
select set_integer_now_func('topic_message', 'unix_now');
select set_integer_now_func('transaction', 'unix_now');

-- turn compression on
alter table account_balance
    set (timescaledb.compress, timescaledb.compress_segmentby = 'account_id');

alter table account_balance_file
    set (timescaledb.compress, timescaledb.compress_segmentby = 'node_account_id');

alter table account_balance_sets
    set (timescaledb.compress);

alter table address_book
    set (timescaledb.compress, timescaledb.compress_segmentby = 'start_consensus_timestamp, file_id');

alter table address_book_entry
    set (timescaledb.compress, timescaledb.compress_segmentby = 'consensus_timestamp, memo');

alter table contract_result
    set (timescaledb.compress);

alter table crypto_transfer
    set (timescaledb.compress, timescaledb.compress_segmentby = 'entity_id');

alter table event_file
    set (timescaledb.compress, timescaledb.compress_segmentby = 'node_account_id');

alter table file_data
    set (timescaledb.compress, timescaledb.compress_segmentby = 'entity_id');

alter table live_hash
    set (timescaledb.compress);

alter table non_fee_transfer
    set (timescaledb.compress, timescaledb.compress_segmentby = 'entity_id');

alter table record_file
    set (timescaledb.compress, timescaledb.compress_segmentby = 'node_account_id');

alter table schedule
    set (timescaledb.compress, timescaledb.compress_segmentby = 'schedule_id');

alter table schedule_signature
    set (timescaledb.compress, timescaledb.compress_segmentby = 'schedule_id');

alter table t_entities
    set (timescaledb.compress, timescaledb.compress_segmentby = 'fk_entity_type_id');

-- t_entity_types skipped as not a hyper table

-- t_transaction_results skipped as not a hyper table

-- t_transaction_types skipped as not a hyper table

alter table token
    set (timescaledb.compress, timescaledb.compress_segmentby = 'token_id');

alter table token_account
    set (timescaledb.compress, timescaledb.compress_segmentby = 'token_id');

alter table token_balance
    set (timescaledb.compress, timescaledb.compress_segmentby = 'account_id, token_id');

alter table token_transfer
    set (timescaledb.compress, timescaledb.compress_segmentby = 'account_id');

alter table topic_message
    set (timescaledb.compress, timescaledb.compress_segmentby = 'realm_num, topic_num');

alter table transaction
    set (timescaledb.compress, timescaledb.compress_segmentby = 'payer_account_id, type');


-- add compression policy
select add_compression_policy('account_balance', bigint '${compressionAge}');
select add_compression_policy('account_balance_file', bigint '${compressionAge}');
select add_compression_policy('account_balance_sets', bigint '${compressionAge}');
select add_compression_policy('address_book', bigint '${compressionAge}');
select add_compression_policy('address_book_entry', bigint '${compressionAge}');
select add_compression_policy('contract_result', bigint '${compressionAge}');
select add_compression_policy('crypto_transfer', bigint '${compressionAge}');
select add_compression_policy('event_file', bigint '${compressionAge}');
select add_compression_policy('file_data', bigint '${compressionAge}');
select add_compression_policy('live_hash', bigint '${compressionAge}');
select add_compression_policy('non_fee_transfer', bigint '${compressionAge}');
select add_compression_policy('record_file', bigint '${compressionAge}');
select add_compression_policy('schedule', bigint '${compressionAge}');
select add_compression_policy('schedule_signature', bigint '${compressionAge}');
select add_compression_policy('t_entities', bigint '${compressionAge}');
select add_compression_policy('token', bigint '${compressionAge}');
select add_compression_policy('token_account', bigint '${compressionAge}');
select add_compression_policy('token_balance', bigint '${compressionAge}');
select add_compression_policy('token_transfer', bigint '${compressionAge}');
select add_compression_policy('topic_message', bigint '${compressionAge}');
select add_compression_policy('transaction', bigint '${compressionAge}');
