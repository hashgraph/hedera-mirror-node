-------------------
-- Add transaction payer to transfer tables
-------------------
-- add payer to transfer tables
alter table assessed_custom_fee
    add column if not exists payer_account_id bigint;

alter table crypto_transfer
    add column if not exists payer_account_id bigint;

alter table nft_transfer
    add column if not exists payer_account_id bigint;

alter table non_fee_transfer
    add column if not exists payer_account_id bigint;

alter table token_transfer
    add column if not exists payer_account_id bigint;

-- retrieve subset of transaction representing transfers
create temporary table if not exists transfers_subset as
select consensus_timestamp, result, payer_account_id
from transaction
where type = 14
order by consensus_timestamp;

create unique index if not exists transfers_subset__time_res
    on transfers_subset (consensus_timestamp, result);

-- from transaction table, insert payer to transfer tables
update assessed_custom_fee acf
set payer_account_id = t.payer_account_id
from transfers_subset t
where acf.consensus_timestamp = t.consensus_timestamp
  and result = 22;

update crypto_transfer ct
set payer_account_id = t.payer_account_id
from transfers_subset t
where ct.consensus_timestamp = t.consensus_timestamp
  and result = 22;

update nft_transfer nt
set payer_account_id = t.payer_account_id
from transfers_subset t
where nt.consensus_timestamp = t.consensus_timestamp
  and result = 22;

update non_fee_transfer nft
set payer_account_id = t.payer_account_id
from transfers_subset t
where nft.consensus_timestamp = t.consensus_timestamp;

update token_transfer tt
set payer_account_id = t.payer_account_id
from transfers_subset t
where tt.consensus_timestamp = t.consensus_timestamp
  and result = 22;

-- set no nulls
alter table assessed_custom_fee
    alter column payer_account_id set not null;
alter table crypto_transfer
    alter column payer_account_id set not null;
alter table nft_transfer
    alter column payer_account_id set not null;
alter table non_fee_transfer
    alter column payer_account_id set not null;
alter table token_transfer
    alter column payer_account_id set not null;
