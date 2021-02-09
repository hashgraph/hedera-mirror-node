-------------------
-- Init mirror node db, defining table schema
-- Supports mirror nodes migrated from v1.0
-------------------

-- account_balance
create table if not exists account_balance
(
    consensus_timestamp bigint not null,
    balance             bigint not null,
    account_id          bigint not null
);
comment on table account_balance is 'Account balances (historical) in tinybars at different consensus timestamps';

create table if not exists account_balance_file
(
    consensus_timestamp bigint       not null,
    count               bigint       not null,
    load_start          bigint,
    load_end            bigint,
    file_hash           varchar(96),
    name                varchar(250) not null,
    node_account_id     bigint       not null
);
comment on table account_balance_file is 'Account balances stream files';

-- account_balance_sets
create table if not exists account_balance_sets
(
    consensus_timestamp        bigint                      not null,
    is_complete                boolean                     not null default false,
    processing_start_timestamp timestamp without time zone null     default (now() at time zone 'utc'),
    processing_end_timestamp   timestamp without time zone null
);
comment on table account_balance_sets is 'Processing state of snapshots of the entire set of account balances at different consensus timestamps';

-- address_book
create table if not exists address_book
(
    start_consensus_timestamp bigint not null,
    end_consensus_timestamp   bigint null,
    file_id                   bigint not null,
    node_count                int    null,
    file_data                 bytea  not null
);
comment on table address_book is 'Network address book files entries';

-- address_book_entry
create table if not exists address_book_entry
(
    id                  serial,
    consensus_timestamp bigint        not null,
    ip                  varchar(128)  null,
    port                integer       null,
    memo                varchar(128)  null,
    public_key          varchar(1024) null,
    node_id             bigint        null,
    node_account_id     bigint        null,
    node_cert_hash      bytea         null
);
comment on table address_book_entry is 'Network address book node entries';

-- contract_result
create table if not exists contract_result
(
    function_parameters bytea  null,
    gas_supplied        bigint null,
    call_result         bytea  null,
    gas_used            bigint null,
    consensus_timestamp bigint not null
);
comment on table contract_result is 'Crypto contract execution results';

-- crypto_transfer
create table if not exists crypto_transfer
(
    entity_id           bigint not null,
    consensus_timestamp bigint not null,
    amount              bigint not null
);
comment on table crypto_transfer is 'Crypto account Hbar transfers';

-- file_data
create table if not exists file_data
(
    file_data           bytea    null,
    consensus_timestamp bigint   not null,
    entity_id           bigint   not null,
    transaction_type    smallint not null
);
comment on table file_data is 'File data entity entries';

-- live_hash
create table if not exists live_hash
(
    livehash            bytea,
    consensus_timestamp bigint not null
);

-- non_fee_transfer
create table if not exists non_fee_transfer
(
    entity_id           bigint not null,
    consensus_timestamp bigint not null,
    amount              bigint not null
);
comment on table non_fee_transfer is 'Crypto account non fee Hbar transfers';

-- record_file
-- id seq from v1.0 no longer explicitly created as s_record_files_seq
create table if not exists record_file
(
    id                 serial,
    name               character varying(250) not null,
    load_start         bigint,
    load_end           bigint,
    hash               character varying(96),
    file_hash          character varying(96),
    prev_hash          character varying(96),
    consensus_start    bigint default 0       not null,
    consensus_end      bigint default 0       not null,
    node_account_id    bigint                 not null,
    count              bigint                 not null,
    digest_algorithm   int                    not null,
    hapi_version_major int,
    hapi_version_minor int,
    hapi_version_patch int,
    version            int                    not null
);
comment on table record_file is 'Network record file stream entries';

-- schedule
create table if not exists schedule
(
    consensus_timestamp bigint primary key not null,
    creator_account_id  bigint             not null,
    executed_timestamp  bigint             null,
    payer_account_id    bigint             not null,
    schedule_id         bigint             not null,
    transaction_body    bytea              not null
);
comment on table schedule is 'Schedule entity entries';

-- schedule_signature
create table if not exists schedule_signature
(
    consensus_timestamp bigint not null,
    public_key_prefix   bytea  not null,
    schedule_id         bigint not null,
    signature           bytea  not null
);
comment on table schedule is 'Schedule transaction signatories';

-- t_entities
create table if not exists t_entities
(
    entity_num             bigint  not null,
    entity_realm           bigint  not null,
    entity_shard           bigint  not null,
    fk_entity_type_id      integer not null,
    auto_renew_period      bigint,
    key                    bytea,
    deleted                boolean default false,
    exp_time_ns            bigint,
    ed25519_public_key_hex character varying,
    submit_key             bytea,
    memo                   text,
    auto_renew_account_id  bigint,
    id                     bigint  not null,
    proxy_account_id       bigint
);
comment on table t_entities is 'Network entities with state';

-- t_entity_types
create table if not exists t_entity_types
(
    id   integer not null,
    name character varying(8)
);
comment on table t_entity_types is 'Network entity types';

insert into t_entity_types (id, name)
values (1, 'account'),
       (2, 'contract'),
       (3, 'file'),
       (4, 'topic'),
       (5, 'token'),
       (6, 'schedule');

-- t_transaction_results
create table if not exists t_transaction_results
(
    proto_id integer not null,
    result   character varying(100)
);
comment on table t_transaction_results is 'Transaction result types';

insert into t_transaction_results (result, proto_id)
values ('OK', 0),
       ('INVALID_TRANSACTION', 1),
       ('PAYER_ACCOUNT_NOT_FOUND', 2),
       ('INVALID_NODE_ACCOUNT', 3),
       ('TRANSACTION_EXPIRED', 4),
       ('INVALID_TRANSACTION_START', 5),
       ('INVALID_TRANSACTION_DURATION', 6),
       ('INVALID_SIGNATURE', 7),
       ('MEMO_TOO_LONG', 8),
       ('INSUFFICIENT_TX_FEE', 9),
       ('INSUFFICIENT_PAYER_BALANCE', 10),
       ('DUPLICATE_TRANSACTION', 11),
       ('BUSY', 12),
       ('NOT_SUPPORTED', 13),
       ('INVALID_FILE_ID', 14),
       ('INVALID_ACCOUNT_ID', 15),
       ('INVALID_CONTRACT_ID', 16),
       ('INVALID_TRANSACTION_ID', 17),
       ('RECEIPT_NOT_FOUND', 18),
       ('RECORD_NOT_FOUND', 19),
       ('INVALID_SOLIDITY_ID', 20),
       ('UNKNOWN', 21),
       ('SUCCESS', 22),
       ('FAIL_INVALID', 23),
       ('FAIL_FEE', 24),
       ('FAIL_BALANCE', 25),
       ('KEY_REQUIRED', 26),
       ('BAD_ENCODING', 27),
       ('INSUFFICIENT_ACCOUNT_BALANCE', 28),
       ('INVALID_SOLIDITY_ADDRESS', 29),
       ('INSUFFICIENT_GAS', 30),
       ('CONTRACT_SIZE_LIMIT_EXCEEDED', 31),
       ('LOCAL_CALL_MODIFICATION_EXCEPTION', 32),
       ('CONTRACT_REVERT_EXECUTED', 33),
       ('CONTRACT_EXECUTION_EXCEPTION', 34),
       ('INVALID_RECEIVING_NODE_ACCOUNT', 35),
       ('MISSING_QUERY_HEADER', 36),
       ('ACCOUNT_UPDATE_FAILED', 37),
       ('INVALID_KEY_ENCODING', 38),
       ('NULL_SOLIDITY_ADDRESS', 39),
       ('CONTRACT_UPDATE_FAILED', 40),
       ('INVALID_QUERY_HEADER', 41),
       ('INVALID_FEE_SUBMITTED', 42),
       ('INVALID_PAYER_SIGNATURE', 43),
       ('KEY_NOT_PROVIDED', 44),
       ('INVALID_EXPIRATION_TIME', 45),
       ('NO_WACL_KEY', 46),
       ('FILE_CONTENT_EMPTY', 47),
       ('INVALID_ACCOUNT_AMOUNTS', 48),
       ('EMPTY_TRANSACTION_BODY', 49),
       ('INVALID_TRANSACTION_BODY', 50),
       ('INVALID_SIGNATURE_TYPE_MISMATCHING_KEY', 51),
       ('INVALID_SIGNATURE_COUNT_MISMATCHING_KEY', 52),
       ('EMPTY_CLAIM_BODY', 53),
       ('EMPTY_CLAIM_HASH', 54),
       ('EMPTY_CLAIM_KEYS', 55),
       ('INVALID_CLAIM_HASH_SIZE', 56),
       ('EMPTY_QUERY_BODY', 57),
       ('EMPTY_CLAIM_QUERY', 58),
       ('CLAIM_NOT_FOUND', 59),
       ('ACCOUNT_ID_DOES_NOT_EXIST', 60),
       ('CLAIM_ALREADY_EXISTS', 61),
       ('INVALID_FILE_WACL', 62),
       ('SERIALIZATION_FAILED', 63),
       ('TRANSACTION_OVERSIZE', 64),
       ('TRANSACTION_TOO_MANY_LAYERS', 65),
       ('CONTRACT_DELETED', 66),
       ('PLATFORM_NOT_ACTIVE', 67),
       ('KEY_PREFIX_MISMATCH', 68),
       ('PLATFORM_TRANSACTION_NOT_CREATED', 69),
       ('INVALID_RENEWAL_PERIOD', 70),
       ('INVALID_PAYER_ACCOUNT_ID', 71),
       ('ACCOUNT_DELETED', 72),
       ('FILE_DELETED', 73),
       ('ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS', 74),
       ('SETTING_NEGATIVE_ACCOUNT_BALANCE', 75),
       ('OBTAINER_REQUIRED', 76),
       ('OBTAINER_SAME_CONTRACT_ID', 77),
       ('OBTAINER_DOES_NOT_EXIST', 78),
       ('MODIFYING_IMMUTABLE_CONTRACT', 79),
       ('FILE_SYSTEM_EXCEPTION', 80),
       ('AUTORENEW_DURATION_NOT_IN_RANGE', 81),
       ('ERROR_DECODING_BYTESTRING', 82),
       ('CONTRACT_FILE_EMPTY', 83),
       ('CONTRACT_BYTECODE_EMPTY', 84),
       ('INVALID_INITIAL_BALANCE', 85),
       ('INVALID_RECEIVE_RECORD_THRESHOLD', 86),
       ('INVALID_SEND_RECORD_THRESHOLD', 87),
       ('ACCOUNT_IS_NOT_GENESIS_ACCOUNT', 88),
       ('PAYER_ACCOUNT_UNAUTHORIZED', 89),
       ('INVALID_FREEZE_TRANSACTION_BODY', 90),
       ('FREEZE_TRANSACTION_BODY_NOT_FOUND', 91),
       ('TRANSFER_LIST_SIZE_LIMIT_EXCEEDED', 92),
       ('RESULT_SIZE_LIMIT_EXCEEDED', 93),
       ('NOT_SPECIAL_ACCOUNT', 94),
       ('CONTRACT_NEGATIVE_GAS', 95),
       ('CONTRACT_NEGATIVE_VALUE', 96),
       ('INVALID_FEE_FILE', 97),
       ('INVALID_EXCHANGE_RATE_FILE', 98),
       ('INSUFFICIENT_LOCAL_CALL_GAS', 99),
       ('ENTITY_NOT_ALLOWED_TO_DELETE', 100),
       ('AUTHORIZATION_FAILED', 101),
       ('FILE_UPLOADED_PROTO_INVALID', 102),
       ('FILE_UPLOADED_PROTO_NOT_SAVED_TO_DISK', 103),
       ('FEE_SCHEDULE_FILE_PART_UPLOADED', 104),
       ('EXCHANGE_RATE_CHANGE_LIMIT_EXCEEDED', 105),
       ('MAX_CONTRACT_STORAGE_EXCEEDED', 106),
       ('MAX_GAS_LIMIT_EXCEEDED', 111),
       ('MAX_FILE_SIZE_EXCEEDED', 112),
       ('INVALID_TOPIC_ID', 150),
       ('INVALID_ADMIN_KEY', 155),
       ('INVALID_SUBMIT_KEY', 156),
       ('UNAUTHORIZED', 157),
       ('INVALID_TOPIC_MESSAGE', 158),
       ('INVALID_AUTORENEW_ACCOUNT', 159),
       ('AUTORENEW_ACCOUNT_NOT_ALLOWED', 160),
       ('TOPIC_EXPIRED', 162),
       ('INVALID_CHUNK_NUMBER', 163),
       ('INVALID_CHUNK_TRANSACTION_ID', 164),
       ('ACCOUNT_FROZEN_FOR_TOKEN', 165),
       ('TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED', 166),
       ('INVALID_TOKEN_ID', 167),
       ('INVALID_TOKEN_DECIMALS', 168),
       ('INVALID_TOKEN_INITIAL_SUPPLY', 169),
       ('INVALID_TREASURY_ACCOUNT_FOR_TOKEN', 170),
       ('INVALID_TOKEN_SYMBOL', 171),
       ('TOKEN_HAS_NO_FREEZE_KEY', 172),
       ('TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN', 173),
       ('MISSING_TOKEN_SYMBOL', 174),
       ('TOKEN_SYMBOL_TOO_LONG', 175),
       ('ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN', 176),
       ('TOKEN_HAS_NO_KYC_KEY', 177),
       ('INSUFFICIENT_TOKEN_BALANCE', 178),
       ('TOKEN_WAS_DELETED', 179),
       ('TOKEN_HAS_NO_SUPPLY_KEY', 180),
       ('TOKEN_HAS_NO_WIPE_KEY', 181),
       ('INVALID_TOKEN_MINT_AMOUNT', 182),
       ('INVALID_TOKEN_BURN_AMOUNT', 183),
       ('TOKEN_NOT_ASSOCIATED_TO_ACCOUNT', 184),
       ('CANNOT_WIPE_TOKEN_TREASURY_ACCOUNT', 185),
       ('INVALID_KYC_KEY', 186),
       ('INVALID_WIPE_KEY', 187),
       ('INVALID_FREEZE_KEY', 188),
       ('INVALID_SUPPLY_KEY', 189),
       ('MISSING_TOKEN_NAME', 190),
       ('TOKEN_NAME_TOO_LONG', 191),
       ('INVALID_WIPING_AMOUNT', 192),
       ('TOKEN_IS_IMMUTABLE', 193),
       ('TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT', 194),
       ('TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES', 195),
       ('ACCOUNT_IS_TREASURY', 196),
       ('TOKEN_ID_REPEATED_IN_TOKEN_LIST', 197),
       ('TOKEN_TRANSFER_LIST_SIZE_LIMIT_EXCEEDED', 198),
       ('EMPTY_TOKEN_TRANSFER_BODY', 199),
       ('EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS', 200),
       ('INVALID_SCHEDULE_ID', 201),
       ('SCHEDULE_IS_IMMUTABLE', 202),
       ('INVALID_SCHEDULE_PAYER_ID', 203),
       ('INVALID_SCHEDULE_ACCOUNT_ID', 204),
       ('NO_NEW_VALID_SIGNATURES', 205),
       ('UNRESOLVABLE_REQUIRED_SIGNERS', 206),
       ('UNPARSEABLE_SCHEDULED_TRANSACTION', 207),
       ('UNSCHEDULABLE_TRANSACTION', 208),
       ('SOME_SIGNATURES_WERE_INVALID', 209),
       ('TRANSACTION_ID_FIELD_NOT_ALLOWED', 210);

-- t_transaction_types
create table if not exists t_transaction_types
(
    proto_id    integer not null,
    name        character varying(30),
    entity_type integer null
);
comment on table t_transaction_types is 'Transaction types';

insert into t_transaction_types (proto_id, name, entity_type)
values (7, 'CONTRACTCALL', 2),
       (8, 'CONTRACTCREATEINSTANCE', 2),
       (9, 'CONTRACTUPDATEINSTANCE', 2),
       (10, 'CRYPTOADDLIVEHASH', null),
       (11, 'CRYPTOCREATEACCOUNT', 1),
       (12, 'CRYPTODELETE', 1),
       (13, 'CRYPTODELETELIVEHASH', null),
       (14, 'CRYPTOTRANSFER', null),
       (15, 'CRYPTOUPDATEACCOUNT', 1),
       (16, 'FILEAPPEND', 3),
       (17, 'FILECREATE', 3),
       (18, 'FILEDELETE', 3),
       (19, 'FILEUPDATE', 3),
       (20, 'SYSTEMDELETE', null),
       (21, 'SYSTEMUNDELETE', null),
       (22, 'CONTRACTDELETEINSTANCE', 2),
       (23, 'FREEZE', null),
       (24, 'CONSENSUSCREATETOPIC', 4),
       (25, 'CONSENSUSUPDATETOPIC', 4),
       (26, 'CONSENSUSDELETETOPIC', 4),
       (27, 'CONSENSUSSUBMITMESSAGE', 4),
       (28, 'UNCHECKEDSUBMIT', null),
       (29, 'TOKENCREATION', 5),
       (31, 'TOKENFREEZE', 1),
       (32, 'TOKENUNFREEZE', 1),
       (33, 'TOKENGRANTKYC', 1),
       (34, 'TOKENREVOKEKYC', 1),
       (35, 'TOKENDELETION', 5),
       (36, 'TOKENUPDATE', 5),
       (37, 'TOKENMINT', 5),
       (38, 'TOKENBURN', 5),
       (39, 'TOKENWIPE', 5),
       (40, 'TOKENASSOCIATE', 1),
       (41, 'TOKENDISSOCIATE', 1),
       (42, 'SCHEDULECREATE', 6),
       (43, 'SCHEDULEDELETE', 6),
       (44, 'SCHEDULESIGN', 6);

-- token
create table if not exists token
(
    token_id               bigint,
    created_timestamp      bigint                 not null,
    decimals               bigint                 not null,
    freeze_default         boolean                not null default false,
    freeze_key             bytea,
    freeze_key_ed25519_hex varchar                null,
    initial_supply         bigint                 not null,
    kyc_key                bytea,
    kyc_key_ed25519_hex    varchar                null,
    modified_timestamp     bigint                 not null,
    name                   character varying(100) not null,
    supply_key             bytea,
    supply_key_ed25519_hex varchar                null,
    symbol                 character varying(100) not null,
    total_supply           bigint                 not null default 0,
    treasury_account_id    bigint                 not null,
    wipe_key               bytea,
    wipe_key_ed25519_hex   varchar                null
);
comment on table token is 'Token entities';

--- token_account
create table if not exists token_account
(
    account_id         bigint   not null,
    associated         boolean  not null default false,
    created_timestamp  bigint   not null,
    freeze_status      smallint not null default 0,
    kyc_status         smallint not null default 0,
    modified_timestamp bigint   not null,
    token_id           bigint   not null
);
comment on table token is 'Token account entities';

--- token_balance
create table if not exists token_balance
(
    consensus_timestamp bigint not null,
    account_id          bigint not null,
    balance             bigint not null,
    token_id            bigint not null
);
comment on table token_balance is 'Crypto account token balances';

--- token_transfer
create table if not exists token_transfer
(
    token_id            bigint not null,
    account_id          bigint not null,
    consensus_timestamp bigint not null,
    amount              bigint not null
);
comment on table token_transfer is 'Crypto account token transfers';

-- topic_message
create table if not exists topic_message
(
    consensus_timestamp   bigint   not null,
    realm_num             smallint not null,
    topic_num             integer  not null,
    message               bytea    not null,
    running_hash          bytea    not null,
    sequence_number       bigint   not null,
    running_hash_version  smallint not null,
    chunk_num             integer,
    chunk_total           integer,
    payer_account_id      bigint,
    valid_start_timestamp bigint
);
comment on table topic_message is 'Topic entity sequenced messages';

-- transaction
create table if not exists transaction
(
    consensus_ns           bigint   not null,
    type                   smallint not null,
    result                 smallint not null,
    payer_account_id       bigint   not null,
    valid_start_ns         bigint   not null,
    valid_duration_seconds bigint,
    node_account_id        bigint   not null,
    entity_id              bigint,
    initial_balance        bigint            default 0,
    max_fee                bigint,
    charged_tx_fee         bigint,
    memo                   bytea,
    scheduled              boolean  not null default false,
    transaction_hash       bytea,
    transaction_bytes      bytea
);
comment on table transaction is 'Submitted network transactions';
