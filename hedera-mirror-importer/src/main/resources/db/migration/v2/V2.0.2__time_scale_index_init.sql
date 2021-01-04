-------------------
-- Add constraints and indexes to tables
-------------------

-- account_balance
alter table account_balance
    add constraint account_balance_timestamp_id primary key (consensus_timestamp, account_id);
create index if not exists account_balance__account_timestamp
    on account_balance (account_id desc, consensus_timestamp desc);

-- account_balance_sets
alter table account_balance_sets
    add constraint account_balance_sets_timestamp primary key (consensus_timestamp);
create index if not exists balance_sets__completed
    on account_balance_sets (is_complete, consensus_timestamp desc);

-- account_balance_file
create unique index if not exists account_balance_file__name
    on account_balance_file (name, consensus_timestamp desc);
alter table account_balance_file
    add constraint account_balance_file_timestamp primary key (consensus_timestamp);

-- address_book, desc index on start_consensus_timestamp already created by hypertable

-- address_book_entry, desc index on consensus_timestamp already created by hypertable

-- contract_result
create index if not exists contract_result__consensus
    on contract_result (consensus_timestamp desc);

-- crypto_transfer
create index if not exists crypto_transfer__consensus_timestamp
    on crypto_transfer (consensus_timestamp);
create index if not exists crypto_transfer__entity_id_consensus_timestamp
    on crypto_transfer (entity_id, consensus_timestamp)
    where entity_id != 98;
-- id corresponding to treasury address 0.0.98

-- file_data, desc index on consensus_timestamp already created by hypertable

-- live_hash, desc index on consensus_timestamp already created by hypertable

-- non_fee_transfer
create index if not exists non_fee_transfer__consensus_timestamp
    on non_fee_transfer (consensus_timestamp);

-- record_file
create unique index if not exists record_file_name
    on record_file (name, consensus_start); -- have to add consensus_start due to partitioning
create unique index if not exists record_file_hash
    on record_file (file_hash, consensus_start); -- have to add consensus_start due to partitioning
create index if not exists record_file__consensus_end
    on record_file (consensus_end);
create index if not exists record_file__prev_hash
    on record_file (prev_hash);

-- t_entities
-- Enforce lowercase hex representation by constraint rather than making indexes on lower(ed25519).
alter table t_entities
    add constraint c__t_entities__lower_ed25519
        check (ed25519_public_key_hex = lower(ed25519_public_key_hex));
create index if not exists entities__ed25519_public_key_hex_natural_id
    on t_entities (ed25519_public_key_hex, fk_entity_type_id, entity_shard, entity_realm, entity_num);
create unique index if not exists entities_unq
    on t_entities (entity_shard, entity_realm, entity_num, id);
-- have to add id due to partitioning

-- token
create index if not exists token_id
    on token (token_id);

-- token_account
create unique index if not exists token_account__token_account
    on token_account (token_id, account_id, created_timestamp);

-- token_balance
alter table if exists token_balance
    add constraint token_balance_timestamp_ids primary key (consensus_timestamp, account_id, token_id);

-- token_transfer
create index if not exists token_transfer__token_account_timestamp
    on token_transfer (consensus_timestamp desc, token_id desc, account_id desc);

-- topic_message
alter table if exists topic_message
    add constraint topic_message_timestamp primary key (consensus_timestamp);
create index if not exists topic_message__realm_num_timestamp
    on topic_message (realm_num, topic_num, consensus_timestamp);
create unique index if not exists topic_message__topic_num_realm_num_seqnum
    on topic_message (realm_num, topic_num, sequence_number, consensus_timestamp);
-- have to add consensus_timestamp due to partitioning

-- transaction
create index if not exists transaction__transaction_id
    on transaction (valid_start_ns, payer_account_id);
create index if not exists transaction__payer_account_id
    on transaction (payer_account_id);
create index if not exists transaction_consensus_ns
    on transaction (consensus_ns);
