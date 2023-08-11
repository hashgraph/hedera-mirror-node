-- configure session to ensure partitions are created with bounds in UTC
SET
    timezone TO 'UTC';

create
    or replace function updatePartitionTableName(table_name regclass, partition_interval interval,
                                                 from_value timestamptz) returns void as
$$
declare
    partition_name_format varchar;
begin
    IF
        partition_interval = INTERVAL '3 months' THEN
        -- include quarter in partition name
        partition_name_format = 'YYYY"q"Q';
    ELSIF
        partition_interval = INTERVAL '1 week' THEN
        -- include week number in partition name
        partition_name_format := 'IYYY"w"IW';
    ELSE
        -- always start with the year
        partition_name_format := 'YYYY';

        IF
            partition_interval < INTERVAL '1 year' THEN
            -- include month in partition name
            partition_name_format := partition_name_format || '_MM';
        END IF;

        IF
            partition_interval < INTERVAL '1 month' THEN
            -- include day of month in partition name
            partition_name_format := partition_name_format || '_DD';
        END IF;

        IF
            partition_interval < INTERVAL '1 day' THEN
            -- include time of day in partition name
            partition_name_format := partition_name_format || '_HH24MI';
        END IF;

        IF
            partition_interval < INTERVAL '1 minute' THEN
            -- include seconds in time of day in partition name
            partition_name_format := partition_name_format || 'SS';
        END IF;
    END IF;

    EXECUTE format('ALTER TABLE %I rename TO %s', table_name || '_p' || to_char(from_value, partition_name_format), table_name || '_p0');
end
$$
    language plpgsql;

-------------------
-- Add non-repeatable partitioning logic to large tables.
-------------------
select create_time_partitions(table_name :='public.account_balance',
                              partition_interval := INTERVAL ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP - ${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + '1 month');
select create_time_partitions(table_name :='public.assessed_custom_fee',
                              partition_interval := INTERVAL ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP - ${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + '1 month');
select create_time_partitions(table_name :='public.contract', partition_interval := INTERVAL ${partitionIdInterval},
                              start_from := '1970-01-01 00:00:00.000'::timestamptz,
                              end_at := '1970-01-01 00:00:00.000'::timestamptz +
                                        ((INTERVAL ${partitionIdInterval}) * ${partitionIdCount}));
SELECT updatePartitionTableName('public.contract'::regclass, INTERVAL ${partitionIdInterval},
                                '1970-01-01 00:00:00.000'::timestamptz);
select create_time_partitions(table_name :='public.contract_action',
                              partition_interval := INTERVAL ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP - ${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + '1 month');
select create_time_partitions(table_name :='public.contract_log',
                              partition_interval := INTERVAL ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP - ${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + '1 month');
select create_time_partitions(table_name :='public.contract_result',
                              partition_interval := INTERVAL ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP - ${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + '1 month');
select create_time_partitions(table_name :='public.contract_state',
                              partition_interval := INTERVAL ${partitionIdInterval},
                              start_from := '1970-01-01 00:00:00.000'::timestamptz,
                              end_at := '1970-01-01 00:00:00.000'::timestamptz +
                                        ((INTERVAL ${partitionIdInterval}) * ${partitionIdCount}));
SELECT updatePartitionTableName('public.contract_state'::regclass, INTERVAL ${partitionIdInterval},
                                '1970-01-01 00:00:00.000'::timestamptz);
select create_time_partitions(table_name :='public.contract_state_change',
                              partition_interval := INTERVAL ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP - ${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + '1 month');
select create_time_partitions(table_name :='public.crypto_allowance',
                              partition_interval := INTERVAL ${partitionIdInterval},
                              start_from := '1970-01-01 00:00:00.000'::timestamptz,
                              end_at := '1970-01-01 00:00:00.000'::timestamptz +
                                        ((INTERVAL ${partitionIdInterval}) * ${partitionIdCount}));
SELECT updatePartitionTableName('public.crypto_allowance'::regclass, INTERVAL ${partitionIdInterval},
                                '1970-01-01 00:00:00.000'::timestamptz);
select create_time_partitions(table_name :='public.crypto_allowance_history',
                              partition_interval := INTERVAL ${partitionIdInterval},
                              start_from := '1970-01-01 00:00:00.000'::timestamptz,
                              end_at := '1970-01-01 00:00:00.000'::timestamptz +
                                        ((INTERVAL ${partitionIdInterval}) * ${partitionIdCount}));
SELECT updatePartitionTableName('public.crypto_allowance_history'::regclass, INTERVAL ${partitionIdInterval},
                                '1970-01-01 00:00:00.000'::timestamptz);
select create_time_partitions(table_name :='public.crypto_transfer',
                              partition_interval := INTERVAL ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP - ${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + '1 month');
select create_time_partitions(table_name :='public.custom_fee', partition_interval := INTERVAL ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP - ${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + '1 month');
select create_time_partitions(table_name :='public.entity', partition_interval := INTERVAL ${partitionIdInterval},
                              start_from := '1970-01-01 00:00:00.000'::timestamptz,
                              end_at := '1970-01-01 00:00:00.000'::timestamptz +
                                        ((INTERVAL ${partitionIdInterval}) * ${partitionIdCount}));
SELECT updatePartitionTableName('public.entity'::regclass, INTERVAL ${partitionIdInterval},
                                '1970-01-01 00:00:00.000'::timestamptz);
select create_time_partitions(table_name :='public.entity_history',
                              partition_interval := INTERVAL ${partitionIdInterval},
                              start_from := '1970-01-01 00:00:00.000'::timestamptz,
                              end_at := '1970-01-01 00:00:00.000'::timestamptz +
                                        ((INTERVAL ${partitionIdInterval}) * ${partitionIdCount}));
SELECT updatePartitionTableName('public.entity_history'::regclass, INTERVAL ${partitionIdInterval},
                                '1970-01-01 00:00:00.000'::timestamptz);
select create_time_partitions(table_name :='public.entity_stake', partition_interval := INTERVAL ${partitionIdInterval},
                              start_from := '1970-01-01 00:00:00.000'::timestamptz,
                              end_at := '1970-01-01 00:00:00.000'::timestamptz +
                                        ((INTERVAL ${partitionIdInterval}) * ${partitionIdCount}));
SELECT updatePartitionTableName('public.entity_stake'::regclass, INTERVAL ${partitionIdInterval},
                                '1970-01-01 00:00:00.000'::timestamptz);
select create_time_partitions(table_name :='public.entity_stake_history',
                              partition_interval := INTERVAL ${partitionIdInterval},
                              start_from := '1970-01-01 00:00:00.000'::timestamptz,
                              end_at := '1970-01-01 00:00:00.000'::timestamptz +
                                        ((INTERVAL ${partitionIdInterval}) * ${partitionIdCount}));
SELECT updatePartitionTableName('public.entity_stake_history'::regclass, INTERVAL ${partitionIdInterval},
                                '1970-01-01 00:00:00.000'::timestamptz);
select create_time_partitions(table_name :='public.entity_transaction',
                              partition_interval := INTERVAL ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP - ${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + '1 month');
select create_time_partitions(table_name :='public.ethereum_transaction',
                              partition_interval := INTERVAL ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP - ${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + '1 month');
select create_time_partitions(table_name :='public.event_file', partition_interval := INTERVAL ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP - ${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + '1 month');
select create_time_partitions(table_name :='public.file_data', partition_interval := INTERVAL ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP - ${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + '1 month');
select create_time_partitions(table_name :='public.network_freeze',
                              partition_interval := INTERVAL ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP - ${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + '1 month');
select create_time_partitions(table_name :='public.nft', partition_interval := INTERVAL ${partitionIdInterval},
                              start_from := '1970-01-01 00:00:00.000'::timestamptz,
                              end_at := '1970-01-01 00:00:00.000'::timestamptz +
                                        ((INTERVAL ${partitionIdInterval}) * ${partitionIdCount}));
SELECT updatePartitionTableName('public.nft'::regclass, INTERVAL ${partitionIdInterval},
                                '1970-01-01 00:00:00.000'::timestamptz);
select create_time_partitions(table_name :='public.nft_history', partition_interval := INTERVAL ${partitionIdInterval},
                              start_from := '1970-01-01 00:00:00.000'::timestamptz,
                              end_at := '1970-01-01 00:00:00.000'::timestamptz +
                                        ((INTERVAL ${partitionIdInterval}) * ${partitionIdCount}));
SELECT updatePartitionTableName('public.nft_history'::regclass, INTERVAL ${partitionIdInterval},
                                '1970-01-01 00:00:00.000'::timestamptz);
select create_time_partitions(table_name :='public.nft_allowance',
                              partition_interval := INTERVAL ${partitionIdInterval},
                              start_from := '1970-01-01 00:00:00.000'::timestamptz,
                              end_at := '1970-01-01 00:00:00.000'::timestamptz +
                                        ((INTERVAL ${partitionIdInterval}) * ${partitionIdCount}));
SELECT updatePartitionTableName('public.nft_allowance'::regclass, INTERVAL ${partitionIdInterval},
                                '1970-01-01 00:00:00.000'::timestamptz);
select create_time_partitions(table_name :='public.nft_allowance_history',
                              partition_interval := INTERVAL ${partitionIdInterval},
                              start_from := '1970-01-01 00:00:00.000'::timestamptz,
                              end_at := '1970-01-01 00:00:00.000'::timestamptz +
                                        ((INTERVAL ${partitionIdInterval}) * ${partitionIdCount}));
SELECT updatePartitionTableName('public.nft_allowance_history'::regclass, INTERVAL ${partitionIdInterval},
                                '1970-01-01 00:00:00.000'::timestamptz);
select create_time_partitions(table_name :='public.record_file',
                              partition_interval := INTERVAL ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP - ${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + '1 month');
select create_time_partitions(table_name :='public.schedule', partition_interval := INTERVAL ${partitionIdInterval},
                              start_from := '1970-01-01 00:00:00.000'::timestamptz,
                              end_at := '1970-01-01 00:00:00.000'::timestamptz +
                                        ((INTERVAL ${partitionIdInterval}) * ${partitionIdCount}));
SELECT updatePartitionTableName('public.schedule'::regclass, INTERVAL ${partitionIdInterval},
                                '1970-01-01 00:00:00.000'::timestamptz);
select create_time_partitions(table_name :='public.staking_reward_transfer',
                              partition_interval := INTERVAL ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP - ${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + '1 month');
select create_time_partitions(table_name :='public.token'::regclass,
                              partition_interval := INTERVAL ${partitionIdInterval},
                              start_from := '1970-01-01 00:00:00.000'::timestamptz,
                              end_at := '1970-01-01 00:00:00.000'::timestamptz +
                                        ((INTERVAL ${partitionIdInterval}) * ${partitionIdCount}));
SELECT updatePartitionTableName('public.token', INTERVAL ${partitionIdInterval},
                                '1970-01-01 00:00:00.000'::timestamptz);
select create_time_partitions(table_name :='public.token_history',
                              partition_interval := INTERVAL ${partitionIdInterval},
                              start_from := '1970-01-01 00:00:00.000'::timestamptz,
                              end_at := '1970-01-01 00:00:00.000'::timestamptz +
                                        ((INTERVAL ${partitionIdInterval}) * ${partitionIdCount}));
SELECT updatePartitionTableName('public.token_history'::regclass, INTERVAL ${partitionIdInterval},
                                '1970-01-01 00:00:00.000'::timestamptz);
select create_time_partitions(table_name :='public.token_account',
                              partition_interval := INTERVAL ${partitionIdInterval},
                              start_from := '1970-01-01 00:00:00.000'::timestamptz,
                              end_at := '1970-01-01 00:00:00.000'::timestamptz +
                                        ((INTERVAL ${partitionIdInterval}) * ${partitionIdCount}));
SELECT updatePartitionTableName('public.token_account'::regclass, INTERVAL ${partitionIdInterval},
                                '1970-01-01 00:00:00.000'::timestamptz);
select create_time_partitions(table_name :='public.token_account_history',
                              partition_interval := INTERVAL ${partitionIdInterval},
                              start_from := '1970-01-01 00:00:00.000'::timestamptz,
                              end_at := '1970-01-01 00:00:00.000'::timestamptz +
                                        ((INTERVAL ${partitionIdInterval}) * ${partitionIdCount}));
SELECT updatePartitionTableName('public.token_account_history'::regclass, INTERVAL ${partitionIdInterval},
                                '1970-01-01 00:00:00.000'::timestamptz);
select create_time_partitions(table_name :='public.token_allowance',
                              partition_interval := INTERVAL ${partitionIdInterval},
                              start_from := '1970-01-01 00:00:00.000'::timestamptz,
                              end_at := '1970-01-01 00:00:00.000'::timestamptz +
                                        ((INTERVAL ${partitionIdInterval}) * ${partitionIdCount}));
SELECT updatePartitionTableName('public.token_allowance'::regclass, INTERVAL ${partitionIdInterval},
                                '1970-01-01 00:00:00.000'::timestamptz);
select create_time_partitions(table_name :='public.token_allowance_history',
                              partition_interval := INTERVAL ${partitionIdInterval},
                              start_from := '1970-01-01 00:00:00.000'::timestamptz,
                              end_at := '1970-01-01 00:00:00.000'::timestamptz +
                                        ((INTERVAL ${partitionIdInterval}) * ${partitionIdCount}));
SELECT updatePartitionTableName('public.token_allowance_history'::regclass, INTERVAL ${partitionIdInterval},
                                '1970-01-01 00:00:00.000'::timestamptz);
select create_time_partitions(table_name :='public.token_balance',
                              partition_interval := INTERVAL ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP - ${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + '1 month');
select create_time_partitions(table_name :='public.token_transfer',
                              partition_interval := INTERVAL ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP - ${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + '1 month');
select create_time_partitions(table_name :='public.topic_message',
                              partition_interval := INTERVAL ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP - ${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + '1 month');
select create_time_partitions(table_name :='public.transaction',
                              partition_interval := INTERVAL ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP - ${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + '1 month');
select create_time_partitions(table_name :='public.transaction_hash',
                              partition_interval := INTERVAL ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP - ${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + '1 month');
select create_time_partitions(table_name :='public.transaction_signature',
                              partition_interval := INTERVAL ${partitionIdInterval},
                              start_from := '1970-01-01 00:00:00.000'::timestamptz,
                              end_at := '1970-01-01 00:00:00.000'::timestamptz +
                                        ((INTERVAL ${partitionIdInterval}) * ${partitionIdCount}));
SELECT updatePartitionTableName('public.transaction_signature'::regclass, INTERVAL ${partitionIdInterval},
                                '1970-01-01 00:00:00.000'::timestamptz);
drop function updatePartitionTableName;
