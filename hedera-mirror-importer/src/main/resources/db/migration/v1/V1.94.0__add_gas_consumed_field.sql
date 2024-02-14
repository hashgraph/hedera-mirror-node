alter table contract_result
add column if not exists gas_consumed bigint null;

-- Function for counting zero and non-zero bytes
create or replace function bytes_gas_calculation(bytea) returns bigint as $$
declare
  zero_count bigint := 0;
  non_zero_count bigint := 0;
  i int;
begin
  for i in 1..octet_length($1) loop
    if get_byte($1, i-1) = 0 then
      zero_count := zero_count + 1;
    else
      non_zero_count := non_zero_count + 1;
    end if;
  end loop;
  return zero_count * 4 + non_zero_count * 16; -- 4 gas per zero byte, 16 gas per non-zero byte
end;
$$ language plpgsql immutable;

-- Function to get intrinsic gas for contract creation
create or replace function get_intrinsic_gas(initcode bytea, is_creation boolean) returns bigint as $$
begin
  -- Always include the base base cost of 21000 gas
  if is_creation then
    return 21000 + 32000 + bytes_gas_calculation(initcode); -- Calculate extra gas cost for contract creation
  else
    return 21000;
  end if;
end;
$$ language plpgsql immutable;

-- Update contract_result with gas_consumed values only if there are corresponding entries in contract_action
with contract_action_gas_usage as (
    select
        consensus_timestamp,
        sum(gas_used) as total_gas_used
    from
        contract_action
    group by
        consensus_timestamp
), contract_transaction_info as (
    select
        cr.consensus_timestamp,
        cr.contract_id,
        e.created_timestamp,
        c.initcode,
        -- Determine if this is a contract create or call transaction based on entity's created_timestamp
        (cr.consensus_timestamp = e.created_timestamp) as is_creation
    from
        contract_result cr
    left join contract c on cr.contract_id = c.id
    left join entity e on c.id = e.id
), gas_calculation as (
    select
        cti.consensus_timestamp,
        cti.is_creation,
        cti.initcode,
        cagu.total_gas_used
    from
        contract_transaction_info cti
    -- Filter out only contract transactions with contract actions sidecars
    join contract_action_gas_usage cagu on cti.consensus_timestamp = cagu.consensus_timestamp
)
update contract_result cr
set gas_consumed = gc.total_gas_used +
                   get_intrinsic_gas(gc.initcode, gc.is_creation)
from gas_calculation gc
where cr.consensus_timestamp = gc.consensus_timestamp;

drop function if exists bytes_gas_calculation(bytea);
drop function if exists get_intrinsic_gas(bytea);