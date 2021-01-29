/*
 * Rename / reorganize hash columns in record_file table:
 *   - columns hash and prev_hash store the chained hash of the current file and the previous file respectively
 *   - the new file_hash column stores the file hash of record files if the file hash is not used as chained hash,
 *     e.g., v1 and v2 record files
 */

alter table if exists record_file rename column file_hash to hash;
alter table if exists record_file add column if not exists file_hash character varying(96);

create or replace function migrate_record_file_hash() returns void as
$$
declare
	recordFileCursor refcursor;
	recordFile       record;
begin
	open recordFileCursor no scroll for select end_running_hash, hash, version
										from record_file
										order by consensus_end desc
										for update;

	while true
		loop
			fetch recordFileCursor into recordFile;
			if not FOUND or recordFile.version != 5 then
				exit;
			end if;

			-- set v5 record file's file_hash and hash columns
			update record_file
			set file_hash = recordFile.hash,
			    hash = recordFile.end_running_hash
			where current of recordFileCursor;
		end loop;

	close recordFileCursor;
end;
$$ language plpgsql;

select migrate_record_file_hash();
drop function if exists migrate_record_file_hash();

alter table if exists record_file drop column if exists end_running_hash;

