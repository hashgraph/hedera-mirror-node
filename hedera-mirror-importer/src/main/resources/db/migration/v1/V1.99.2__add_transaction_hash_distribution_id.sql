alter table if exists transaction_hash add column distribution_id smallint;

drop function if exists get_transaction_info_by_hash(bytea);

create or replace function get_transaction_info_by_hash(bytea)
  returns table
    (
      consensus_timestamp bigint,
      hash                bytea,
      payer_account_id    bigint,
      distribution_id     smallint
    )
as
$$
declare
shard varchar;
begin
  shard := concat('transaction_hash_', to_char(mod(get_byte($1, 0), 32), 'fm00'));
  return query execute 'select * from ' || shard || ' where substring(hash from 1 for 32) = $1' using $1;
end
$$ language plpgsql;