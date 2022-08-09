create index if not exists transaction__hash_prefix on transaction (substring(transaction_hash from 1 for 32));
