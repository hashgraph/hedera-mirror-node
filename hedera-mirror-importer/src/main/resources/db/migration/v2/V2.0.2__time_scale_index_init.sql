-------------------
-- Add constraints and indexes to tables
-------------------

-- account_balance
alter table account_balance
    add primary key (consensus_timestamp, account_id);
create index if not exists account_balance__account_timestamp
    on account_balance (account_id desc, consensus_timestamp desc);

-- account_balance_sets
alter table account_balance_sets
    add primary key (consensus_timestamp);
create index if not exists balance_sets__completed
    on account_balance_sets (is_complete, consensus_timestamp desc);

-- account_balance_file
alter table account_balance_file
    add primary key (consensus_timestamp);
create unique index if not exists account_balance_file__name
    on account_balance_file (name, consensus_timestamp desc);

-- address_book
alter table address_book
    add primary key (start_consensus_timestamp);

-- address_book_entry
alter table address_book_entry
    add primary key (consensus_timestamp, memo);
create index if not exists address_book_entry__timestamp
    on address_book_entry (consensus_timestamp);

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

-- event_file
alter table event_file
    add primary key (consensus_end);
create unique index if not exists event_file__hash
    on event_file (hash, consensus_end);

-- file_data
alter table file_data
    add primary key (consensus_timestamp);

-- live_hash
alter table live_hash
    add primary key (consensus_timestamp);

-- non_fee_transfer
create index if not exists non_fee_transfer__consensus_timestamp
    on non_fee_transfer (consensus_timestamp);

-- record_file
alter table record_file
    add primary key (consensus_start);
create unique index if not exists record_file__name
    on record_file (name, consensus_start); -- have to add consensus_start due to partitioning
create unique index if not exists record_file__hash
    on record_file (hash, consensus_start); -- have to add consensus_start due to partitioning
create index if not exists record_file__consensus_end
    on record_file (consensus_end);
create index if not exists record_file__prev_hash
    on record_file (prev_hash);

-- schedule
create unique index if not exists schedule__schedule_id
    on schedule (schedule_id desc, consensus_timestamp desc);

-- schedule_signature
create index if not exists schedule_signature__schedule_id
    on schedule_signature (schedule_id desc, consensus_timestamp desc);

create unique index if not exists schedule_signature__timestamp_public_key_prefix
    on schedule_signature (consensus_timestamp desc, public_key_prefix);

-- t_entities
alter table t_entities
    add primary key (id);
-- Enforce lowercase hex representation by constraint rather than making indexes on lower(ed25519).
alter table t_entities
    add constraint c__t_entities__lower_ed25519
        check (ed25519_public_key_hex = lower(ed25519_public_key_hex));
create index if not exists entities__ed25519_public_key_hex_natural_id
    on t_entities (ed25519_public_key_hex, fk_entity_type_id, entity_shard, entity_realm, entity_num);
create unique index if not exists entities_unq
    on t_entities (entity_shard, entity_realm, entity_num, id);
-- have to add id when creating unique indexes due to partitioning

-- t_entity_types
alter table t_entity_types
    add primary key (id);

-- t_transaction_results
alter table t_transaction_results
    add primary key (proto_id);
create unique index if not exists t_transaction_results_name
    on t_transaction_results (result);

-- t_transaction_types
alter table t_transaction_types
    add primary key (proto_id);
create unique index if not exists t_transaction_types_name
    on t_transaction_types (name);

-- token
alter table token
    add primary key (created_timestamp);
create unique index if not exists token__id_timestamp
    on token (token_id, created_timestamp);

-- token_account
alter table token_account
    add primary key (created_timestamp, token_id);
create unique index if not exists token_account__token_account_timestamp
    on token_account (token_id, account_id, created_timestamp);

-- token_balance
alter table token_balance
    add primary key (consensus_timestamp, account_id, token_id);

-- token_transfer
create index if not exists token_transfer__token_account_timestamp
    on token_transfer (consensus_timestamp desc, token_id desc, account_id desc);

-- topic_message
alter table if exists topic_message
    add primary key (consensus_timestamp);
create index if not exists topic_message__realm_num_timestamp
    on topic_message (realm_num, topic_num, consensus_timestamp);
create unique index if not exists topic_message__topic_num_realm_num_seqnum
    on topic_message (realm_num, topic_num, sequence_number, consensus_timestamp);
-- have to add consensus_timestamp when creating unique indexes due to partitioning

-- transaction
alter table if exists transaction
    add primary key (consensus_ns);
create index if not exists transaction__transaction_id
    on transaction (valid_start_ns, payer_account_id);
create index if not exists transaction__payer_account_id
    on transaction (payer_account_id);
