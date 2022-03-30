-- add columns to nft table for nft instance allowance
alter table nft
  add column if not exists allowance_granted_timestamp bigint default null,
  add column if not exists delegating_spender bigint default null,
  add column if not exists spender bigint default null;

-- update nft table with nft instance allowance
with allowance as (
  select owner, spender, lower(timestamp_range) as timestamp, token_id, serial_number
  from nft_allowance, unnest(serial_numbers) as serial_number
  where array_length(serial_numbers, 1) > 0
)
update nft
  set allowance_granted_timestamp = allowance.timestamp, spender = allowance.spender
from allowance
where nft.account_id = allowance.owner
  and nft.token_id = allowance.token_id
  and nft.serial_number = allowance.serial_number;

-- delete non approved_for_all allowance
delete from nft_allowance where approved_for_all is false and array_length(serial_numbers, 1) = 0;
alter table nft_allowance drop column if exists serial_numbers;

-- create new index for nft instance allowance query
create index if not exists nft__allowance on nft (account_id, spender, token_id, serial_number)
  where account_id is not null and spender is not null;
