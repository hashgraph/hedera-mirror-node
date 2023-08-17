-- configure session to ensure partitions are created with bounds in UTC
SET timezone TO 'UTC';

CREATE OR REPLACE FUNCTION get_missing_range_partition_ranges(
    table_name regclass,
    partition_size bigint,
    to_value bigint,
    from_value bigint DEFAULT 0)
returns table(
    partition_name text,
    range_from_value text,
    range_to_value text)
LANGUAGE plpgsql
AS $$
DECLARE
    -- properties of the partitioned table
table_name_text text;
    table_schema_text text;
    number_of_partition_columns int;
    partition_column_index int;
    partition_column_type regtype;

    -- used for generating time ranges
    current_range_from_value bigint := NULL;
    current_range_to_value bigint := NULL;
    current_range_from_value_text text;
    current_range_to_value_text text;
    partition_count_text text;

    -- used to check whether there are misaligned (manually created) partitions
    manual_partition regclass;
    manual_partition_from_value_text text;
    manual_partition_to_value_text text;

    -- used for partition naming
    max_table_name_length int := current_setting('max_identifier_length');

    partition regclass;
    partition_covers_query text;
    partition_exist_query text;
BEGIN
    -- check whether the table is time partitioned table, if not error out
SELECT relname, nspname, partnatts, partattrs[0]
INTO table_name_text, table_schema_text, number_of_partition_columns, partition_column_index
FROM pg_catalog.pg_partitioned_table, pg_catalog.pg_class c, pg_catalog.pg_namespace n
WHERE partrelid = c.oid AND c.oid = table_name
  AND c.relnamespace = n.oid;
IF NOT FOUND THEN
        RAISE '% is not partitioned', table_name;
    ELSIF number_of_partition_columns <> 1 THEN
        RAISE 'partitioned tables with multiple partition columns are not supported';
END IF;

    -- to not to have partitions to be created in parallel
EXECUTE format('LOCK TABLE %I.%I IN SHARE UPDATE EXCLUSIVE MODE', table_schema_text, table_name_text);

-- get datatype here to check interval-table type alignment and generate range values in the right data format
SELECT atttypid
INTO partition_column_type
FROM pg_attribute
WHERE attrelid = table_name::oid
    AND attnum = partition_column_index;

IF partition_column_type <> 'bigint'::regtype
        AND partition_column_type <> 'integer'::regtype
        AND partition_column_type <> 'smallint'::regtype
            THEN
            RAISE 'type of the partition column of the table % must be bigint, integer, or smallint', table_name;
END IF;

    -- If no partition exists, truncate from_value to find intuitive initial value.
    -- If any partition exist, use the initial partition as the pivot partition.
    -- tp.to_value and tp.from_value are equal to '', if default partition exists.
    -- If no partition exists, truncate from_value to find intuitive initial value.
    -- If any partition exist, use the initial partition as the pivot partition.
    -- tp.to_value and tp.from_value are equal to '', if default partition exists.
SELECT tp.from_value::bigint, tp.to_value::bigint
INTO current_range_from_value, current_range_to_value
FROM pg_catalog.time_partitions tp
WHERE parent_table = table_name
  AND tp.to_value <> ''
  AND tp.from_value <> ''
ORDER BY tp.from_value::bigint ASC
    LIMIT 1;

IF current_range_from_value is NULL THEN
        -- Decide on the current_range_from_value of the initial partition according to partition_size of the table.
        current_range_from_value = from_value % partition_size;
        IF from_value % partition_size = 0 THEN
            current_range_from_value = from_value;
ELSE
            current_range_from_value = (from_value / partition_size) * partition_size;
END IF;
        current_range_to_value := current_range_from_value + partition_size;

ELSE
        -- if from_value is newer than pivot's from value, go forward, else go backward
        IF from_value >= current_range_from_value THEN
            WHILE current_range_from_value < from_value LOOP
                current_range_from_value := current_range_from_value + partition_size;
END LOOP;
ELSE
            WHILE current_range_from_value > from_value LOOP
                current_range_from_value := current_range_from_value - partition_size;
END LOOP;
END IF;
        current_range_to_value := current_range_from_value + partition_size;
END IF;

    partition_exist_query = format('SELECT partition FROM pg_catalog.time_partitions tp
        WHERE tp.from_value::bigint = $1 AND tp.to_value::bigint = $2 AND parent_table = $3');
    partition_covers_query = format('SELECT partition, tp.from_value, tp.to_value
        FROM pg_catalog.time_partitions tp
        WHERE
            (($1 >= tp.from_value::bigint AND $1 < tp.to_value::bigint) OR
            ($2 > tp.from_value::bigint AND $2 < tp.to_value::bigint)) AND
            parent_table = $3');

    WHILE current_range_from_value < to_value LOOP
        -- Check whether partition with given range has already been created
        EXECUTE partition_exist_query into partition using current_range_from_value, current_range_to_value, table_name;

        IF partition is not NULL THEN
            current_range_from_value := current_range_to_value;
            current_range_to_value := current_range_to_value + partition_size;
CONTINUE;
END IF;

        -- Check whether any other partition covers from_value or to_value
        -- That means some partitions doesn't align with the initial partition.
        -- In other words, gap(s) exist between partitions which is not multiple of intervals.
EXECUTE partition_covers_query
    INTO manual_partition, manual_partition_from_value_text, manual_partition_to_value_text
    using current_range_from_value, current_range_to_value, table_name;

IF manual_partition is not NULL THEN
            RAISE 'partition % with the range from % to % does not align with the initial partition given the partition interval',
            manual_partition::text,
            manual_partition_from_value_text,
            manual_partition_to_value_text
            USING HINT = 'Only use partitions of the same size, without gaps between partitions.';
END IF;
SELECT current_range_from_value::text INTO current_range_from_value_text;
SELECT current_range_to_value::text INTO current_range_to_value_text;
SELECT (current_range_from_value / partition_size)::text INTO partition_count_text;
-- use range values within the name of partition to have unique partition names
RETURN QUERY
SELECT
        substring(table_name_text, 0, max_table_name_length - length(partition_count_text) - 1) || '_p' || partition_count_text,
        current_range_from_value_text,
        current_range_to_value_text;

current_range_from_value := current_range_to_value;
        current_range_to_value := current_range_to_value + partition_size;
END LOOP;
    RETURN;
END;
$$;
COMMENT
ON FUNCTION get_missing_range_partition_ranges(
    table_name regclass,
    partition_size bigint,
    to_value bigint,
    from_value bigint)
IS 'get missing partitions ranges for table within the range using the given partition size';


CREATE OR REPLACE FUNCTION create_range_partitions(
    table_name regclass,
    partition_size bigint,
    end_at bigint,
    start_from bigint DEFAULT 0)
returns boolean
LANGUAGE plpgsql
AS $$
DECLARE
    -- partitioned table name
schema_name_text name;
    table_name_text name;

    -- record for to-be-created partition
    missing_partition_record record;

    -- result indiciates whether any partitions were created
    partition_created bool := false;
BEGIN
    IF start_from >= end_at THEN
        RAISE 'start_from (%) must be older than end_at (%)', start_from, end_at;
END IF;

SELECT nspname, relname
INTO schema_name_text, table_name_text
FROM pg_class JOIN pg_namespace ON pg_class.relnamespace = pg_namespace.oid
WHERE pg_class.oid = table_name::oid;

-- Get missing partition range info using the get_missing_partition_ranges
-- and create partitions using that info.
FOR missing_partition_record IN
SELECT *
FROM get_missing_range_partition_ranges(table_name, partition_size, end_at, start_from)
         LOOP
    EXECUTE format('CREATE TABLE %I.%I PARTITION OF %I.%I FOR VALUES FROM (%L) TO (%L)',
        schema_name_text,
        missing_partition_record.partition_name,
        schema_name_text,
        table_name_text,
        missing_partition_record.range_from_value,
        missing_partition_record.range_to_value);

partition_created := true;
END LOOP;

RETURN partition_created;
END;
$$;
COMMENT ON FUNCTION create_range_partitions(
    table_name regclass,
    partition_size bigint,
    end_at bigint,
    start_from bigint)
IS 'create range partitions for the given range';

create or replace procedure mirror_node_create_partitions() as
$$
declare
    partitionInfo      record;
    createdPartition   boolean;
    timePartitionRegex varchar = '^(.*_timestamp|consensus_end)$';
begin
    for partitionInfo in
        SELECT tp.parent_table,
               tp.partition_column,
               case
                   when tp.partition_column::varchar ~ timePartitionRegex
                       then extract(epoch from(to_timestamp(max(tp.to_value::bigint) / 1000000000.0)))::bigint
                   else max(tp.to_value)::bigint
                   end                                               as next_from,
               case
                   when tp.partition_column::varchar ~ timePartitionRegex
                       then extract(epoch from(to_timestamp(max(tp.to_value::bigint) / 1000000000.0) + interval ${partitionTimeInterval}))::bigint
                   else max(tp.to_value)::bigint + ${idPartitionSize}
                   end                                               as next_to,
               (tp.partition_column::varchar ~ timePartitionRegex)   as time_partition,
               (select coalesce(max(id), 1) from entity)             as max_entity_id
        from time_partitions tp
        group by tp.parent_table, tp.partition_column
        loop
            if ((not partitionInfo.time_partition and
                partitionInfo.max_entity_id * ${maxEntityIdRatio} < partitionInfo.next_from) or
               (partitionInfo.time_partition and
                (CURRENT_TIMESTAMP + interval ${partitionTimeInterval} < to_timestamp(partitionInfo.next_from))))
            then
                raise info 'Skipping partition creation for %', partitionInfo.parent_table;
                continue;
            end if;
            if partitionInfo.time_partition then
                select create_time_partitions(table_name := partitionInfo.parent_table,
                                              partition_interval := interval ${partitionTimeInterval},
                                              start_from := to_timestamp(partitionInfo.next_from),
                                              end_at := to_timestamp(partitionInfo.next_to))
                into createdPartition;
            else
                select create_range_partitions(table_name := partitionInfo.parent_table,
                                              partition_size := ${idPartitionSize},
                                              start_from := partitionInfo.next_from,
                                              end_at := partitionInfo.next_to)
                into createdPartition;
            end if;
            commit;
            raise info 'Processed % created %', partitionInfo.parent_table, createdPartition;
        end loop;
end;
$$
    language plpgsql;

-------------------
-- Add non-repeatable partitioning logic to large tables.
-------------------
select create_time_partitions(table_name :='public.account_balance',
                              partition_interval := interval ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP - ${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + ${partitionTimeInterval});
select create_time_partitions(table_name :='public.assessed_custom_fee',
                              partition_interval := interval ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP - ${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + ${partitionTimeInterval});
select create_range_partitions(table_name :='public.contract', partition_size := ${idPartitionSize},
                              end_at := ${maxEntityId} + ${idPartitionSize});
select create_time_partitions(table_name :='public.contract_action',
                              partition_interval := interval ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP - ${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + ${partitionTimeInterval});
select create_time_partitions(table_name :='public.contract_log',
                              partition_interval := interval ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP - ${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + ${partitionTimeInterval});
select create_time_partitions(table_name :='public.contract_result',
                              partition_interval := interval ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP - ${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + ${partitionTimeInterval});
select create_range_partitions(table_name :='public.contract_state',
                              partition_size := ${idPartitionSize},
                              end_at := ${maxEntityId} + ${idPartitionSize});
select create_time_partitions(table_name :='public.contract_state_change',
                              partition_interval := interval ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP - ${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + ${partitionTimeInterval});
select create_range_partitions(table_name :='public.crypto_allowance',
                              partition_size := ${idPartitionSize},
                              end_at := ${maxEntityId} + ${idPartitionSize});
select create_range_partitions(table_name :='public.crypto_allowance_history',
                              partition_size := ${idPartitionSize},
                              end_at := ${maxEntityId} + ${idPartitionSize});
select create_time_partitions(table_name :='public.crypto_transfer',
                              partition_interval := interval ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP - ${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + ${partitionTimeInterval});
select create_range_partitions(table_name :='public.custom_fee', partition_size := ${idPartitionSize},
                              end_at := ${maxEntityId} + ${idPartitionSize});
select create_range_partitions(table_name :='public.custom_fee_history', partition_size := ${idPartitionSize},
                              end_at := ${maxEntityId} + ${idPartitionSize});
select create_range_partitions(table_name :='public.entity', partition_size := ${idPartitionSize},
                              end_at := ${maxEntityId} + ${idPartitionSize});
select create_range_partitions(table_name :='public.entity_history',
                              partition_size := ${idPartitionSize},
                              end_at := ${maxEntityId} + ${idPartitionSize});
select create_range_partitions(table_name :='public.entity_stake', partition_size := ${idPartitionSize},
                              end_at := ${maxEntityId} + ${idPartitionSize});
select create_range_partitions(table_name :='public.entity_stake_history',
                              partition_size := ${idPartitionSize},
                              end_at := ${maxEntityId} + ${idPartitionSize});
select create_time_partitions(table_name :='public.entity_transaction',
                              partition_interval := interval ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP - ${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + ${partitionTimeInterval});
select create_time_partitions(table_name :='public.ethereum_transaction',
                              partition_interval := interval ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP - ${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + ${partitionTimeInterval});
select create_time_partitions(table_name :='public.event_file', partition_interval := interval ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP - ${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + ${partitionTimeInterval});
select create_time_partitions(table_name :='public.file_data', partition_interval := interval ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP - ${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + ${partitionTimeInterval});
select create_time_partitions(table_name :='public.network_freeze',
                              partition_interval := interval ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP - ${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + ${partitionTimeInterval});
select create_range_partitions(table_name :='public.nft', partition_size := ${idPartitionSize},
                              end_at := ${maxEntityId} + ${idPartitionSize});
select create_range_partitions(table_name :='public.nft_history', partition_size := ${idPartitionSize},
                              end_at := ${maxEntityId} + ${idPartitionSize});
select create_range_partitions(table_name :='public.nft_allowance',
                              partition_size := ${idPartitionSize},
                              end_at := ${maxEntityId} + ${idPartitionSize});
select create_range_partitions(table_name :='public.nft_allowance_history',
                              partition_size := ${idPartitionSize},
                              end_at := ${maxEntityId} + ${idPartitionSize});
select create_time_partitions(table_name :='public.record_file',
                              partition_interval := interval ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP - ${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + ${partitionTimeInterval});
select create_range_partitions(table_name :='public.schedule', partition_size := ${idPartitionSize},
                              end_at := ${maxEntityId} + ${idPartitionSize});
select create_time_partitions(table_name :='public.staking_reward_transfer',
                              partition_interval := interval ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP - ${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + ${partitionTimeInterval});
select create_range_partitions(table_name :='public.token'::regclass,
                              partition_size := ${idPartitionSize},
                              end_at := ${maxEntityId} + ${idPartitionSize});
select create_range_partitions(table_name :='public.token_history',
                              partition_size := ${idPartitionSize},
                              end_at := ${maxEntityId} + ${idPartitionSize});
select create_range_partitions(table_name :='public.token_account',
                              partition_size := ${idPartitionSize},
                              end_at := ${maxEntityId} + ${idPartitionSize});
select create_range_partitions(table_name :='public.token_account_history',
                              partition_size := ${idPartitionSize},
                              end_at := ${maxEntityId} + ${idPartitionSize});
select create_range_partitions(table_name :='public.token_allowance',
                              partition_size := ${idPartitionSize},
                              end_at := ${maxEntityId} + ${idPartitionSize});
select create_range_partitions(table_name :='public.token_allowance_history',
                              partition_size := ${idPartitionSize},
                              end_at := ${maxEntityId} + ${idPartitionSize});
select create_time_partitions(table_name :='public.token_balance',
                              partition_interval := interval ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP - ${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + ${partitionTimeInterval});
select create_time_partitions(table_name :='public.token_transfer',
                              partition_interval := interval ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP - ${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + ${partitionTimeInterval});
select create_time_partitions(table_name :='public.topic_message',
                              partition_interval := interval ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP - ${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + ${partitionTimeInterval});
select create_time_partitions(table_name :='public.transaction',
                              partition_interval := interval ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP - ${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + ${partitionTimeInterval});
select create_time_partitions(table_name :='public.transaction_hash',
                              partition_interval := interval ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP - ${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + ${partitionTimeInterval});
select create_range_partitions(table_name :='public.transaction_signature',
                              partition_size := ${idPartitionSize},
                              end_at := ${maxEntityId} + ${idPartitionSize});
