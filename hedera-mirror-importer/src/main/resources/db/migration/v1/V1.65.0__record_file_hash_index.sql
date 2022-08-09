create index if not exists record_file__hash
    on record_file (encode(hash::bytea, 'hex') COLLATE "C");
