-------------------
-- Add support for automatic token associations
-------------------


CREATE TYPE token_pause_status AS ENUM ('NOT_APPLICABLE', 'PAUSED', 'UNPAUSED');

-- TODO verify these two
insert into t_transaction_types (proto_id, name, entity_type)
(46, 'TOKENPAUSE', 5),
(47, 'TOKENUNPAUSE', 5);

alter table if exists token
    add column pause_status token_pause_status not null default NOT_APPLICABLE,
    add column pause_key bytea,
    add column pause_key_ed25519_hex varchar null;
