alter table if exists transaction
    add column if not exists itemized_transfer jsonb null;

with nested_itemized_transfer as (
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


update transaction
set itemized_transfer = t,,mmm,,,,mransfer
    from nested_itemized_transfer
where nested_itemized_transfer.consensus_timestamp = transaction.consensus_timestamp;

drop table if exists non_fee_transfer;

