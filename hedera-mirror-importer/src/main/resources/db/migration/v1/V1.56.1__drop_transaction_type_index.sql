-- drop the transaction type index now that the index by type/consensus timestamp exists

drop index if exists transaction_type;