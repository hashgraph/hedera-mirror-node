-------------------
-- Add support for nft balance snapshot
-------------------

create table if not exists nft_balance (
  account_id          bigint not null,
  consensus_timestamp bigint not null,
  serial_number       bigint not null,
  token_id            bigint not null
);
create index if not exists nft_balance__timestamp_account
    on nft_balance(consensus_timestamp, account_id);
