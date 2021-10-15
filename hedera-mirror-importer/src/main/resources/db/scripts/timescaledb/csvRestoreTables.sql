-------------------
-- Restore Backup tables use efficient COPY process to CSV's
-------------------

\copy account_balance (consensus_timestamp, balance, account_id) from account_balance.csv csv;

\copy account_balance_file (consensus_timestamp, count, load_start, load_end, file_hash, name, node_account_id, bytes) from account_balance_file.csv csv;

\copy address_book (start_consensus_timestamp, end_consensus_timestamp, file_id, node_count, file_data) from address_book.csv csv;

\copy address_book_entry (consensus_timestamp, memo, public_key, node_id, node_account_id, node_cert_hash, description, stake) from address_book_entry.csv csv;

\copy address_book_service_endpoint (consensus_timestamp, ip_address_v4, node_id, port) from address_book_service_endpoint.csv csv;

\copy assessed_custom_fee (amount, collector_account_id, consensus_timestamp, token_id) from assessed_custom_fee.csv csv;

\copy contract (auto_renew_period, created_timestamp, deleted, expiration_timestamp, file_id, id, key, memo, num, obtainer_id, proxy_account_id, public_key, realm, shard, timestamp_range, type) from contract.csv csv;

\copy contract_history (auto_renew_period, created_timestamp, deleted, expiration_timestamp, file_id, id, key, memo, num, obtainer_id, proxy_account_id, public_key, realm, shard, timestamp_range, type) from contract_history.csv csv;

\copy contract_log (bloom, consensus_timestamp, contract_id, data, index, topic0, topic1, topic2, topic3) from contract_log.csv csv;

\copy contract_result (amount, bloom, call_result, consensus_timestamp, contract_id, created_contract_ids, error_message, function_parameters, function_result, gas_limit, gas_used) from contract_result.csv csv;

\copy crypto_transfer (entity_id, consensus_timestamp, amount) from crypto_transfer.csv csv;

\copy custom_fee (amount, amount_denominator, collector_account_id, created_timestamp, denominating_token_id, maximum_amount, minimum_amount, token_id) from custom_fee.csv csv;

\copy entity (auto_renew_account_id, auto_renew_period, created_timestamp, deleted, expiration_timestamp, id, key, memo, num, public_key, proxy_account_id, realm, shard, submit_key, type, receiver_sig_required, max_automatic_token_associations, timestamp_range) from entity.csv csv;

\copy entity_history (auto_renew_account_id, auto_renew_period, created_timestamp, deleted, expiration_timestamp, id, key, memo, num, public_key, proxy_account_id, realm, shard, submit_key, type, receiver_sig_required, max_automatic_token_associations, timestamp_range) from entity_history.csv csv;

\copy event_file (bytes, consesnsus_start, consensus_end, count, digest_algorithm, file_hash, hash, load_start, load_end, name, node_account_id, previous_hash, version) from event_file.csv csv;

\copy file_data (file_data, consensus_timestamp, entity_id, transaction_type) from file_data.csv csv;

\copy live_hash (livehash, consensus_timestamp) from live_hash.csv csv;

\copy nft (account_id, created_timestamp, deleted, modified_timestamp, metadata, serial_number, token_id) from nft.csv csv;

\copy nft_transfer (consensus_timestamp, receiver_account_id, sender_account_id, serial_number, token_id) from nft_transfer.csv csv

\copy non_fee_transfer (entity_id, consensus_timestamp, amount) from non_fee_transfer.csv csv;

\copy record_file (name, load_start, load_end, hash, prev_hash, consensus_start, consensus_end, node_account_id, count, digest_algorithm, hapi_version_major, hapi_version_minor, hapi_version_patch, version, file_hash, bytes, index) from record_file.csv csv;

\copy schedule (consensus_timestamp, creator_account_id, executed_timestamp, payer_account_id, schedule_id, transaction_body) from schedule.csv csv;

\copy token (token_id, created_timestamp, decimals, fee_schedule_key, fee_schedule_key_ed25519_hex, freeze_default, freeze_key, freeze_key_ed25519_hex, initial_supply, kyc_key, kyc_key_ed25519_hex, max_supply, modified_timestamp, name, supply_key, supply_key_ed25519_hex, supply_type, symbol, total_supply, treasury_account_id, type, wipe_key, wipe_key_ed25519_hex, pause_key, pause_status) from token.csv csv;

\copy token_account (account_id, associated, created_timestamp, freeze_status, kyc_status, modified_timestamp, token_id, automatic_association) from token_account.csv csv;

\copy token_balance (consensus_timestamp, account_id, balance, token_id) from token_balance.csv csv;

\copy token_transfer (token_id, account_id, consensus_timestamp, amount) from token_transfer.csv csv;

\copy topic_message (consensus_timestamp, realm_num, topic_num, message, running_hash, sequence_number, running_hash_version, chunk_num, chunk_total, payer_account_id, valid_start_timestamp) from topic_message.csv csv;

\copy transaction (consensus_ns, type, result, payer_account_id, valid_start_ns, valid_duration_seconds, node_account_id, entity_id, initial_balance, max_fee, charged_tx_fee, memo, transaction_hash, transaction_bytes, scheduled) from transaction.csv csv;

\copy transaction_signature (consensus_timestamp, public_key_prefix, entity_id, signature) from transaction_signature.csv csv;
