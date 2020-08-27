-------------------
-- Init mirror node db, defining table schema and creating hyper tables
-- Supports mirror nodes migrated from v1.0
-- Use default of 604800000000000 ns (7 days) as chunk time interval
-- add TIMESTAMPTZ data type column to tables where no monotonically increasing id exists
-------------------

-- account_balance
create index if not exists balances__account_then_timestamp
    on account_balance (account_realm_num desc, account_num desc, consensus_timestamp desc);

-- account_balance_sets
create index if not exists balance_sets__completed
    on account_balance_sets (is_complete, consensus_timestamp desc);

-- address_book


-- address_book_entry
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
    where entity_id != 98; -- id corresponding to treasury address 0.0.98

-- file_data
create index if not exists file_data__consensus
    on file_data (consensus_timestamp desc);

-- flyway_schema_history


-- live_hash
create index if not exists livehashes__consensus
    on live_hash (consensus_timestamp desc);

-- non_fee_transfer
create index if not exists non_fee_transfer__consensus_timestamp
    on non_fee_transfer (consensus_timestamp);

-- record_file
create unique index if not exists record_file_name ON record_file (name, id); -- have to add id due to partitioning
create unique index if not exists record_file_hash ON record_file (file_hash, id); -- have to add id due to partitioning
create index if not exists record_file__consensus_end on record_file (consensus_end);
create index if not exists record_file__prev_hash on record_file (prev_hash);

-- application_status


-- t_entities
create index if not exists entities__ed25519_public_key_hex_natural_id
    on t_entities (ed25519_public_key_hex, fk_entity_type_id, entity_shard, entity_realm, entity_num);
create unique index if not exists entities_unq on t_entities (entity_shard, entity_realm, entity_num, id); -- have to add id due to partitioning

-- entity_types


-- transaction_results


-- transaction_types


-- topic_message
create index if not exists topic_message__realm_num_timestamp
    on topic_message (realm_num, topic_num, consensus_timestamp);
create unique index if not exists topic_message__topic_num_realm_num_seqnum
    on topic_message (realm_num, topic_num, sequence_number, consensus_timestamp); -- have to add consensus_timestamp due to partitioning

-- transaction

select create_hypertable('transaction', 'consensus_ns', chunk_time_interval => 604800000000000, if_not_exists => true);
create index transaction__transaction_id
    on transaction (valid_start_ns, payer_account_id);
create index transaction__payer_account_id
    on transaction (payer_account_id);

-- is it necessary to explicitly grant the following?
--grant usage on SCHEMA :schema_name to :db_user;
--grant connect on database :db_name to :db_user;
--grant all privileges on database :db_name to db_user;
--grant all privileges on all tables in :schema_name public to db_user;
--grant all ON record_file to db_user;
