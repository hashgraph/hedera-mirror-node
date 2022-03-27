-- add a multi-column index on transaction type and consensus timestamp to speed up type-filtered, timestamp-ordered queries on transactions

create index if not exists transaction__type_consensus_timestamp on transaction (type, consensus_timestamp);