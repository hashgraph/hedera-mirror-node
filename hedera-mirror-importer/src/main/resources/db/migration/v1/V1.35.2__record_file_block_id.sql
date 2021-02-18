-------------------
-- Add column block_index, a 0-based index for record files by consensus_end in ascending order.
-- Also drop the id column and its sequence
-------------------

alter table if exists record_file
    drop column id,
    alter column consensus_start drop default,
    alter column consensus_end drop default;
drop index if exists record_file__consensus_end;
drop sequence if exists s_record_files_seq;

alter table if exists record_file
    add column block_index bigint,
    add primary key (consensus_end);
create unique index if not exists record_file__block_index on record_file (block_index);

create or replace function backfill_block_index() returns void as
$$
declare
    index      bigint;
    cursor     refcursor;
    recordFile record;
begin
    index := 0;
    open cursor no scroll for select consensus_end from record_file order by consensus_end for update;

    while true
        loop
            fetch cursor into recordFile;
            if not FOUND then
                exit;
            end if;

            update record_file set block_index = index where current of cursor;
            index := index + 1;
        end loop;

    close cursor;
end;
$$ language plpgsql;

select backfill_block_index();
drop function if exists backfill_block_index();

alter table if exists record_file alter column block_index set not null;
