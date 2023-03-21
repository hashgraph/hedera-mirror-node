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
    add constraint account_balance_file__pk primary key (consensus_timestamp, node_id);

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

-- contract_action
alter table if exists contract_action
    add constraint contract_action__pk primary key (consensus_timestamp, index, payer_account_id);

-- contract_log
alter table if exists contract_log
    add constraint contract_log__pk primary key (consensus_timestamp, index, contract_id);
create index if not exists contract_log__contract_id_timestamp_index
    on contract_log (contract_id, consensus_timestamp desc, index);

-- contract_result
alter table if exists contract_result
    add constraint contract_result__pk primary key (consensus_timestamp, contract_id);

create index if not exists contract_result__hash
    on contract_result using hash (transaction_hash);

create index if not exists contract_result__id_payer_timestamp
    on contract_result (contract_id, payer_account_id, consensus_timestamp);

create index if not exists contract_result__id_timestamp
    on contract_result (contract_id, consensus_timestamp);

create index if not exists contract_result__payer_timestamp
    on contract_result (payer_account_id, consensus_timestamp);

-- contract_state
alter table if exists contract_state
    add constraint contract_state__pk primary key (contract_id, slot);

-- contract_state_change
alter table if exists contract_state_change
    add constraint contract_state_change__pk primary key (consensus_timestamp, contract_id, slot);

-- contract_state_change__id_slot_timestamp
create index if not exists contract_state_change__id_slot_timestamp
    on contract_state_change (contract_id, slot, consensus_timestamp);

-- crypto_allowance
alter table if exists crypto_allowance
    add constraint crypto_allowance__pk primary key (owner, spender);
alter table if exists crypto_allowance_history
    add constraint crypto_allowance_history__pk primary key (owner, spender, timestamp_range);
create index if not exists crypto_allowance_history__timestamp_range on crypto_allowance_history using gist (timestamp_range);

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
create index if not exists entity__id_type
    on entity (id, type);
create index if not exists entity__public_key_type
    on entity (public_key, type) where public_key is not null;
create index if not exists entity__alias
    on entity (alias) where alias is not null;
create index if not exists entity__evm_address on entity (evm_address) where evm_address is not null;

-- entity_history
alter table if exists entity_history
    add constraint entity_history__pk primary key (id, timestamp_range);
create index if not exists entity_history__alias on entity_history (alias) where alias is not null;
create index if not exists entity_history__evm_address on entity_history (evm_address) where evm_address is not null;
create index if not exists entity_history__timestamp_range on entity_history using gist (timestamp_range);

-- entity_stake
alter table if exists entity_stake
    add constraint entity_stake__pk primary key (id);

-- ethereum_transaction
alter table ethereum_transaction
    add constraint ethereum_transaction__pk primary key (consensus_timestamp, payer_account_id);
create index if not exists ethereum_transaction__hash on ethereum_transaction using hash (hash);

-- event_file
alter table event_file
    add constraint event_file__pk primary key (consensus_end, node_id);
create index if not exists event_file__hash
    on event_file using hash (hash);

-- file_data
alter table file_data
    add constraint file_data__pk primary key (consensus_timestamp, entity_id);
create index if not exists file_data__id_timestamp
    on file_data (entity_id, consensus_timestamp);

-- live_hash
alter table live_hash
    add constraint live_hash__pk primary key (consensus_timestamp);

-- network_stake
alter table if exists network_stake
    add constraint network_stake__pk primary key (consensus_timestamp);

-- nft
alter table nft
    add constraint nft__pk primary key (token_id, serial_number);
create index if not exists nft__account_token_serialnumber on nft (account_id, token_id, serial_number);
create index if not exists nft__allowance on nft (account_id, spender, token_id, serial_number)
    where account_id is not null and spender is not null;

-- nft_allowance
alter table if exists nft_allowance
    add constraint nft_allowance__pk primary key (owner, spender, token_id);
alter table if exists nft_allowance_history
    add constraint nft_allowance_history__pk primary key (owner, spender, token_id, timestamp_range);
create index if not exists nft_allowance_history__timestamp_range on nft_allowance_history using gist (timestamp_range);

-- nft_transfer
create index if not exists nft_transfer__timestamp on nft_transfer (consensus_timestamp desc);
create index if not exists nft_transfer__token_id_serial_num_timestamp
    on nft_transfer (token_id desc, serial_number desc, consensus_timestamp desc);

alter table if exists node_stake
    add constraint node_stake__pk primary key (consensus_timestamp, node_id);

-- non_fee_transfer
create index if not exists non_fee_transfer__consensus_timestamp
    on non_fee_transfer (consensus_timestamp);

-- prng
alter table prng
    add constraint prng__pk primary key (consensus_timestamp, payer_account_id);

-- reconciliation_job
alter table reconciliation_job
    add constraint reconciliation_job__pk primary key (timestamp_start);

-- record_file
alter table record_file
    add constraint record_file__pk primary key (consensus_end, node_id);
create index if not exists record_file__index_node
    on record_file (index);
create index if not exists record_file__hash
    on record_file (hash collate "C");

-- schedule
alter table schedule
    add constraint schedule__pk primary key (schedule_id);
create index if not exists schedule__creator_account_id
    on schedule (creator_account_id desc);

-- sidecar_file
alter table sidecar_file
    add constraint sidecar_file__pk primary key (consensus_end, id);

-- staking_reward_transfer
alter table staking_reward_transfer
    add constraint staking_reward_transfer__pk primary key (consensus_timestamp, account_id, payer_account_id);
create index if not exists staking_reward_transfer__account_timestamp
    on staking_reward_transfer (account_id, consensus_timestamp);

-- token
alter table token
    add constraint token__pk primary key (token_id);

-- token_account
alter table token_account
    add constraint token_account__pk primary key (account_id, token_id);
alter table if exists token_account_history
    add constraint token_account_history__pk primary key (account_id, token_id, timestamp_range);

-- token_allowance
alter table if exists token_allowance
    add constraint token_allowance__pk primary key (owner, spender, token_id);
alter table if exists token_allowance_history
    add constraint token_allowance_history__pk primary key (owner, spender, token_id, timestamp_range);
create index if not exists token_allowance_history__timestamp_range on token_allowance_history using gist (timestamp_range);

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
create index if not exists topic_message__topic_id_seqnum
    on topic_message (topic_id, sequence_number);

-- transaction
alter table if exists transaction
    add constraint transaction__pk primary key (consensus_timestamp, payer_account_id);
create index if not exists transaction__transaction_id
    on transaction (valid_start_ns, payer_account_id);
create index if not exists transaction__payer_account_id
    on transaction (payer_account_id);
create index if not exists transaction__type_consensus_timestamp
    on transaction (type, consensus_timestamp);

-- transaction_hash
create index if not exists transaction_hash__hash
    on transaction_hash using hash (hash);

-- transaction_signature
create index if not exists transaction_signature__entity_id
    on transaction_signature (entity_id desc, consensus_timestamp desc);
create index if not exists transaction_signature__timestamp_public_key_prefix
    on transaction_signature (consensus_timestamp desc, public_key_prefix);

-- revert to default
set local citus.multi_shard_modify_mode to 'parallel';
