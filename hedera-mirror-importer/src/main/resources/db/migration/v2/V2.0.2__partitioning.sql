-------------------
-- Add partitioning logic to large tables.
-- Our tables use integers representing time from epoch. This requires extra logic to support automated partitioning.
-- Automatic options would rely on pg_partman, citus UDFs or cron jobs
-- Nanoseconds are not supported at time of addition
-- As an interim solution specify ranges manually with support from 0 epoch timestamps and ids
-------------------

-- id partitioning helper function
drop function if exists add_entity_partitions(text, text);
create function add_entity_partitions(partName text, tableName text)
    returns void as
$$
begin
    execute format(
            'create table if not exists %I_0 partition of %I for values from (0) to (10000000)',
            partName, tableName
        );
    execute format(
            'create table if not exists %I_1 partition of %I for values from (10000000) to (20000000)',
            partName, tableName
        );
    execute format(
            'create table if not exists %I_2 partition of %I for values from (20000000) to (30000000)',
            partName, tableName
        );
    execute format(
            'create table if not exists %I_3 partition of %I for values from (30000000) to (99999999999)',
            partName, tableName
        );
end
$$ language plpgsql;

-- timestamp partitioning helper function
-- endOf2018Ns: 1546300799000000000, endOf2019Ns: 1577836799000000000, endOf2020Ns: 1609459199000000000,
-- endOf2021Ns: 1640995199000000000, endOf2022Ns: 1672531199000000000, endOf2023Ns: 1704067199000000000
drop function if exists add_time_partitions(text, text, int);
create function add_time_partitions(partName text, tableName text, autovacuumVacuumInsertThreshold int)
    returns void as
$$
begin
    -- create partitions
    execute format(
            'create table if not exists %I_0_2019 partition of %I for values from (0) to (1577836799000000000)',
            partName, tableName
        );
    execute format(
            'create table if not exists %I_0_2020 partition of %I for values from (1577836799000000000) to (1609459199000000000)',
            partName, tableName
        );
    execute format(
            'create table if not exists %I_0_2021 partition of %I for values from (1609459199000000000) to (1640995199000000000)',
            partName, tableName
        );
    execute format(
            'create table if not exists %I_0_2022 partition of %I for values from (1640995199000000000) to (1672531199000000000)',
            partName, tableName
        );
    execute format(
            'create table if not exists %I_0_2023 partition of %I for values from (1672531199000000000) to (1704067199000000000)',
            partName, tableName
        );

    if autovacuumVacuumInsertThreshold > 0 then
        -- autovacuum insert-only tables more frequently to ensure most pages are visible for index-only scans
        -- set autovacuum at time of partitioning creation since it can not be set on parent table
        execute format(
                'alter table if exists %I_0_2019
                set (
                    autovacuum_vacuum_insert_scale_factor = 0,
                    autovacuum_vacuum_insert_threshold = %I,
                    log_autovacuum_min_duration = 0
                )',
                partName, autovacuumVacuumInsertThreshold
            );

        execute format(
                'alter table if exists %I_0_2020
                set (
                    autovacuum_vacuum_insert_scale_factor = 0,
                    autovacuum_vacuum_insert_threshold = %I,
                    log_autovacuum_min_duration = 0
                )',
                partName, autovacuumVacuumInsertThreshold
            );

        execute format(
                'alter table if exists %I_0_2021
                set (
                    autovacuum_vacuum_insert_scale_factor = 0,
                    autovacuum_vacuum_insert_threshold = %I,
                    log_autovacuum_min_duration = 0
                )',
                partName, autovacuumVacuumInsertThreshold
            );

        execute format(
                'alter table if exists %I_0_2022
                set (
                    autovacuum_vacuum_insert_scale_factor = 0,
                    autovacuum_vacuum_insert_threshold = %I,
                    log_autovacuum_min_duration = 0
                )',
                partName, autovacuumVacuumInsertThreshold
            );

        execute format(
                'alter table if exists %I_0_2023
                set (
                    autovacuum_vacuum_insert_scale_factor = 0,
                    autovacuum_vacuum_insert_threshold = %I,
                    log_autovacuum_min_duration = 0
                )',
                partName, autovacuumVacuumInsertThreshold
            );
    end if;
end
$$ language plpgsql;


-- id (space) based partitioning for tables with unpredictable population
select add_entity_partitions('contract', 'contract');
select add_entity_partitions('contract_h', 'contract_history');
select add_entity_partitions('entity', 'entity');
select add_entity_partitions('entity_h', 'entity_history');
select add_entity_partitions('schedule', 'schedule');
select add_entity_partitions('token', 'token');

-- consensus timestamp based partitioning on tables regularly populated
select add_time_partitions('acc_bal', 'account_balance', 0);
select add_time_partitions('contract_l', 'contract_log', 0);
select add_time_partitions('contract_r', 'contract_result', 0);
select add_time_partitions('crypto_tr', 'crypto_transfer', ${autovacuumVacuumInsertThresholdCryptoTransfer});
select add_time_partitions('nft_tr', 'nft_transfer', 0);
select add_time_partitions('n_f_tr', 'non_fee_transfer', 0);
select add_time_partitions('rec_file', 'record_file', 0);
select add_time_partitions('tok_bal', 'token_balance', 0);
select add_time_partitions('tok_tr', 'token_transfer', ${autovacuumVacuumInsertThresholdTokenTransfer});
select add_time_partitions('topic_mess', 'topic_message', 0);
select add_time_partitions('tx', 'transaction', ${autovacuumVacuumInsertThresholdTransaction});
select add_time_partitions('tx_sig', 'transaction_signature', 0);

-- drop functions
drop function if exists add_entity_partitions(text, text);
drop function if exists add_time_partitions(text, text, int);
