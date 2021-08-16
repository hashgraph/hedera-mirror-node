-------------------
-- Add the missing token account association
-------------------

-- Importer has missed the following auto token-account associations:
--   1. A token's treasury at the time of token creation
--   2. A token's custom fee collector of either a fixed custom fee charged in the newly created token or a fractional
--      fee, at the time of token creation
-- Once the importer is patched, we can add associations for the token-account pairs in the account balance file but
-- not in the token_account table
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
