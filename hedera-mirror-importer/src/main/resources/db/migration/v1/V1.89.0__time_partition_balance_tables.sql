create or replace function partition_and_prune_balance_table(table_name regclass, old_table_name regclass) returns void as
$$
declare
  current_partition text;
  first_timestamp_ns_after_oa constant bigint := 1568411631396440000;
  max_timestamp_ns constant bigint := 9223372036854775807;
  next_partition text;
  next_start_timestamp timestamp;
  next_start_timestamp_ns bigint;
  one_sec_in_ns constant bigint := 10^9;
  one_hour_in_ns constant bigint := 3600 * one_sec_in_ns;
  one_week_in_ns constant bigint := 7 * 24 * one_hour_in_ns;
  one_week_after_oa constant bigint := first_timestamp_ns_after_oa + one_week_in_ns;
  partition record;
  start_timestamp timestamp;
  start_timestamp_ns bigint;
begin
  if not exists(select * from account_balance_file order by consensus_timestamp limit 1) then
    -- by default, create three partitions if there is no balance info at all
    execute format('create table %I_pruned partition of %I for values from values from (0) to (%s)',
      table_name, table_name, first_timestamp_ns_after_oa);
    execute format('create table %I_2019_09_13 partition of %I for values from (%s) to (%s)',
      table_name, table_name, first_timestamp_ns_after_oa, one_week_after_oa);
    execute format('create table %I_2019_09_20 partition of %I for values from (%s) to (%s)',
      table_name, table_name, one_week_after_oa, max_timestamp_ns);
  else
    -- keep the last 6 hours balance info
    select max(consensus_timestamp) - 6 * one_hour_in_ns into start_timestamp_ns from account_balance_file;

    -- create the pruned partition
    execute format('create table %I_pruned partition of %I for values from (0) to (%s)', table_name, table_name,
      start_timestamp_ns);

    -- create the current partition starting with the 6-hour full balance info
    start_timestamp := to_timestamp(start_timestamp_ns / one_sec_in_ns);
    next_start_timestamp_ns := start_timestamp_ns + one_week_in_ns;
    current_partition := format('%I_%s_%s_%s', table_name, extract(year from start_timestamp),
      lpad(extract(month from start_timestamp)::text, 2, '0'), lpad(extract(day from start_timestamp)::text, 2, '0'));
    execute format('create table %I partition of %I for values from (%s) to (%s)', current_partition, table_name,
      start_timestamp_ns, next_start_timestamp_ns);
    raise info 'Created current partition %', current_partition;

    -- create the next partition
    next_start_timestamp := to_timestamp(next_start_timestamp_ns / one_sec_in_ns);
    next_partition := format('%I_%s_%s_%s', table_name, extract(year from next_start_timestamp),
      lpad(extract(month from next_start_timestamp)::text, 2, '0'),
      lpad(extract(day from next_start_timestamp)::text, 2, '0'));
    execute format('create table %I partition of %I for values from (%s) to (%s)', next_partition, table_name,
      next_start_timestamp_ns, max_timestamp_ns);
    raise info 'Created next partition %', next_partition;

    -- copy the 6-hour balance info into the current partition
    execute format('insert into %I select * from %I where consensus_timestamp >= %s', current_partition,
      old_table_name, start_timestamp_ns);
    raise info 'Copied 6-hour balance info to partition %', current_partition;
  end if;

  -- set storage parameters, note for a partitioned table, can only set them on partitions
  for partition in
    select child.relname
    from pg_inherits
      join pg_class as parent on pg_inherits.inhparent = parent.oid
      join pg_class as child on pg_inherits.inhrelid = child.oid
    where parent.relname = table_name::name
  loop
    execute format('alter table %I set (autovacuum_vacuum_insert_scale_factor = 0,' ||
     ' autovacuum_vacuum_insert_threshold = 10000, parallel_workers = 4)', partition.relname);
  end loop;
end
$$ language plpgsql;

alter table account_balance rename to account_balance_old;
create table if not exists account_balance (like account_balance_old) partition by range (consensus_timestamp);
select partition_and_prune_balance_table('account_balance', 'account_balance_old');
alter table account_balance add primary key (consensus_timestamp, account_id);

alter table token_balance rename to token_balance_old;
create table if not exists token_balance (like token_balance_old) partition by range (consensus_timestamp);
select partition_and_prune_balance_table('token_balance', 'token_balance_old');
alter table token_balance add primary key (consensus_timestamp, account_id, token_id);

drop function if exists partition_and_prune_balance_table(regclass, regclass);