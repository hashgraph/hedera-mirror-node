begin;

-- value 1 means account_id receives the nft and -1 means account_id sends the nft
create temporary table nft_balance_change (
  account_id    bigint not null,
  serial_number bigint not null,
  token_id      bigint not null,
  value         int    not null
) on commit drop;

-- create rows for both the nft receiver and the nft sender
insert into nft_balance_change (token_id, serial_number, account_id, value)
select token_id, serial_number, account_id, value
from (
  select consensus_timestamp, token_id, serial_number, sender_account_id, -1::integer
  from nft_transfer
  where sender_account_id is not null and
        consensus_timestamp <= :end_transfer_timestamp and
        consensus_timestamp > :last_snapshot_timestamp
  union
  select consensus_timestamp,token_id, serial_number, receiver_account_id, 1::integer
  from nft_transfer
  where receiver_account_id is not null and
        consensus_timestamp <= :end_transfer_timestamp and
        consensus_timestamp > :last_snapshot_timestamp
  order by consensus_timestamp
) v (consensus_timestamp, token_id, serial_number, account_id, value);

insert into nft_balance (consensus_timestamp, account_id, serial_number, token_id)
select :end_transfer_timestamp, account_id, serial_number, token_id
from (
  select
      coalesce(nb.account_id, change.account_id) account_id,
      coalesce(nb.serial_number, change.serial_number) serial_number,
      coalesce(nb.token_id, change.token_id) token_id,
      coalesce(nb.value, 0) + coalesce(change.value, 0) as value
  from (
    select account_id, serial_number, token_id, 1::integer as value
    from nft_balance
    where consensus_timestamp = :last_snapshot_timestamp
  ) nb
  full outer join (
    select account_id, serial_number, token_id, sum(value) as value
    from nft_balance_change
    group by account_id, serial_number, token_id
    having sum(value) <> 0
  ) change on change.account_id = nb.account_id and
              change.serial_number = nb.serial_number and
              change.token_id = nb.token_id
) v
where value > 0;

commit;
