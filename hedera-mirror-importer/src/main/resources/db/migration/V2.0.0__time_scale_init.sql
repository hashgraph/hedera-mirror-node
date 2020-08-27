-------------------
-- Init mirror node db, defining table schema and creating hyper tables
-- Supports mirror nodes migrated from v1.0
-- Use default of 604800000000000 ns (7 days) as chunk time interval
-- add TIMESTAMPTZ data type column to tables where no monotonically increasing id exists
-------------------

-- Extend the database with TimescaleDB
create extension if not exists timescaledb cascade;


-- sequences
--create sequence if not exists s_record_files_seq;

-- account_balance
create table if not exists account_balances (
    consensus_timestamp bigint not null,
    account_id entity_id not null,
    balance hbar_tinybars not null,
    constraint pk__account_balances primary key (consensus_timestamp, account_id)
);
comment on table account_balances is 'account balances (historical) in tinybars at different consensus timestamps';
create index if not exists idx__account_balances__account_then_timestamp
    on account_balances (account_id, consensus_timestamp desc);
select create_hypertable('account_balance', 'consensus_timestamp', chunk_time_interval => 604800000000000, if_not_exists => true);

-- account_balance_sets


-- address_book


-- address_book_entry


-- contract_result


-- crypto_transfer


-- file_data


-- flyway_schema_history


-- live_hash


-- non_fee_transfer


-- record_file
-- id seq from v1.0 no longer explicitly created as s_record_files_seq


-- t_application_status


-- t_entities


-- t_entity_types


-- t_transaction_results


-- t_transaction_types


-- topic_message


-- transaction


-- is it necessary to explicitly grant the following?
--grant usage on SCHEMA :schema_name to :db_user;
--grant connect on database :db_name to :db_user;
--grant all privileges on database :db_name to db_user;
--grant all privileges on all tables in :schema_name public to db_user;
--grant all ON t_record_files to db_user;
