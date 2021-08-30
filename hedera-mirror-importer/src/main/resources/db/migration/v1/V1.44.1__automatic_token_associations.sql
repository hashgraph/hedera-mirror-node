-------------------
-- Add support for automatic token associations
-------------------

insert into t_transaction_results (result, proto_id)
values ('NO_REMAINING_AUTO_ASSOCIATIONS', 262),
       ('EXISTING_AUTOMATIC_ASSOCIATIONS_EXCEED_GIVEN_LIMIT', 263);

alter table if exists entity
    add column max_automatic_token_associations bigint;

update entity
set max_automatic_token_associations = 0
where type = 1;

alter table if exists token_account
    add column auto_associated boolean not null default false;

-- delete all dissociated token_account rows
delete from token_account where associated is false;
