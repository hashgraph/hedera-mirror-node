------------
-- functions
------------
drop function if exists extractNsFromFilePath(varchar);
create function extractNsFromFilePath(filePath varchar) returns bigint as
$$
declare
	basename varchar;
	secsStr  varchar;
	secs     bigint;
	nsecsStr varchar;
	nsecs    bigint;
begin
	basename := regexp_replace(filePath, '^.*/([^/]*?)(\.[^/.]+)?$', '\1');

	secsStr := split_part(basename, '.', 1);
	secsStr := translate(secsStr, 'T_', ' :');
	secs := extract(epoch from to_timestamp(secsStr, 'YYYY-MM-DD HH24:MI:SS'));

	nsecsStr := regexp_replace(basename, '.*\.([0-9]*)Z', '\1');
	nsecsStr := rpad(nsecsStr, 9, '0');
	nsecs := to_number(nsecsStr, '999999999');

	return secs * 1000000000 + nsecs;
end;
$$ language plpgsql;

drop function if exists getLastConsensusNsInRange(bigint, bigint);
-- get the last consensus_ns from table transaction where consensus_ns is in the range [startNs, endNs)
create function getLastConsensusNsInRange(startNs bigint, endNs bigint) returns bigint as
$$
declare
	lastConsensusNs bigint;
begin
	select consensus_ns
	into strict lastConsensusNs
	from transaction
	where consensus_ns >= startNs
	  and consensus_ns < endNs
	order by consensus_ns desc
	limit 1;
	return lastConsensusNs;
end;
$$ language plpgsql;

drop function if exists getNextRecordFileConsensusStartAfterNs(bigint);
-- given a timestamp ns, find the immediate next record file after ns and return its consensus_start
create function getNextRecordFileConsensusStartAfterNs(ns bigint) returns bigint as
$$
declare
	nextConsensusStart bigint;
begin
	select consensus_start
	into strict nextConsensusStart
	from t_record_files
	where consensus_start > ns
	order by consensus_start
	limit 1;
	return nextConsensusStart;
exception
	when NO_DATA_FOUND then
		-- no record file after consensus_start, use bigint max
		return 9223372036854775807;
end;
$$ language plpgsql;


-- set consensus_start to the ns extracted from file name if it's 0
update t_record_files
set consensus_start = extractNsFromFilePath(name)
where consensus_start = 0;

-- set consensus_end to the consensus timestamp of the last transaction in the record file
update t_record_files
set consensus_end = getLastConsensusNsInRange(consensus_start, getNextRecordFileConsensusStartAfterNs(consensus_start))
where consensus_end = 0;


-- drop all functions
drop function if exists extractNsFromFilePath(varchar);
drop function if exists getLastConsensusNsInRange(bigint, bigint);
drop function if exists getNextRecordFileConsensusStartAfterNs(bigint);
