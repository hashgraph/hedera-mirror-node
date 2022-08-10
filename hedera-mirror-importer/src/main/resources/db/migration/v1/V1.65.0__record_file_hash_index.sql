create unique index if not exists record_file__hash
    on record_file (hash collate "C");
