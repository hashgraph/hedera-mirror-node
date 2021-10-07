-------------------
-- Revert V1.43.2 which limits changes to token_account table
-------------------

drop function if exists add_missing_token_account_association() cascade;
drop table if exists last_transaction;
