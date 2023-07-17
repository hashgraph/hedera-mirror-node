alter table if exists transaction
    add column if not exists itemized_transfer jsonb null;

with nested_itemized_transfer as (
    select nt.consensus_timestamp, jsonb_agg(jsonb_build_object(
            'entity_id', nt.entity_id,
            'amount', nt.amount,
            'is_approval', nt.is_approval
        )) as transfer
    from non_fee_transfer nt
    group by nt.consensus_timestamp
    )

update transaction
set itemized_transfer = transfer
from nested_itemized_transfer
where nested_itemized_transfer.consensus_timestamp = transaction.consensus_timestamp;



