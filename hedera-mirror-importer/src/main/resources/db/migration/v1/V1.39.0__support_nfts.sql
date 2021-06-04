-------------------
-- Update and add tables for NFT support
-------------------

-- Update the token table
alter table token
    add column max_supply bigint not null default 9223372036854775807,
    add column supply_type character varying(10) not null default 'INFINITE',
    add column type character varying(20) not null default 'NON_FUNGIBLE_UNIQUE';

-- Create nft table
create table if not exists nft
(
  account_id            bigint                  not null,
  created_timestamp     bigint  primary key     not null,
  deleted               boolean default false   not null,
  modified_timestamp    bigint                  not null,
  metadata              bytea   default ''      not null,
  serial_number         bigint                  not null,
  token_id              bigint                  not null
);
create unique index if not exists nft__token_id_serial_num
    on nft (token_id, serial_number);
comment on table nft is 'NFT';

-- Create nft_transfer table
create table if not exists nft_transfer
(
  consensus_timestamp   bigint  not null,
  receiver_account_id   bigint  not null,
  sender_account_id     bigint  not null,
  serial_number         bigint  not null,
  token_id              bigint  not null
);
create unique index if not exists nft_transfer__timestamp_token_id_serial_num
    on nft_transfer (consensus_timestamp, token_id, serial_number);
comment on table nft_transfer is 'Crypto account nft transfers';

-- Insert new response codes
insert into t_transaction_results (result, proto_id)
values ('ACCOUNT_EXPIRED_AND_PENDING_REMOVAL', 223),
       ('INVALID_TOKEN_MAX_SUPPLY', 224),
       ('INVALID_TOKEN_NFT_SERIAL_NUMBER', 225),
       ('INVALID_NFT_ID', 226);
