-------------------
-- Add constraints and indexes to tables
-------------------

set experimental_enable_hash_sharded_indexes = on;

-- assessed_custom_fee
create index if not exists assessed_custom_fee__consensus_timestamp
    on assessed_custom_fee (consensus_timestamp);

-- account_balance
create index if not exists account_balance__account_timestamp
    on account_balance (account_id desc, consensus_timestamp desc) using hash with bucket_count = 8;

-- account_balance_file
create unique index if not exists account_balance_file__name
    on account_balance_file (name desc);

-- address_book

-- address_book_entry

-- address_book_service_endpoint

-- contract_result

-- crypto_transfer
create index if not exists crypto_transfer__entity_id_consensus_timestamp
    on crypto_transfer (entity_id, consensus_timestamp) using hash with bucket_count = 8
    where entity_id != 98;
-- id corresponding to treasury address 0.0.98

-- custom_fee
-- create index if not exists custom_fee__token_timestamp
--     on custom_fee (token_id desc, created_timestamp desc);

-- entity
-- Enforce lowercase hex representation by constraint rather than making indexes on lower(ed25519).
alter table entity
    add constraint c__entity__lower_ed25519
        check (public_key = lower(public_key));
create index if not exists entity__id_type
    on entity (id, type);
create index if not exists entity__public_key
    on entity (public_key) where public_key is not null;
create unique index if not exists entity__shard_realm_num
    on entity (shard, realm, num);

-- event_file

create unique index if not exists event_file__hash
    on event_file (hash, consensus_end);

-- file_data
create index if not exists file_data__entity_id on file_data (entity_id) using hash with bucket_count = 8;

-- live_hash

-- nft

-- nft_transfer

-- non_fee_transfer
create index if not exists non_fee_transfer__consensus_timestamp
    on non_fee_transfer (consensus_timestamp);

-- record_file
create unique index if not exists record_file__index
    on record_file ("index");
create unique index if not exists record_file__hash
    on record_file (hash);
create index if not exists record_file__prev_hash
    on record_file (prev_hash);

-- schedule
create index if not exists schedule__creator_account_id
    on schedule (creator_account_id desc);

-- t_entity_types

-- t_transaction_results
create unique index if not exists t_transaction_results_name
    on t_transaction_results (result);

-- t_transaction_types
create unique index if not exists t_transaction_types_name
    on t_transaction_types (name);

-- token

-- token_account

-- token_balance

-- token_transfer
create index if not exists token_transfer__account_timestamp
    on token_transfer (account_id, consensus_timestamp desc) using hash with bucket_count = 8;

-- topic_message
create index if not exists topic_message__realm_num_timestamp
    on topic_message (topic_num, consensus_timestamp) using hash with bucket_count = 8;
create index if not exists topic_message__topic_num_realm_num_seqnum
    on topic_message (topic_num, sequence_number, consensus_timestamp);

-- transaction
create index if not exists transaction__transaction_id
    on transaction (valid_start_ns, payer_account_id) using hash with bucket_count = 8;
create index if not exists transaction__payer_account_id
    on transaction (payer_account_id, consensus_ns);
create index if not exists transaction_type
    on transaction (type);

-- transaction_signature
create index if not exists transaction_signature__entity_id
    on transaction_signature (entity_id desc, consensus_timestamp desc);
