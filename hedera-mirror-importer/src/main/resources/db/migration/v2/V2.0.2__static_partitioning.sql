-- configure session to ensure partitions are created with bounds in UTC
SET timezone TO 'UTC';

create
    or replace function update_partition_table_name(table_name regclass, partition_interval interval,
                                                    from_value timestamptz) returns void as
$$
declare
    partition_name_format varchar;
    partitionNumber       int;
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
    select (extract(epoch from (from_value)) /
            extract(epoch from (partition_interval)))::int
    into partitionNumber;

    EXECUTE format('ALTER TABLE %I rename TO %s', table_name || '_p' || to_char(from_value, partition_name_format),
                   table_name || '_p' || partitionNumber);
end
$$
    language plpgsql;

create or replace function mirror_node_create_partitions() returns void as
$$
declare
    partitionInfo      record;
    createdPartition   boolean;
    timePartitionRegex varchar = '^(.*_timestamp|consensus_end)$';
begin
    for partitionInfo in
        SELECT tp.parent_table,
               tp.partition_column,
               to_timestamp(max(tp.to_value::bigint / 1000000000.0)) as next_from,
               case
                   when tp.partition_column::varchar ~ timePartitionRegex
                       then to_timestamp(max(tp.to_value::bigint / 1000000000.0)) + ${partitionTimeInterval}
                   else to_timestamp(max(tp.to_value::bigint / 1000000000.0)) + ${partitionIdInterval}
                   end                                               as next_to,
               (tp.partition_column::varchar ~ timePartitionRegex)   as time_partition,
               (select COALESCE(max(id), 1) from entity)             as max_entity_id
        from time_partitions tp
        group by tp.parent_table, tp.partition_column
        loop
            if (not partitionInfo.time_partition and
                partitionInfo.max_entity_id * 2.0 < (extract(epoch from (partitionInfo.next_from))) * 1000000000.0) or
               (partitionInfo.time_partition and
                (CURRENT_TIMESTAMP + ${partitionTimeInterval} < partitionInfo.next_from))
            then
                raise info 'Skipping partition creation for %', partitionInfo.parent_table;
                continue;
            end if;
            if partitionInfo.time_partition then
                select create_time_partitions(table_name := partitionInfo.parent_table,
                                              partition_interval := ${partitionTimeInterval},
                                              start_from := partitionInfo.next_from::timestamptz,
                                              end_at := partitionInfo.next_to::timestamptz)
                into createdPartition;
            else
                select create_time_partitions(table_name := partitionInfo.parent_table,
                                              partition_interval := ${partitionIdInterval},
                                              start_from := partitionInfo.next_from::timestamptz,
                                              end_at := partitionInfo.next_to::timestamptz)
                into createdPartition;

                PERFORM update_partition_table_name(partitionInfo.parent_table, ${partitionIdInterval},
                                                    partitionInfo.next_from::timestamptz);
            end if;
            raise info 'Processed % created %', partitionInfo.parent_table, createdPartition;
        end loop;
end;
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
                                        ((INTERVAL ${partitionIdInterval})));
SELECT update_partition_table_name('public.contract'::regclass, INTERVAL ${partitionIdInterval},
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
                                        ((INTERVAL ${partitionIdInterval})));
SELECT update_partition_table_name('public.contract_state'::regclass, INTERVAL ${partitionIdInterval},
                                   '1970-01-01 00:00:00.000'::timestamptz);
select create_time_partitions(table_name :='public.contract_state_change',
                              partition_interval := INTERVAL ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP - ${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + '1 month');
select create_time_partitions(table_name :='public.crypto_allowance',
                              partition_interval := INTERVAL ${partitionIdInterval},
                              start_from := '1970-01-01 00:00:00.000'::timestamptz,
                              end_at := '1970-01-01 00:00:00.000'::timestamptz +
                                        ((INTERVAL ${partitionIdInterval})));
SELECT update_partition_table_name('public.crypto_allowance'::regclass, INTERVAL ${partitionIdInterval},
                                   '1970-01-01 00:00:00.000'::timestamptz);
select create_time_partitions(table_name :='public.crypto_allowance_history',
                              partition_interval := INTERVAL ${partitionIdInterval},
                              start_from := '1970-01-01 00:00:00.000'::timestamptz,
                              end_at := '1970-01-01 00:00:00.000'::timestamptz +
                                        ((INTERVAL ${partitionIdInterval})));
SELECT update_partition_table_name('public.crypto_allowance_history'::regclass, INTERVAL ${partitionIdInterval},
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
                                        ((INTERVAL ${partitionIdInterval})));
SELECT update_partition_table_name('public.entity'::regclass, INTERVAL ${partitionIdInterval},
                                   '1970-01-01 00:00:00.000'::timestamptz);
select create_time_partitions(table_name :='public.entity_history',
                              partition_interval := INTERVAL ${partitionIdInterval},
                              start_from := '1970-01-01 00:00:00.000'::timestamptz,
                              end_at := '1970-01-01 00:00:00.000'::timestamptz +
                                        ((INTERVAL ${partitionIdInterval})));
SELECT update_partition_table_name('public.entity_history'::regclass, INTERVAL ${partitionIdInterval},
                                   '1970-01-01 00:00:00.000'::timestamptz);
select create_time_partitions(table_name :='public.entity_stake', partition_interval := INTERVAL ${partitionIdInterval},
                              start_from := '1970-01-01 00:00:00.000'::timestamptz,
                              end_at := '1970-01-01 00:00:00.000'::timestamptz +
                                        ((INTERVAL ${partitionIdInterval})));
SELECT update_partition_table_name('public.entity_stake'::regclass, INTERVAL ${partitionIdInterval},
                                   '1970-01-01 00:00:00.000'::timestamptz);
select create_time_partitions(table_name :='public.entity_stake_history',
                              partition_interval := INTERVAL ${partitionIdInterval},
                              start_from := '1970-01-01 00:00:00.000'::timestamptz,
                              end_at := '1970-01-01 00:00:00.000'::timestamptz +
                                        ((INTERVAL ${partitionIdInterval})));
SELECT update_partition_table_name('public.entity_stake_history'::regclass, INTERVAL ${partitionIdInterval},
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
                                        ((INTERVAL ${partitionIdInterval})));
SELECT update_partition_table_name('public.nft'::regclass, INTERVAL ${partitionIdInterval},
                                   '1970-01-01 00:00:00.000'::timestamptz);
select create_time_partitions(table_name :='public.nft_history', partition_interval := INTERVAL ${partitionIdInterval},
                              start_from := '1970-01-01 00:00:00.000'::timestamptz,
                              end_at := '1970-01-01 00:00:00.000'::timestamptz +
                                        ((INTERVAL ${partitionIdInterval})));
SELECT update_partition_table_name('public.nft_history'::regclass, INTERVAL ${partitionIdInterval},
                                   '1970-01-01 00:00:00.000'::timestamptz);
select create_time_partitions(table_name :='public.nft_allowance',
                              partition_interval := INTERVAL ${partitionIdInterval},
                              start_from := '1970-01-01 00:00:00.000'::timestamptz,
                              end_at := '1970-01-01 00:00:00.000'::timestamptz +
                                        ((INTERVAL ${partitionIdInterval})));
SELECT update_partition_table_name('public.nft_allowance'::regclass, INTERVAL ${partitionIdInterval},
                                   '1970-01-01 00:00:00.000'::timestamptz);
select create_time_partitions(table_name :='public.nft_allowance_history',
                              partition_interval := INTERVAL ${partitionIdInterval},
                              start_from := '1970-01-01 00:00:00.000'::timestamptz,
                              end_at := '1970-01-01 00:00:00.000'::timestamptz +
                                        ((INTERVAL ${partitionIdInterval})));
SELECT update_partition_table_name('public.nft_allowance_history'::regclass, INTERVAL ${partitionIdInterval},
                                   '1970-01-01 00:00:00.000'::timestamptz);
select create_time_partitions(table_name :='public.record_file',
                              partition_interval := INTERVAL ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP - ${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + '1 month');
select create_time_partitions(table_name :='public.schedule', partition_interval := INTERVAL ${partitionIdInterval},
                              start_from := '1970-01-01 00:00:00.000'::timestamptz,
                              end_at := '1970-01-01 00:00:00.000'::timestamptz +
                                        ((INTERVAL ${partitionIdInterval})));
SELECT update_partition_table_name('public.schedule'::regclass, INTERVAL ${partitionIdInterval},
                                   '1970-01-01 00:00:00.000'::timestamptz);
select create_time_partitions(table_name :='public.staking_reward_transfer',
                              partition_interval := INTERVAL ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP - ${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + '1 month');
select create_time_partitions(table_name :='public.token'::regclass,
                              partition_interval := INTERVAL ${partitionIdInterval},
                              start_from := '1970-01-01 00:00:00.000'::timestamptz,
                              end_at := '1970-01-01 00:00:00.000'::timestamptz +
                                        ((INTERVAL ${partitionIdInterval})));
SELECT update_partition_table_name('public.token', INTERVAL ${partitionIdInterval},
                                   '1970-01-01 00:00:00.000'::timestamptz);
select create_time_partitions(table_name :='public.token_history',
                              partition_interval := INTERVAL ${partitionIdInterval},
                              start_from := '1970-01-01 00:00:00.000'::timestamptz,
                              end_at := '1970-01-01 00:00:00.000'::timestamptz +
                                        ((INTERVAL ${partitionIdInterval})));
SELECT update_partition_table_name('public.token_history'::regclass, INTERVAL ${partitionIdInterval},
                                   '1970-01-01 00:00:00.000'::timestamptz);
select create_time_partitions(table_name :='public.token_account',
                              partition_interval := INTERVAL ${partitionIdInterval},
                              start_from := '1970-01-01 00:00:00.000'::timestamptz,
                              end_at := '1970-01-01 00:00:00.000'::timestamptz +
                                        ((INTERVAL ${partitionIdInterval})));
SELECT update_partition_table_name('public.token_account'::regclass, INTERVAL ${partitionIdInterval},
                                   '1970-01-01 00:00:00.000'::timestamptz);
select create_time_partitions(table_name :='public.token_account_history',
                              partition_interval := INTERVAL ${partitionIdInterval},
                              start_from := '1970-01-01 00:00:00.000'::timestamptz,
                              end_at := '1970-01-01 00:00:00.000'::timestamptz +
                                        ((INTERVAL ${partitionIdInterval})));
SELECT update_partition_table_name('public.token_account_history'::regclass, INTERVAL ${partitionIdInterval},
                                   '1970-01-01 00:00:00.000'::timestamptz);
select create_time_partitions(table_name :='public.token_allowance',
                              partition_interval := INTERVAL ${partitionIdInterval},
                              start_from := '1970-01-01 00:00:00.000'::timestamptz,
                              end_at := '1970-01-01 00:00:00.000'::timestamptz +
                                        ((INTERVAL ${partitionIdInterval})));
SELECT update_partition_table_name('public.token_allowance'::regclass, INTERVAL ${partitionIdInterval},
                                   '1970-01-01 00:00:00.000'::timestamptz);
select create_time_partitions(table_name :='public.token_allowance_history',
                              partition_interval := INTERVAL ${partitionIdInterval},
                              start_from := '1970-01-01 00:00:00.000'::timestamptz,
                              end_at := '1970-01-01 00:00:00.000'::timestamptz +
                                        ((INTERVAL ${partitionIdInterval})));
SELECT update_partition_table_name('public.token_allowance_history'::regclass, INTERVAL ${partitionIdInterval},
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
                                        ((INTERVAL ${partitionIdInterval})));
SELECT update_partition_table_name('public.transaction_signature'::regclass, INTERVAL ${partitionIdInterval},
                                   '1970-01-01 00:00:00.000'::timestamptz);
