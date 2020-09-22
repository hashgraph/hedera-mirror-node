-------------------
-- Support hts transactions
-------------------

-- Add new token entity type
insert into t_entity_types (id, name) values (5, 'token');

-- Add new hts transaction body types
insert into t_transaction_types (proto_id, name) values
    (28, 'UNCHECKEDSUBMIT'),
    (29, 'TOKENCREATE'),
    (30, 'TOKENTRANSFER'),
    (31, 'TOKENFREEZE'),
    (32, 'TOKENUNFREEZE'),
    (33, 'TOKENGRANTKYC'),
    (34, 'TOKENREVOKEKYC'),
    (35, 'TOKENDELETE'),
    (36, 'TOKENUPDATE'),
    (37, 'TOKENMINT'),
    (38, 'TOKENBURN'),
    (39, 'TOKENWIPE');

-- Add hts transaction result types
insert into t_transaction_results (proto_id, result) values
    (165, 'ACCOUNT_FROZEN_FOR_TOKEN'),
    (166, 'TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED'),
    (167, 'INVALID_TOKEN_ID'),
    (168, 'INVALID_TOKEN_DIVISIBILITY'),
    (169, 'INVALID_TOKEN_FLOAT'),
    (170, 'INVALID_TREASURY_ACCOUNT_FOR_TOKEN'),
    (171, 'INVALID_TOKEN_SYMBOL'),
    (172, 'TOKEN_HAS_NO_FREEZE_KEY'),
    (173, 'TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN'),
    (174, 'MISSING_TOKEN_SYMBOL'),
    (175, 'TOKEN_SYMBOL_TOO_LONG'),
    (176, 'TOKEN_SYMBOL_ALREADY_IN_USE'),
    (177, 'INVALID_TOKEN_REF'),
    (178, 'ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN'),
    (179, 'TOKEN_HAS_NO_KYC_KEY'),
    (180, 'INSUFFICIENT_TOKEN_BALANCE'),
    (181, 'TOKEN_WAS_DELETED'),
    (182, 'TOKEN_HAS_NO_SUPPLY_KEY'),
    (183, 'TOKEN_HAS_NO_WIPE_KEY'),
    (184, 'INVALID_TOKEN_MINT_AMOUNT'),
    (185, 'INVALID_TOKEN_BURN_AMOUNT'),
    (186, 'ACCOUNT_HAS_NO_TOKEN_RELATIONSHIP'),
    (187, 'CANNOT_WIPE_TOKEN_TREASURY_ACCOUNT'),
    (188, 'INVALID_KYC_KEY'),
    (189, 'INVALID_WIPE_KEY'),
    (190, 'INVALID_FREEZE_KEY'),
    (191, 'INVALID_SUPPLY_KEY'),
    (192, 'INVALID_TOKEN_EXPIRY'),
    (193, 'TOKEN_HAS_EXPIRED'),
    (194, 'TOKEN_IS_IMMUTABlE');

-- Add token table to hold token properties
create table if not exists token
(
    token_id            entity_id               primary key,
    create_timestamp    bigint                  not null,
    divisibility        bigint                  not null,
    freeze_default      boolean                 not null,
    freeze_key          bytea,
    initial_supply      bigint                  not null,
    kyc_default         boolean                 not null,
    kyc_key             bytea,
    modify_timestamp    bigint,
    supply_key          bytea,
    symbol              character varying(100)  not null,
    treasury_account_id entity_id               not null,
    wipe_key            bytea
);

--- Add token_account table to capture token-account info such as frozen, kyc and wipe status
create table if not exists token_account
(
    id                  serial              primary key,
    account_id          entity_id           not null,
    create_timestamp    bigint              not null,
    frozen              boolean             not null,
    kyc                 boolean             not null,
    modify_timestamp    bigint              not null,
    token_id            entity_id           not null,
    wiped               boolean             not null default false
);

create unique index if not exists token_account__token_account on token_account(token_id, account_id);

create index if not exists token_account__token_account_timestamp
     on token_account (token_id desc, account_id desc, create_timestamp desc);

--- Add token_balance table to capture token account balances
create table if not exists token_balance
(
    consensus_timestamp bigint              primary key not null,
    account_id          entity_id           not null,
    balance             bigint              not null,
    token_id            entity_id           not null
);

create index if not exists token_balance__token_account_timestamp
     on token_balance (token_id desc, account_id desc, consensus_timestamp desc);

--- Add token_transfer
create table if not exists token_transfer
(
    token_id            entity_id,
    account_id          entity_id,
    consensus_timestamp bigint,
    amount              hbar_tinybars
);

create index if not exists token_transfer__timestamp
     on token_transfer (token_id desc, account_id desc, consensus_timestamp desc);

create index if not exists token_transfer__token_account_timestamp
     on token_transfer (token_id desc, account_id desc, consensus_timestamp desc);
