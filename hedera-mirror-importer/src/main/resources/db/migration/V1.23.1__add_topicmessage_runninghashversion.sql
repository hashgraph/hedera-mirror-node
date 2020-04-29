-- add the column conditionally
alter table topic_message add column if not exists running_hash_version smallint null;

UPDATE topic_message SET running_hash_version = CASE WHEN consensus_timestamp < ${r5-rc4-deployDate} THEN 1 ELSE 2 END WHERE running_hash_version IS NULL;
