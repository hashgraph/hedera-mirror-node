-------------------
-- Fix the missing token treasury association
-------------------

-- If an account owns a token without an association, the account must be the treasury when the token is created
insert into
    token_account (account_id, associated, created_timestamp, freeze_status, kyc_status, modified_timestamp, token_id)
select
    tb.account_id,
    true,
    t.created_timestamp,
    case when t.freeze_key is null then 0
         else 2
    end,
    case when t.kyc_key is null then 0
         else 1
    end,
    t.created_timestamp,
    tb.token_id
from token_balance tb
join token t on t.token_id = tb.token_id
where tb.consensus_timestamp = (select max(consensus_timestamp) from token_balance)
on conflict do nothing;
