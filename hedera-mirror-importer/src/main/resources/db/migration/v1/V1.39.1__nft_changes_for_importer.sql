-------------------
-- Update NFT tables based on discoveries adding Importer code
-------------------

-- Change nft primary key
alter table if exists nft
    drop constraint nft_pkey;
alter table if exists nft
    drop constraint nft__token_id_serial_num;
alter table nft
    add primary key (token_id, serial_number);

-- Remove not null constraints on nft_tranfer account ids
ALTER TABLE nft_transfer ALTER COLUMN receiver_account_id DROP NOT NULL;
ALTER TABLE nft_transfer ALTER COLUMN sender_account_id DROP NOT NULL;


-- Insert new response codes
insert into t_transaction_results (result, proto_id)
values ('METADATA_TOO_LONG', 227),
       ('BATCH_SIZE_LIMIT_EXCEEDED', 228),
       ('QUERY_RANGE_LIMIT_EXCEEDED', 229);

