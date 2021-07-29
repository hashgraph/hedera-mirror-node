-------------------
-- Add constraints and indexes to tables
-------------------

-- assessed_custom_fee
create index if not exists assessed_custom_fee__consensus_timestamp
    on assessed_custom_fee (consensus_timestamp);

-- account_balance
-- alter table account_balance
--     add primary key (consensus_timestamp, account_id);
create index if not exists account_balance__account_timestamp
    on account_balance (account_id desc, consensus_timestamp desc);

-- account_balance_file
-- alter table account_balance_file
--     add primary key (consensus_timestamp);
create unique index if not exists account_balance_file__name
    on account_balance_file (name, consensus_timestamp desc);

-- address_book
-- alter table address_book
--     add primary key (start_consensus_timestamp);

-- address_book_entry
-- alter table address_book_entry
--     add primary key (consensus_timestamp, node_id);

-- address_book_service_endpoint
-- alter table address_book_service_endpoint
--     add primary key (consensus_timestamp, node_id, ip_address_v4, port);

-- contract_result
-- create index if not exists contract_result__consensus
--     on contract_result (consensus_timestamp desc);

-- crypto_transfer
-- create index if not exists crypto_transfer__consensus_timestamp
--     on crypto_transfer (consensus_timestamp);
-- create index if not exists crypto_transfer__entity_id_consensus_timestamp
--     on crypto_transfer (entity_id, consensus_timestamp)
--     where entity_id != 98;
-- id corresponding to treasury address 0.0.98

-- custom_fee
-- create index if not exists custom_fee__token_timestamp
--     on custom_fee (token_id desc, created_timestamp desc);

-- entity
-- alter table entity
--     add primary key (id);
-- Enforce lowercase hex representation by constraint rather than making indexes on lower(ed25519).
alter table entity
    add constraint c__entity__lower_ed25519
        check (public_key = lower(public_key));
create index if not exists entity__id_type
    on entity (id, type);
create index if not exists entity__public_key
    on entity (public_key) where public_key is not null;
create unique index if not exists entity__shard_realm_num
    on entity (shard, realm, num, id);
-- have to add id when creating unique indexes due to partitioning

-- event_file
-- alter table event_file
--     add primary key (consensus_end);
create unique index if not exists event_file__hash
    on event_file (hash, consensus_end);

-- file_data
-- alter table file_data
--     add primary key (consensus_timestamp);

-- live_hash
-- alter table live_hash
--     add primary key (consensus_timestamp);

-- nft
-- alter table nft
--     add primary key (token_id, serial_number, created_timestamp);

-- nft_transfer
-- create unique index if not exists nft_transfer__timestamp_token_id_serial_num
--     on nft_transfer (consensus_timestamp desc, token_id desc, serial_number desc);

-- non_fee_transfer
create index if not exists non_fee_transfer__consensus_timestamp
    on non_fee_transfer (consensus_timestamp);

-- record_file
-- alter table record_file
--     add primary key (consensus_end);
create unique index if not exists record_file__index
    on record_file (idx, consensus_end); -- have to add consensus_end due to partitioning
create unique index if not exists record_file__hash
    on record_file (hash, consensus_end); -- have to add consensus_end due to partitioning
create index if not exists record_file__prev_hash
    on record_file (prev_hash);

-- schedule
-- alter table if exists schedule
--    add primary key (schedule_id);
create index if not exists schedule__creator_account_id
    on schedule (creator_account_id desc);

-- t_entity_types
-- alter table t_entity_types
--     add primary key (id);

-- t_transaction_results
-- alter table t_transaction_results
--     add primary key (proto_id);
create unique index if not exists t_transaction_results_name
    on t_transaction_results (result);

-- t_transaction_types
-- alter table t_transaction_types
--     add primary key (proto_id);
create unique index if not exists t_transaction_types_name
    on t_transaction_types (name);

-- token
-- alter table token
--     add primary key (created_timestamp);
create unique index if not exists token__id_timestamp
    on token (token_id, created_timestamp);

-- token_account
-- alter table token_account
--     add primary key (created_timestamp, token_id);
create unique index if not exists token_account__token_account_timestamp
    on token_account (token_id, account_id, created_timestamp);

-- token_balance
-- alter table token_balance
--     add primary key (consensus_timestamp, account_id, token_id);

-- token_transfer
-- create index if not exists token_transfer__token_account_timestamp
--     on token_transfer (consensus_timestamp desc, token_id desc, account_id desc);
create index if not exists token_transfer__account_timestamp
    on token_transfer (account_id, consensus_timestamp desc);

-- topic_message
-- alter table if exists topic_message
--     add primary key (consensus_timestamp);
create index if not exists topic_message__realm_num_timestamp
    on topic_message (realm_num, topic_num, consensus_timestamp);
create unique index if not exists topic_message__topic_num_realm_num_seqnum
    on topic_message (realm_num, topic_num, sequence_number, consensus_timestamp);
-- have to add consensus_timestamp when creating unique indexes due to partitioning

-- transaction
-- alter table if exists transaction
--     add primary key (consensus_ns);
create index if not exists transaction__transaction_id
    on transaction (valid_start_ns, payer_account_id);
create index if not exists transaction__payer_account_id
    on transaction (payer_account_id);
create index if not exists transaction_type
    on transaction (type, consensus_ns desc);

-- transaction_signature
create index if not exists transaction_signature__entity_id
    on transaction_signature (entity_id desc, consensus_timestamp desc);

-- create unique index if not exists transaction_signature__timestamp_public_key_prefix
--     on transaction_signature (consensus_timestamp desc, public_key_prefix);

