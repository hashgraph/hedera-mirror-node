-- create nft_transfer column; separate migration will copy the existing data into it.
alter table if exists transaction
    add column if not exists nft_transfer jsonb null;
