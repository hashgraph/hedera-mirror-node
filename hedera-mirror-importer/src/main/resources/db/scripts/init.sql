create user mirror_node with login password 'mirror_node_pass';
create database mirror_node;
alter database mirror_node owner to mirror_node;
create role readonly;
create role readwrite;
create user mirror_grpc with login password 'mirror_grpc_pass';
create user mirror_importer with login password 'mirror_importer_pass';
create user mirror_rest with login password 'mirror_rest_pass';
grant readwrite to mirror_importer;
grant readonly to mirror_grpc;
grant readonly to mirror_rest;
\c mirror_node
create schema if not exists mirrornode authorization mirror_node;
grant usage on schema mirrornode to public;
revoke create on schema mirrornode from public;
grant select on database mirror_node to readonly;
grant usage on schema mirrornode to readonly, readwrite;
grant select, insert, update on database mirror_node to readwrite;

-- run V2.0.0
-- adjust entity.csv to replace memo "" with UUID, then replace back after import
alter table assessed_custom_fee
    alter column effective_payer_account_ids set default '{}';
import into assessed_custom_fee (amount, collector_account_id, consensus_timestamp, token_id) csv data ('gs://hedera-cockroachdb/assessed_custom_fee.csv.gz') with nullif = '';
alter table if exists assessed_custom_fee
    alter column effective_payer_account_ids drop default;
import into account_balance (consensus_timestamp, balance, account_id) csv data ('gs://hedera-cockroachdb/account_balance.csv.gz') with nullif = '';
import into account_balance_file (bytes, consensus_timestamp, count, file_hash, load_start, load_end, name, node_account_id) csv data ('gs://hedera-cockroachdb/account_balance_file.csv.gz') with nullif = '';
import into address_book (start_consensus_timestamp, end_consensus_timestamp, file_id, node_count, file_data) csv data ('gs://hedera-cockroachdb/address_book.csv.gz') with nullif = '';
import into address_book_entry (consensus_timestamp, description, memo, public_key, node_id, node_account_id, node_cert_hash, stake) csv data ('gs://hedera-cockroachdb/address_book_entry.csv.gz') with nullif = '';
import into address_book_service_endpoint (consensus_timestamp, ip_address_v4, node_id, port) csv data ('gs://hedera-cockroachdb/address_book_service_endpoint.csv.gz') with nullif = '';
import into contract_result (function_parameters, gas_supplied, call_result, gas_used, consensus_timestamp) csv data ('gs://hedera-cockroachdb/contract_result.csv.gz') with nullif = '';
import into crypto_transfer (entity_id, consensus_timestamp, amount) csv data ('gs://hedera-cockroachdb/crypto_transfer.csv.gz') with nullif = '';
import into custom_fee (amount, amount_denominator, collector_account_id, created_timestamp, denominating_token_id, maximum_amount, minimum_amount, token_id) csv data ('gs://hedera-cockroachdb/custom_fee.csv.gz') with nullif = '';
import into entity (auto_renew_account_id, auto_renew_period, created_timestamp, deleted, expiration_timestamp, id, key, memo, modified_timestamp, num, proxy_account_id, public_key, realm, shard, submit_key, type) csv data ('gs://hedera-cockroachdb/entity.csv.gz') with nullif = '';
import into file_data (file_data, consensus_timestamp, entity_id, transaction_type) csv data ('gs://hedera-cockroachdb/file_data.csv.gz') with nullif = '';
import into live_hash (livehash, consensus_timestamp) csv data ('gs://hedera-cockroachdb/live_hash.csv.gz') with nullif = '';
import into non_fee_transfer (entity_id, consensus_timestamp, amount) csv data ('gs://hedera-cockroachdb/non_fee_transfer.csv.gz') with nullif = '';
import into nft (account_id, created_timestamp, deleted, modified_timestamp, metadata, serial_number, token_id) csv data ('gs://hedera-cockroachdb/nft.csv.gz') with nullif = '';
import into nft_transfer (consensus_timestamp, receiver_account_id, sender_account_id, serial_number, token_id) csv data ('gs://hedera-cockroachdb/nft_transfer.csv.gz') with nullif = '';
import into record_file (bytes, consensus_start, consensus_end, count, digest_algorithm, file_hash, hapi_version_major, hapi_version_minor, hapi_version_patch, hash, "index", load_start, load_end, name, node_account_id, prev_hash, version) csv data ('gs://hedera-cockroachdb/record_file.csv.gz') with nullif = '';
import into schedule (consensus_timestamp, creator_account_id, executed_timestamp, payer_account_id, schedule_id, transaction_body) csv data ('gs://hedera-cockroachdb/schedule.csv.gz') with nullif = '';
import into token (token_id, created_timestamp, decimals, fee_schedule_key, fee_schedule_key_ed25519_hex, freeze_default, freeze_key, freeze_key_ed25519_hex, initial_supply, kyc_key, kyc_key_ed25519_hex, max_supply, modified_timestamp, name, supply_key, supply_key_ed25519_hex, supply_type, symbol, total_supply, treasury_account_id, type, wipe_key, wipe_key_ed25519_hex) csv data ('gs://hedera-cockroachdb/token.csv.gz') with nullif = '';
import into token_account (account_id, associated, created_timestamp, freeze_status, kyc_status, modified_timestamp, token_id) csv data ('gs://hedera-cockroachdb/token_account.csv.gz') with nullif = '';
import into token_balance (consensus_timestamp, account_id, balance, token_id) csv data ('gs://hedera-cockroachdb/token_balance.csv.gz') with nullif = '';
import into token_transfer (token_id, account_id, consensus_timestamp, amount) csv data ('gs://hedera-cockroachdb/token_transfer.csv.gz') with nullif = '';
import into topic_message (consensus_timestamp, realm_num, topic_num, message, running_hash, sequence_number, running_hash_version, chunk_num, chunk_total, payer_account_id, valid_start_timestamp) csv data ('gs://hedera-cockroachdb/topic_message.csv.gz') with nullif = '';
import into transaction (consensus_ns, type, result, payer_account_id, valid_start_ns, valid_duration_seconds, node_account_id, entity_id, initial_balance, max_fee, charged_tx_fee, memo, scheduled, transaction_hash, transaction_bytes) csv data ('gs://hedera-cockroachdb/transaction.csv.gz') with nullif = '';
import into transaction_signature (consensus_timestamp, public_key_prefix, entity_id, signature) csv data ('gs://hedera-cockroachdb/transaction_signature.csv.gz') with nullif = '';

