-- Update file_data table

-- remove file_data rows where file_data is empty and the transaction type is not FILECREATE
delete from file_data
where length(file_data) = 0 and transaction_type <> 17;

alter table if exists file_data alter column file_data set not null;

-- add index for queries by file id
create index if not exists file_data__id_timestamp on file_data(entity_id, consensus_timestamp);
