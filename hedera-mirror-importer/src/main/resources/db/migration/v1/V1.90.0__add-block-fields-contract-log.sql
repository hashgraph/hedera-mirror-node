alter table contract_log add column block_hash character varying(96);
alter table contract_log add column block_number bigint;

update contract_log
set block_number = (select index from record_file where consensus_end >= consensus_timestamp
                                                 order by consensus_end asc limit 1),
    block_hash = (select hash from record_file where consensus_end >= consensus_timestamp
                                                order by consensus_end asc limit 1);

alter table contract_log alter column block_hash set not null;
alter table contract_log alter column block_number set not null;