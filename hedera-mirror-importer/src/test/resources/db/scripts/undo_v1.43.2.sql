-------------------
-- Undo the resources created in V1.43.2
-------------------

-- with cascade, also drop the trigger
drop function if exists add_missing_token_account_association() cascade;
drop table if exists last_transaction;
