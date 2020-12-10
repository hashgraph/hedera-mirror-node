-------------------
-- Add compression policies to larger tables
-------------------

-- hyper tables with integer based require a function that returns the now() value (current time) in the units of the time column
-- Needed for policies
create or replace function unix_now() returns bigint
    language sql
    stable as
$$ select extract(epoch from now())::bigint * 1000000000
$$;

-- set integer now functions for tables
select set_integer_now_func('account_balance', 'unix_now');
select set_integer_now_func('crypto_transfer', 'unix_now');
select set_integer_now_func('token_transfer', 'unix_now');
select set_integer_now_func('topic_message', 'unix_now');
select set_integer_now_func('transaction', 'unix_now');

-- turn compression on
alter table account_balance
    set (timescaledb.compress, timescaledb.compress_orderby = 'consensus_timestamp desc', timescaledb.compress_segmentby = 'account_id');
alter table crypto_transfer
    set (timescaledb.compress, timescaledb.compress_orderby = 'consensus_timestamp desc', timescaledb.compress_segmentby = 'entity_id');
alter table token_transfer
    set (timescaledb.compress, timescaledb.compress_orderby = 'consensus_timestamp desc', timescaledb.compress_segmentby = 'account_id');
alter table topic_message
    set (timescaledb.compress, timescaledb.compress_orderby = 'consensus_timestamp desc', timescaledb.compress_segmentby = 'topic_num');
alter table transaction
    set (timescaledb.compress, timescaledb.compress_orderby = 'consensus_ns desc', timescaledb.compress_segmentby = 'type');

-- add compression policy
select add_compression_policy('account_balance', bigint '${compressionAge}');
select add_compression_policy('crypto_transfer', bigint '${compressionAge}');
select add_compression_policy('token_transfer', bigint '${compressionAge}');
select add_compression_policy('topic_message', bigint '${compressionAge}');
select add_compression_policy('transaction', bigint '${compressionAge}');
