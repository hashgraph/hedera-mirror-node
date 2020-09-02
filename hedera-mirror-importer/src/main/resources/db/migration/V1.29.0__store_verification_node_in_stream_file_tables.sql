-- set the number of transactions for historic record_file records
create or replace function updateRecordFileCount() returns void as
$$
declare
	txnCursor          refcursor;
	recordFile         record;
	consensusStart     bigint;
	consensusEnd       bigint;
	currentNs          bigint;
	txnCount           bigint;
begin
	open txnCursor no scroll for select consensus_ns from transaction order by consensus_ns;
	currentNs := 0;

	for recordFile in
		select consensus_start,consensus_end
		from record_file
		order by consensus_end
		loop
			txnCount := 0;
			consensusStart := recordFile.consensus_start;
			consensusEnd := recordFile.consensus_end;

			if currentNs is NOT NULL and currentNs >= consensusStart then
				txnCount := 1;
			end if;

			while true
				loop
					fetch txnCursor into currentNs;
					if currentNs is NULL or currentNs > consensusEnd then
					    update record_file
					        set count = txnCount
					    where consensus_end = consensusEnd;
						exit; -- exit the while loop
					end if;
					if currentNs >= consensusStart then
						txnCount := txnCount + 1;
					end if;
				end loop;
		end loop;
end
$$ language plpgsql;

-- add node_account_id to record_file table
alter table if exists record_file
    add column node_account_id entity_id not null default 3;
alter table if exists record_file
    alter column node_account_id drop default;

-- add count to record_file table
alter table if exists record_file
    add column count bigint;

select updateRecordFileCount();

alter table if exists record_file
    alter column count set not null;

drop function if exists updateRecordFileCount();

-- account_balance_file table
create table if not exists account_balance_file (
    name                varchar(250) primary key,
    consensus_timestamp nanos_timestamp not null,
    count               bigint not null,
    load_start          bigint,
    load_end            bigint,
    file_hash           varchar(96),
    node_account_id     entity_id not null
);
