-------------------
-- Revert V1.43.2 which limits changes to token_account table
-------------------

create or replace function revert_v1_43_2() returns void as
$$
begin
    perform * from pg_proc where proname = 'add_missing_token_account_association';
    if not found then
        raise info 'function is already deleted';
        return;
    end if;

    raise info 'drop function, trigger, and temp table created in v1.43.2';
    drop function add_missing_token_account_association() cascade;
    drop table if exists last_transaction;
end
$$ language plpgsql;

select revert_v1_43_2();

drop function if exists revert_v1_43_2();

