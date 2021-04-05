-------------------
-- Restore Backup tables use efficient COPY process to CSV's
-------------------

\copy account_balance (consensus_timestamp, balance, account_id) from account_balance.csv csv;

\copy account_balance_file (consensus_timestamp, count, load_start, load_end, file_hash, name, node_account_id, bytes) from account_balance_file.csv csv;

\copy address_book (start_consensus_timestamp, end_consensus_timestamp, file_id, node_count, file_data) from address_book.csv csv;

\copy address_book_entry (id, consensus_timestamp, ip, port, memo, public_key, node_id, node_account_id, node_cert_hash) from address_book_entry.csv csv;

\copy contract_result (function_parameters, gas_supplied, call_result, gas_used, consensus_timestamp) from contract_result.csv csv;

\copy crypto_transfer (entity_id, consensus_timestamp, amount) from crypto_transfer.csv csv;

\copy file_data (file_data, consensus_timestamp, entity_id, transaction_type) from file_data.csv csv;

\copy live_hash (livehash, consensus_timestamp) from live_hash.csv csv;

\copy non_fee_transfer (entity_id, consensus_timestamp, amount) from non_fee_transfer.csv csv;

\copy record_file (name, load_start, load_end, hash, prev_hash, consensus_start, consensus_end, node_account_id, count, digest_algorithm, hapi_version_major, hapi_version_minor, hapi_version_patch, version, file_hash, bytes, index) from record_file.csv csv;

\copy schedule (consensus_timestamp, creator_account_id, executed_timestamp, payer_account_id, schedule_id, transaction_body) from schedule.csv csv;

\copy entity (auto_renew_account_id, auto_renew_period, created_timestamp, deleted, expiration_timestamp, id, key, memo, modified_timestamp, num, public_key, proxy_account_id, realm, shard, submit_key, type) from entity.csv csv;

\copy token (token_id, created_timestamp, decimals, freeze_default, freeze_key, freeze_key_ed25519_hex, initial_supply, kyc_key, kyc_key_ed25519_hex, modified_timestamp, name, supply_key, supply_key_ed25519_hex, symbol, total_supply, treasury_account_id, wipe_key, wipe_key_ed25519_hex) from token.csv csv;

\copy token_account (account_id, associated, created_timestamp, freeze_status, kyc_status, modified_timestamp, token_id) from token_account.csv csv;

\copy token_balance (consensus_timestamp, account_id, balance, token_id) from token_balance.csv csv;

\copy token_transfer (token_id, account_id, consensus_timestamp, amount) from token_transfer.csv csv;

\copy topic_message (consensus_timestamp, realm_num, topic_num, message, running_hash, sequence_number, running_hash_version, chunk_num, chunk_total, payer_account_id, valid_start_timestamp) from topic_message.csv csv;

\copy transaction (consensus_ns, type, result, payer_account_id, valid_start_ns, valid_duration_seconds, node_account_id, entity_id, initial_balance, max_fee, charged_tx_fee, memo, transaction_hash, transaction_bytes, scheduled) from transaction.csv csv;

\copy transaction_signature (consensus_timestamp, public_key_prefix, entity_id, signature) from transaction_signature.csv csv;
