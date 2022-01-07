-------------------
-- Add constraints and indexes to tables.
-------------------

-- use sequential, avoid error "The index name ... on a shard is too long and could lead to deadlocks when executed ..."
set local citus.multi_shard_modify_mode to 'sequential';

-- assessed_custom_fee
create index if not exists assessed_custom_fee__consensus_timestamp
    on assessed_custom_fee (consensus_timestamp);

-- account_balance
alter table account_balance
    add constraint account_balance__pk primary key (consensus_timestamp, account_id);

-- account_balance_file
alter table account_balance_file
    add constraint account_balance_file__pk primary key (consensus_timestamp);
create unique index if not exists account_balance_file__name
    on account_balance_file (name);

-- address_book
alter table address_book
    add constraint address_book__pk primary key (start_consensus_timestamp);

-- address_book_entry
alter table address_book_entry
    add constraint address_book_entry__pk primary key (consensus_timestamp, node_id);

-- address_book_service_endpoint
alter table address_book_service_endpoint
    add constraint address_book_service_endpoint__pk primary key (consensus_timestamp, node_id, ip_address_v4, port);

-- contract
alter table if exists contract
    add constraint contract__pk primary key (id);
alter table if exists contract
    add constraint contract__type_check
        check (type = 'CONTRACT');
create index if not exists contract__public_key on contract (public_key) where public_key is not null;

-- contract_history
alter table if exists contract_history
    add constraint contract_history__pk primary key (id, timestamp_range);
alter table if exists contract_history
    add constraint contract_history__type_check
        check (type = 'CONTRACT');
create index if not exists contract_history__timestamp_range on contract_history using gist (timestamp_range);

-- contract_log
alter table if exists contract_log
    add constraint contract_log__pk primary key (consensus_timestamp, index, payer_account_id);
create index if not exists contract_log__contract_id_timestamp_index
    on contract_log (contract_id, consensus_timestamp desc, index);

-- contract_result
alter table if exists contract_result
    add constraint contract_result__pk primary key (consensus_timestamp, payer_account_id);

create index if not exists contract_result__id_payer_timestamp
    on contract_result (contract_id, payer_account_id, consensus_timestamp);

-- crypto_transfer
create index if not exists crypto_transfer__consensus_timestamp
    on crypto_transfer (consensus_timestamp);
create index if not exists crypto_transfer__entity_id_consensus_timestamp
    on crypto_transfer (entity_id, consensus_timestamp)
    where entity_id != 98;
-- id corresponding to treasury address 0.0.98

-- custom_fee
create index if not exists custom_fee__token_timestamp
    on custom_fee (token_id desc, created_timestamp desc);

-- entity
alter table entity
    add constraint entity__pk primary key (id);
alter table if exists entity
    add constraint entity__type_check
        check (type <> 'CONTRACT');
create index if not exists entity__id_type
    on entity (id, type);
create index if not exists entity__public_key_type
    on entity (public_key, type) where public_key is not null;
create index if not exists entity__alias
    on entity (alias) where alias is not null;

-- entity_history
alter table if exists entity_history
    add constraint entity_history__pk primary key (id, timestamp_range);
alter table if exists entity_history
    add constraint entity_history__type_check
        check (type <> 'CONTRACT');
create index if not exists entity_history__timestamp_range on entity_history using gist (timestamp_range);

-- event_file
alter table event_file
    add constraint event_file__pk primary key (consensus_end, node_account_id);
create index if not exists event_file__hash
    on event_file (hash);

-- file_data
alter table file_data
    add constraint file_data__pk primary key (consensus_timestamp, entity_id);
create index if not exists file_data__id_timestamp
    on file_data (entity_id, consensus_timestamp);

-- live_hash
alter table live_hash
    add constraint live_hash__pk primary key (consensus_timestamp);

-- nft
alter table nft
    add constraint nft__pk primary key (token_id, serial_number);
create index if not exists nft__account_token on nft (account_id, token_id);

-- nft_transfer
create index if not exists nft_transfer__timestamp on nft_transfer (consensus_timestamp desc);
create unique index if not exists nft_transfer__token_id_serial_num_timestamp
    on nft_transfer(token_id desc, serial_number desc, consensus_timestamp desc);

-- non_fee_transfer
create index if not exists non_fee_transfer__consensus_timestamp
    on non_fee_transfer (consensus_timestamp);

-- record_file
alter table record_file
    add constraint record_file__pk primary key (consensus_end, node_account_id);
create index if not exists record_file__index_node
    on record_file (index);
create index if not exists record_file__hash_node
    on record_file (hash);
create index if not exists record_file__prev_hash
    on record_file (prev_hash);

-- schedule
alter table schedule
    add constraint schedule__pk primary key (schedule_id);
create index if not exists schedule__creator_account_id
    on schedule (creator_account_id desc);

-- token
alter table token
    add constraint token__pk primary key (token_id);

-- token_account
alter table token_account
    add constraint token_account__pk primary key (account_id, token_id, modified_timestamp);

-- token_balance
alter table token_balance
    add constraint token_balance__pk primary key (consensus_timestamp, account_id, token_id);
create index if not exists token_balance__timestamp_token
    on token_balance (consensus_timestamp desc, token_id);

-- token_transfer
create index if not exists token_transfer__token_account_timestamp
    on token_transfer (consensus_timestamp desc, token_id desc, account_id desc);
create index if not exists token_transfer__account_timestamp
    on token_transfer (account_id, consensus_timestamp desc);

-- topic_message
alter table if exists topic_message
    add constraint topic_message__pk primary key (consensus_timestamp, topic_id);
create index if not exists topic_message__topic_id_timestamp
    on topic_message (topic_id, consensus_timestamp);
create unique index if not exists topic_message__topic_id_seqnum
    on topic_message (topic_id, sequence_number);

-- transaction
alter table if exists transaction
    add constraint transaction__pk primary key (consensus_timestamp, payer_account_id);
create index if not exists transaction__transaction_id
    on transaction (valid_start_ns, payer_account_id);
create index if not exists transaction__payer_account_id
    on transaction (payer_account_id);
create index if not exists transaction_type
    on transaction (type);

-- transaction_signature
create index if not exists transaction_signature__entity_id
    on transaction_signature (entity_id desc, consensus_timestamp desc);
create index if not exists transaction_signature__timestamp_public_key_prefix
    on transaction_signature (consensus_timestamp desc, public_key_prefix);

-- revert to default
set local citus.multi_shard_modify_mode to 'parallel';
