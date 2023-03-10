do
'
    declare
        t record;
    begin
        for t in select table_name
                 from information_schema.tables
                 where table_schema = ''public'' and table_name !~ ''^(?:(?!transaction_hash_sharded_[0-9]{2})).*(flyway|transaction_type|citus_|_\d+).*'' and table_type <> ''VIEW''
            loop
                execute format(''delete from %s'', t.table_name);
            end loop;
    end;
';
