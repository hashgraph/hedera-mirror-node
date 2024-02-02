-- configure session to ensure partitions are created with bounds in UTC
SET timezone TO 'UTC';

CREATE FUNCTION nanos_to_timestamptz(nanos bigint) RETURNS timestamptz
    LANGUAGE plpgsql AS
    $$
DECLARE
value timestamptz;
BEGIN
select to_timestamp(nanos / 1000000000.0)
into value;
return value;
END;
    $$;
CREATE CAST (bigint AS timestamptz) WITH FUNCTION nanos_to_timestamptz(bigint);

CREATE FUNCTION timestamptz_to_nanos(ts timestamptz) RETURNS bigint
    LANGUAGE plpgsql AS
    $$
DECLARE
value bigint;
BEGIN
select extract(epoch from ts) * 1000000000
into value;
return value;
END;
    $$;
CREATE CAST (timestamptz AS bigint) WITH FUNCTION timestamptz_to_nanos(timestamptz);

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

create or replace function apply_vacuum_settings_from_child(parent regclass, child regclass) returns void as
$$
declare
    current_partition_vacuum_settings text;
    new_partition                     regclass;
    begin
        select tp.partition
        from time_partitions tp
        where tp.parent_table = parent order by tp.from_value::bigint desc limit 1
        into new_partition;
        select string_agg(options.option, ',') as vacuum_settings
        from (select unnest(reloptions) as option
              from pg_class where relname=child::text) options
        where options.option ILIKE '%vacuum%'
        into current_partition_vacuum_settings;

        if new_partition != child and length(coalesce(current_partition_vacuum_settings, '')) > 0 then
            execute format(concat('alter table if exists %I set (', current_partition_vacuum_settings, ')'), new_partition);
        end if;
    end;
$$
    language plpgsql;

create or replace procedure create_mirror_node_range_partitions() as
$$
declare
    partition_info      record;
    created_partition   boolean;
    time_partition_pattern varchar = '^(.*_timestamp|consensus_end)$';
begin
    for partition_info in
        SELECT distinct on (tp.parent_table) tp.parent_table,
               tp.to_value::bigint                              as next_from,
               tp.to_value::bigint + ${idPartitionSize}::bigint as next_to,
               (select coalesce(max(id), 1) from entity)        as max_entity_id,
               tp.partition                                     as current_partition
        from time_partitions tp
        where tp.partition_column::varchar !~ time_partition_pattern
        order by tp.parent_table, tp.from_value::bigint desc
        loop
            if partition_info.max_entity_id * ${maxEntityIdRatio} < partition_info.next_from then
                raise log 'Skipping partition creation for range partition % from % to %', partition_info.parent_table, partition_info.next_from, partition_info.next_to;
                continue;
            end if;
            select create_range_partitions(partition_info.parent_table, ${idPartitionSize}::bigint,
                                           partition_info.next_to::bigint, partition_info.next_from::bigint)
            into created_partition;
            if created_partition then
                perform apply_vacuum_settings_from_child(partition_info.parent_table, partition_info.current_partition);
            end if;
            commit;
            raise log 'Processed % for values from % to % created %',
                       partition_info.parent_table, partition_info.next_from,
                       partition_info.next_to, created_partition;
        end loop;
end;
$$
    language plpgsql;

create or replace procedure create_mirror_node_time_partitions() as
$$
declare
    partition_info                    record;
    created_partition                 boolean;
    time_partition_pattern            varchar = '^(.*_timestamp|consensus_end)$';
begin
    for partition_info in
        SELECT distinct on (tp.parent_table) tp.parent_table,
               to_timestamp(tp.to_value::bigint / 1000000000.0)                                      as next_from,
               to_timestamp(tp.to_value::bigint / 1000000000.0) + interval ${partitionTimeInterval}  as next_to,
               tp.partition                                                                          as current_partition
        from time_partitions tp
        where tp.partition_column::varchar ~ time_partition_pattern
        order by tp.parent_table, tp.from_value::bigint desc
        loop
            if CURRENT_TIMESTAMP + interval ${partitionTimeInterval} < partition_info.next_from then
                raise log 'Skipping partition creation for time partition % from % to %',
                          partition_info.parent_table, partition_info.next_from, partition_info.next_to;
                continue;
            end if;
            select create_time_partitions(partition_info.parent_table, interval ${partitionTimeInterval},
                                          partition_info.next_to, partition_info.next_from)
            into created_partition;
            if created_partition then
                perform apply_vacuum_settings_from_child(partition_info.parent_table, partition_info.current_partition);
            end if;
            commit;
            raise log 'Processed % for values from % to % created %',
                       partition_info.parent_table, partition_info.next_from,
                       partition_info.next_to, created_partition;
        end loop;
end;
$$
    language plpgsql;
-------------------
-- Add non-repeatable partitioning logic to large tables.
-------------------
select create_time_partitions(table_name :='public.account_balance',
                              partition_interval := ${partitionTimeInterval},
                              start_from := ${partitionStartDate}::timestamptz,
                              end_at := CURRENT_TIMESTAMP + ${partitionTimeInterval});
select create_time_partitions(table_name :='public.assessed_custom_fee',
                              partition_interval := ${partitionTimeInterval},
                              start_from := ${partitionStartDate}::timestamptz,
                              end_at := CURRENT_TIMESTAMP + ${partitionTimeInterval});
select create_range_partitions(table_name :='public.contract', partition_size := ${idPartitionSize},
                              end_at := ${maxEntityId} + ${idPartitionSize});
select create_time_partitions(table_name :='public.contract_action',
                              partition_interval := ${partitionTimeInterval},
                              start_from := ${partitionStartDate}::timestamptz,
                              end_at := CURRENT_TIMESTAMP + ${partitionTimeInterval});
select create_time_partitions(table_name :='public.contract_log',
                              partition_interval := ${partitionTimeInterval},
                              start_from := ${partitionStartDate}::timestamptz,
                              end_at := CURRENT_TIMESTAMP + ${partitionTimeInterval});
select create_time_partitions(table_name :='public.contract_result',
                              partition_interval := ${partitionTimeInterval},
                              start_from := ${partitionStartDate}::timestamptz,
                              end_at := CURRENT_TIMESTAMP + ${partitionTimeInterval});
select create_range_partitions(table_name :='public.contract_state',
                              partition_size := ${idPartitionSize},
                              end_at := ${maxEntityId} + ${idPartitionSize});
select create_time_partitions(table_name :='public.contract_state_change',
                              partition_interval := ${partitionTimeInterval},
                              start_from := ${partitionStartDate}::timestamptz,
                              end_at := CURRENT_TIMESTAMP + ${partitionTimeInterval});
select create_time_partitions(table_name :='public.contract_transaction',
                              partition_interval := interval ${partitionTimeInterval},
                              start_from := ${partitionStartDate}::timestamptz,
                              end_at := CURRENT_TIMESTAMP + ${partitionTimeInterval});
select create_range_partitions(table_name :='public.crypto_allowance',
                              partition_size := ${idPartitionSize},
                              end_at := ${maxEntityId} + ${idPartitionSize});
select create_range_partitions(table_name :='public.crypto_allowance_history',
                              partition_size := ${idPartitionSize},
                              end_at := ${maxEntityId} + ${idPartitionSize});
select create_time_partitions(table_name :='public.crypto_transfer',
                              partition_interval := ${partitionTimeInterval},
                              start_from := ${partitionStartDate}::timestamptz,
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
                              partition_interval := ${partitionTimeInterval},
                              start_from := ${partitionStartDate}::timestamptz,
                              end_at := CURRENT_TIMESTAMP + ${partitionTimeInterval});
select create_time_partitions(table_name :='public.ethereum_transaction',
                              partition_interval := ${partitionTimeInterval},
                              start_from := ${partitionStartDate}::timestamptz,
                              end_at := CURRENT_TIMESTAMP + ${partitionTimeInterval});
select create_time_partitions(table_name :='public.file_data', partition_interval := ${partitionTimeInterval},
                              start_from := ${partitionStartDate}::timestamptz,
                              end_at := CURRENT_TIMESTAMP + ${partitionTimeInterval});
select create_time_partitions(table_name :='public.network_freeze',
                              partition_interval := ${partitionTimeInterval},
                              start_from := ${partitionStartDate}::timestamptz,
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
                              partition_interval := ${partitionTimeInterval},
                              start_from := ${partitionStartDate}::timestamptz,
                              end_at := CURRENT_TIMESTAMP + ${partitionTimeInterval});
select create_range_partitions(table_name :='public.schedule', partition_size := ${idPartitionSize},
                              end_at := ${maxEntityId} + ${idPartitionSize});
select create_time_partitions(table_name :='public.staking_reward_transfer',
                              partition_interval := ${partitionTimeInterval},
                              start_from := ${partitionStartDate}::timestamptz,
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
                              partition_interval := ${partitionTimeInterval},
                              start_from := ${partitionStartDate}::timestamptz,
                              end_at := CURRENT_TIMESTAMP + ${partitionTimeInterval});
select create_time_partitions(table_name :='public.token_transfer',
                              partition_interval := ${partitionTimeInterval},
                              start_from := ${partitionStartDate}::timestamptz,
                              end_at := CURRENT_TIMESTAMP + ${partitionTimeInterval});
select create_time_partitions(table_name :='public.topic_message',
                              partition_interval := ${partitionTimeInterval},
                              start_from := ${partitionStartDate}::timestamptz,
                              end_at := CURRENT_TIMESTAMP + ${partitionTimeInterval});
select create_time_partitions(table_name :='public.transaction',
                              partition_interval := ${partitionTimeInterval},
                              start_from := ${partitionStartDate}::timestamptz,
                              end_at := CURRENT_TIMESTAMP + ${partitionTimeInterval});
select create_range_partitions(table_name :='public.transaction_signature',
                              partition_size := ${idPartitionSize},
                              end_at := ${maxEntityId} + ${idPartitionSize});
