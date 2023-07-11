alter table if exists transaction
    add column if not exists itemized_transfer jsonb null;

create temp table nested_itemized_transfer (consensus_timestamp bigint not null, transfer jsonb not null) on commit drop;

with nested_general_itemized_transfer as (
    select nt.consensus_timestamp, jsonb_agg(jsonb_build_object(
            'entity_id', nt.entity_id,
            'consensus_timestamp', nt.consensus_timestamp,
            'amount', nt.amount,
            'payer_account_id', nt.payer_account_id,
            'is_approval', nt.is_approval
        )) as transfer
    from non_fee_transfer nt
             join transaction t on t.consensus_timestamp = nt.consensus_timestamp
group by nt.consensus_timestamp
    )

insert into nested_itemized_transfer (consensus_timestamp, transfer)
select consensus_timestamp, transfer
from nested_general_itemized_transfer
order by consensus_timestamp;

create unique index on nested_itemized_transfer (consensus_timestamp);

update transaction
set itemized_transfer = transfer
    from nested_itemized_transfer
where nested_itemized_transfer.consensus_timestamp = transaction.consensus_timestamp;

drop table if exists non_fee_transfer;
