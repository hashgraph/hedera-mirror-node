-------------------
-- Add missing custom fee collector token account association
-------------------

-- If an initial custom fee collector of
--   1. fixed custom fee charged in the new token; or 2. fractional custom fee
-- still owns the token and no token account association exists, create one with associated set to true. This may add
-- a token account association for the treasury if the treasury is such a custom fee collector
insert into
    token_account (account_id, associated, created_timestamp, modified_timestamp, token_id)
select
    collector_account_id,
    true,
    cf.created_timestamp,
    cf.created_timestamp,
    cf.token_id
from custom_fee cf
join token t on cf.token_id = t.token_id and cf.created_timestamp = t.created_timestamp
join token_balance tb on t.token_id = tb.token_id and collector_account_id = tb.account_id
where
    tb.consensus_timestamp = (select max(consensus_timestamp) from token_balance) and
    (cf.amount_denominator is not null or cf.denominating_token_id = t.token_id)
on conflict do nothing;
