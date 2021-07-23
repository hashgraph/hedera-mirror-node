-------------------
-- Add compression policies to larger tables
-------------------

-- hyper tables with integer based time_column_name require a function that returns the now() value (current time) in
-- the units of the time column. This is needed for policies. Return the latest record file's consensus end timestamp
-- to avoid insert/update on compressed chunks in catch-up mode
create or replace function latest_consensus_timestamp() returns bigint
    stable as
$$
declare
    consensusEnd bigint;
begin
    select consensus_end into consensusEnd from record_file order by consensus_end desc limit 1;
    if not FOUND then
        consensusEnd := 0;
    end if;

    return consensusEnd;
end;
$$ language plpgsql;

-- set integer now functions for tables
select set_integer_now_func('assessed_custom_fee', 'latest_consensus_timestamp');
select set_integer_now_func('account_balance', 'latest_consensus_timestamp');
select set_integer_now_func('account_balance_file', 'latest_consensus_timestamp');
select set_integer_now_func('contract_result', 'latest_consensus_timestamp');
select set_integer_now_func('crypto_transfer', 'latest_consensus_timestamp');
select set_integer_now_func('custom_fee', 'latest_consensus_timestamp');
select set_integer_now_func('event_file', 'latest_consensus_timestamp');
select set_integer_now_func('file_data', 'latest_consensus_timestamp');
select set_integer_now_func('live_hash', 'latest_consensus_timestamp');
select set_integer_now_func('nft_transfer', 'latest_consensus_timestamp');
select set_integer_now_func('non_fee_transfer', 'latest_consensus_timestamp');
select set_integer_now_func('record_file', 'latest_consensus_timestamp');
select set_integer_now_func('transaction_signature', 'latest_consensus_timestamp');
select set_integer_now_func('token_balance', 'latest_consensus_timestamp');
select set_integer_now_func('token_transfer', 'latest_consensus_timestamp');
select set_integer_now_func('topic_message', 'latest_consensus_timestamp');
select set_integer_now_func('transaction', 'latest_consensus_timestamp');

-- turn compression on
alter table assessed_custom_fee
    set (timescaledb.compress, timescaledb.compress_segmentby = 'collector_account_id, token_id');

alter table account_balance
    set (timescaledb.compress, timescaledb.compress_segmentby = 'account_id');

alter table account_balance_file
    set (timescaledb.compress, timescaledb.compress_segmentby = 'node_account_id');

-- address_book skipped as not a hyper table

-- address_book_entry skipped as not a hyper table

-- address_book_service_endpoint skipped as not a hyper table

alter table contract_result
    set (timescaledb.compress);

alter table crypto_transfer
    set (timescaledb.compress, timescaledb.compress_segmentby = 'entity_id');

alter table custom_fee
    set (timescaledb.compress, timescaledb.compress_segmentby = 'token_id');

alter table event_file
    set (timescaledb.compress, timescaledb.compress_segmentby = 'node_account_id');

alter table file_data
    set (timescaledb.compress, timescaledb.compress_segmentby = 'entity_id');

alter table live_hash
    set (timescaledb.compress);

-- nft skipped as update on compressed chunk is not allowed

alter table nft_transfer
    set (timescaledb.compress, timescaledb.compress_segmentby = 'token_id');

alter table non_fee_transfer
    set (timescaledb.compress, timescaledb.compress_segmentby = 'entity_id');

alter table record_file
    set (timescaledb.compress, timescaledb.compress_segmentby = 'node_account_id');

-- schedule skipped as update (executed_timestamp) on compressed chunk is not allowed

-- entity skipped as update on compressed chunk is not allowed

-- t_entity_types skipped as not a hyper table

-- t_transaction_results skipped as not a hyper table

-- t_transaction_types skipped as not a hyper table

-- token skipped as update on compressed chunk is not allowed

-- token_account skipped as update on compressed chunk is not allowed

alter table token_balance
    set (timescaledb.compress, timescaledb.compress_segmentby = 'account_id, token_id');

alter table token_transfer
    set (timescaledb.compress, timescaledb.compress_segmentby = 'account_id');

alter table topic_message
    set (timescaledb.compress, timescaledb.compress_segmentby = 'realm_num, topic_num');

alter table transaction
    set (timescaledb.compress, timescaledb.compress_segmentby = 'payer_account_id, type');

alter table transaction_signature
    set (timescaledb.compress, timescaledb.compress_segmentby = 'entity_id');

-- add compression policy
select add_compression_policy('assessed_custom_fee', bigint '${compressionAge}');
select add_compression_policy('account_balance', bigint '${compressionAge}');
select add_compression_policy('account_balance_file', bigint '${compressionAge}');
select add_compression_policy('contract_result', bigint '${compressionAge}');
select add_compression_policy('crypto_transfer', bigint '${compressionAge}');
select add_compression_policy('custom_fee', bigint '${compressionAge}');
select add_compression_policy('event_file', bigint '${compressionAge}');
select add_compression_policy('file_data', bigint '${compressionAge}');
select add_compression_policy('live_hash', bigint '${compressionAge}');
select add_compression_policy('nft_transfer', bigint '${compressionAge}');
select add_compression_policy('non_fee_transfer', bigint '${compressionAge}');
select add_compression_policy('record_file', bigint '${compressionAge}');
select add_compression_policy('token_balance', bigint '${compressionAge}');
select add_compression_policy('token_transfer', bigint '${compressionAge}');
select add_compression_policy('topic_message', bigint '${compressionAge}');
select add_compression_policy('transaction', bigint '${compressionAge}');
select add_compression_policy('transaction_signature', bigint '${compressionAge}');
