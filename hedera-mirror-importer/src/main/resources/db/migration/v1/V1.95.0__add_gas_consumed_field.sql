alter table if exists contract_result
    add column if not exists gas_consumed bigint null;

-- Function to get intrinsic gas for contract creation
create or replace function get_intrinsic_gas(payload bytea, is_creation boolean) returns bigint as $$
declare
  zero_count bigint := 0;
  non_zero_count bigint := 0;
  i int;
begin
  for i in 1..octet_length(payload) loop
    if get_byte(payload, i-1) = 0 then
      zero_count := zero_count + 1;
    else
      non_zero_count := non_zero_count + 1;
    end if;
  end loop;
  if is_creation then
    return 21000 + 32000 + zero_count * 4 + non_zero_count * 16; -- contract creation cost calculation
  else
    return 21000 + zero_count * 4 + non_zero_count * 16; -- contract call cost calculation
  end if;
end;
$$ language plpgsql immutable;

-- Update contract_result with gas_consumed values.
-- Use the contract_action with call_depth = 0, as this is the top-level action, and add intrinsic gas to it.
with contract_action_gas_usage as (
    select
        consensus_timestamp,
        gas_used
    from
        contract_action
    where
        call_depth = 0
), contract_transaction_info as (
    select
        cr.consensus_timestamp,
        cr.contract_id,
        e.created_timestamp,
        -- Correctly assign `payload` based on whether it's a contract creation or call.
        case
            when cr.consensus_timestamp = e.created_timestamp then c.initcode
            else cr.function_parameters
        end as payload,
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
        cti.payload,
        cagu.gas_used
    from
        contract_transaction_info cti
    -- Filter out only contract transactions with contract actions sidecars
    join contract_action_gas_usage cagu on cti.consensus_timestamp = cagu.consensus_timestamp
)
update contract_result cr
set gas_consumed = gc.gas_used +
                   get_intrinsic_gas(gc.payload, gc.is_creation)
from gas_calculation gc
where cr.consensus_timestamp = gc.consensus_timestamp;

drop function if exists bytes_gas_calculation(bytea);
drop function if exists get_intrinsic_gas(bytea, boolean);