-- Remove t_transaction_types and alter tables referencing it to use a transaction_result value instead.

-- Create the enum transaction_result to replace the foreign key with
DO
$$
BEGIN
EXECUTE (
    SELECT format('CREATE TYPE transaction_result AS ENUM (%s)'
                  ,string_agg(DISTINCT quote_literal(result), ', '))
    FROM  t_transaction_results t);
END
$$;

create or replace function updateTransactionResultFromInt(integer)
    returns transaction_result as 'select cast(upper(ttr.result) as transaction_result) from t_transaction_results ttr where ttr.proto_id = $1;'
    language sql
    returns null on null input;


-- Alter t_transaction_types to use the new enum transaction_result
alter table transaction
    add column result_enum transaction_result null;

update transaction
    set result_enum = updateTransactionResultFromInt(result);

alter table transaction
    drop column result;

alter table transaction
    rename column result_enum to result;

alter table transaction
    alter column type set not null;

-- Drop t_transaction_types
drop table t_transaction_results;
