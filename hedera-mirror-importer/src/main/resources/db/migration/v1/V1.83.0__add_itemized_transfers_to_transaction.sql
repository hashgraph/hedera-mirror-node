alter table if exists transaction
    add column if not exists itemized_transfer jsonb null;
