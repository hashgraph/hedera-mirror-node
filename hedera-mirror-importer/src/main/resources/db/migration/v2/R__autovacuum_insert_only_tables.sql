-- -------------------
-- -- autovacuum insert-only tables more frequently to ensure most pages are visible for index-only scans
-- -------------------
--
create
or replace function apply_vacuum_settings_for_partitioned_tables(parent regclass, vacuum_settings text)
    returns void as
$$
declare
partition_info record;
begin
    for partition_info in
    SELECT tp.partition
    from time_partitions tp
    where tp.parent_table = parent
    order by tp.from_value::bigint desc
    limit 2
    loop
        EXECUTE format(concat('alter table if exists %I set (', vacuum_settings, ')'),
                partition_info.partition);
    end loop;
end;
$$
language plpgsql;


create or replace function apply_vacuum_settings() returns void as
$$
begin
    -- auto vacuum per each account balance snapshot if there are at least 10000 accounts' balance inserted
    perform apply_vacuum_settings_for_partitioned_tables('account_balance'::regclass, 'autovacuum_vacuum_insert_scale_factor=0, autovacuum_vacuum_insert_threshold=10000');
    -- based on 12 contract actions per smart contract transaction and max 300 TPS
    perform apply_vacuum_settings_for_partitioned_tables('contract_action'::regclass, 'autovacuum_vacuum_insert_scale_factor=0, autovacuum_vacuum_insert_threshold=2160000');
    -- base on average of 3 contract logs per smart contract transaction and max 300 TPS
    perform apply_vacuum_settings_for_partitioned_tables('contract_log'::regclass, 'autovacuum_vacuum_insert_scale_factor=0, autovacuum_vacuum_insert_threshold=540000');
    -- max 300 smart contract transactions per second
    perform apply_vacuum_settings_for_partitioned_tables('contract_result'::regclass, 'autovacuum_vacuum_insert_scale_factor=0, autovacuum_vacuum_insert_threshold=180000');
    -- based on average of 10 state changes per smart contract transaction and max 300 TPS
    perform apply_vacuum_settings_for_partitioned_tables('contract_state_change'::regclass, 'autovacuum_vacuum_insert_scale_factor=0, autovacuum_vacuum_insert_threshold=1800000');
    -- autovacuum every 10 minutes at 10K TPS with average of 4 crypto transfers per transaction
    perform apply_vacuum_settings_for_partitioned_tables('crypto_transfer'::regclass, 'autovacuum_vacuum_insert_scale_factor=0, autovacuum_vacuum_insert_threshold=24000000');
    -- adjust when ethereum transaction TPS becomes higher
    perform apply_vacuum_settings_for_partitioned_tables('ethereum_transaction'::regclass, 'autovacuum_vacuum_insert_scale_factor=0.1');
    -- autovacuum every 60 minutes with 2s interval record files
    perform apply_vacuum_settings_for_partitioned_tables('record_file'::regclass, 'autovacuum_vacuum_insert_scale_factor=0, autovacuum_vacuum_insert_threshold=1800');
    -- threshold set based on recent stats so the table has about 2 to 3 automatic vacuums per day, adjust if there's more
    -- daily staking reward claims
    perform apply_vacuum_settings_for_partitioned_tables('staking_reward_transfer'::regclass, 'autovacuum_vacuum_insert_scale_factor=0, autovacuum_vacuum_insert_threshold=4000');
    -- autovacuum per each token balance snapshot if there are at least 10000 account token balance inserted
    perform apply_vacuum_settings_for_partitioned_tables('token_balance'::regclass, 'autovacuum_vacuum_insert_scale_factor=0, autovacuum_vacuum_insert_threshold=10000');
    -- autovacuum every 10 minutes at 10K TPS crypto transfer transactions with two token transfer rows per transaction
    perform apply_vacuum_settings_for_partitioned_tables('token_transfer'::regclass, 'autovacuum_vacuum_insert_scale_factor=0, autovacuum_vacuum_insert_threshold=12000000');
    -- autovacuum every 10 minutes at 10K TPS consensus submit message transactions
    perform apply_vacuum_settings_for_partitioned_tables('topic_message'::regclass, 'autovacuum_vacuum_insert_scale_factor=0, autovacuum_vacuum_insert_threshold=6000000');
    -- autovacuum every 10 minutes at 10K TPS
    perform apply_vacuum_settings_for_partitioned_tables('transaction'::regclass, 'autovacuum_vacuum_insert_scale_factor=0, autovacuum_vacuum_insert_threshold=6000000');
end;
$$
language plpgsql;

select apply_vacuum_settings_for_partitioned_tables();

-- Vacuum settings for non-partitioned tables

-- auto vacuum daily with 15-minute interval account balance files
alter table if exists account_balance_file set (
    autovacuum_vacuum_insert_scale_factor = 0,
    autovacuum_vacuum_insert_threshold = 96
    );

-- autovacuum every 60 minutes with 2s interval record files. Note normally there is just one sidecar record
-- per each record file
alter table if exists sidecar_file set (
    autovacuum_vacuum_insert_scale_factor = 0,
    autovacuum_vacuum_insert_threshold = 1800
    );
