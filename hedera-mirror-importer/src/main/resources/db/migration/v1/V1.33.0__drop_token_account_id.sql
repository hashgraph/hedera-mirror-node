-------------------
-- Drop token_account id, replacing id primary key with unique index on (created_timestamp)
-------------------
alter table token_account
    drop constraint token_account_pkey;
create unique index if not exists token_account__created_timestamp
    on token_account (created_timestamp);

alter table if exists token_account
    drop column if exists id;

drop sequence if exists token_account_id_seq;

-- drop unused functions
drop function if exists
    f_entity_create(bigint, bigint, bigint, integer, bigint, bigint, bigint, bigint, character varying, bytea, bigint, bytea, nanos_timestamp, text);

drop function if exists
    encodeentityid(bigint, bigint, bigint);
