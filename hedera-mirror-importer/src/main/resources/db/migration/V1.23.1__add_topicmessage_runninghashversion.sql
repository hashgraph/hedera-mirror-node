-- add the running_hash_version column conditionally
alter table topic_message add column if not exists running_hash_version smallint null;

-- set rows with empty running_hash_version to 1 if r5-rc4 is not yet released and to the default value if it is
UPDATE topic_message SET running_hash_version = CASE WHEN consensus_timestamp < ${v05-deployDate} THEN 1 ELSE ${runningHashDefaultVersion} END WHERE running_hash_version IS NULL;
