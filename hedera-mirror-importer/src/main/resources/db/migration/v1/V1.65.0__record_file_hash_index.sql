create index if not exists record_file__hash_prefix
    on record_file (substring(hash from 1 for 64));
