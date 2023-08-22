-- -------------------
-- -- autovacuum insert-only tables more frequently to ensure most pages are visible for index-only scans
-- -------------------
--
create
or replace function apply_vacuum_settings_for_table(parent regclass, autovacuum_vacuum_insert_scale_factor float,
                                              autovacuum_vacuum_insert_threshold integer, log_autovacuum_min_duration integer)
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
        EXECUTE format('alter table if exists %I set (autovacuum_vacuum_insert_scale_factor=%L, autovacuum_vacuum_insert_threshold=%L, log_autovacuum_min_duration=%L)',
                partition_info.partition, autovacuum_vacuum_insert_scale_factor,  autovacuum_vacuum_insert_threshold, log_autovacuum_min_duration);
    end loop;
end;
$$
language plpgsql;


create or replace function apply_vacuum_settings() returns void as
$$
begin
    perform apply_vacuum_settings_for_table('crypto_transfer'::regclass, 0, ${autovacuumVacuumInsertThresholdCryptoTransfer}, 0);
    perform apply_vacuum_settings_for_table('token_transfer'::regclass, 0, ${autovacuumVacuumInsertThresholdTokenTransfer},0);
    perform apply_vacuum_settings_for_table('transaction'::regclass, 0, ${autovacuumVacuumInsertThresholdTransaction}, 0);
end;
$$
language plpgsql;

select apply_vacuum_settings();
