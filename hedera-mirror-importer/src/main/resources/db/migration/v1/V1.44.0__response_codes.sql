-------------------
-- Add new response codes
-------------------

insert into t_transaction_results (result, proto_id)
values ('NO_REMAINING_AUTO_ASSOCIATIONS', 262),
       ('EXISTING_AUTOMATIC_ASSOCIATIONS_EXCEED_GIVEN_LIMIT', 263);
