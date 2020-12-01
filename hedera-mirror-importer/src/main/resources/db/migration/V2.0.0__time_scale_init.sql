-------------------
-- Init mirror node db, defining table schema
-- Supports mirror nodes migrated from v1.0
-------------------

-- domains
create domain hbar_tinybars as bigint;
create domain entity_num as integer;
create domain entity_realm_num as smallint;
create domain entity_type_id as character(1);
create domain entity_id as bigint;
create domain nanos_timestamp as bigint; -- dropped using this domain in some tables as it's needed as a bigint for hyper table partitioning

-- account_balance
create table if not exists account_balance (
    consensus_timestamp     bigint              not null,
    balance                 hbar_tinybars       not null,
    account_id              entity_id           not null
);
comment on table account_balance is 'account balances (historical) in tinybars at different consensus timestamps';

create table if not exists account_balance_file (
    consensus_timestamp     bigint          primary key,
    count                   bigint          not null,
    load_start              bigint,
    load_end                bigint,
    file_hash               varchar(96),
	name                    varchar(250)    not null,
    node_account_id         entity_id       not null
);

-- account_balance_sets
create table if not exists account_balance_sets (
    consensus_timestamp bigint not null,
    is_complete boolean not null default false,
    processing_start_timestamp timestamp without time zone null default (now() at time zone 'utc'),
    processing_end_timestamp timestamp without time zone null,
    constraint pk__account_balance_sets primary key (consensus_timestamp)
);
comment on table account_balance_sets is 'processing state of snapshots of the entire set of account balances at different consensus timestamps';

-- address_book
create table if not exists address_book
(
    start_consensus_timestamp   bigint      primary key,
    end_consensus_timestamp     bigint      null,
    file_id                     entity_id   not null,
    node_count                  int         null,
    file_data                   bytea       not null
);


-- address_book_entry
create table if not exists address_book_entry
(
    id                      serial          primary key,
    consensus_timestamp     bigint          references address_book (start_consensus_timestamp) not null,
    ip                      varchar(128)    null,
    port                    integer         null,
    memo                    varchar(128)    null,
    public_key              varchar(1024)   null,
    node_id                 bigint          null,
    node_account_id         entity_id       null,
    node_cert_hash          bytea           null
);

-- contract_result
create table if not exists contract_result
(
    function_parameters     bytea   null,
    gas_supplied            bigint  null,
    call_result             bytea   null,
    gas_used                bigint  null,
    consensus_timestamp     bigint  not null
);

-- crypto_transfer
create table if not exists crypto_transfer
(
    entity_id               entity_id       not null,
    consensus_timestamp     bigint          not null,
    amount                  hbar_tinybars   not null
);

-- file_data
create table if not exists file_data (
    file_data           bytea       null,
    consensus_timestamp bigint      not null,
    entity_id           entity_id   not null,
    transaction_type    smallint    not null
);

-- live_hash
create table if not exists live_hash (
    livehash            bytea,
    consensus_timestamp bigint not null
);

-- non_fee_transfer
create table if not exists non_fee_transfer (
    entity_id           entity_id       not null,
    consensus_timestamp bigint          not null,
    amount              hbar_tinybars   not null
);

-- record_file
-- id seq from v1.0 no longer explicitly created as s_record_files_seq
create table if not exists record_file (
    id              serial      primary key,
    name            character   varying(250)  not null,
    load_start      bigint,
    load_end        bigint,
    file_hash       character   varying(96),
    prev_hash       character   varying(96),
    consensus_start bigint      default 0 not null,
    consensus_end   bigint      default 0 not null,
    node_account_id entity_id   not null,
    count           bigint      not null
);

-- t_application_status
create table if not exists t_application_status (
    status_name     character varying(40),
    status_code     character varying(40),
    status_value    character varying(100)
);

insert into t_application_status (status_name, status_code) values ('Last valid downloaded record file name', 'LAST_VALID_DOWNLOADED_RECORD_FILE');
insert into t_application_status (status_name, status_code) values ('Last valid downloaded record file hash', 'LAST_VALID_DOWNLOADED_RECORD_FILE_HASH');
insert into t_application_status (status_name, status_code) values ('Last valid downloaded balance file name', 'LAST_VALID_DOWNLOADED_BALANCE_FILE');
insert into t_application_status (status_name, status_code) values ('Last valid downloaded event file name', 'LAST_VALID_DOWNLOADED_EVENT_FILE');
insert into t_application_status (status_name, status_code) values ('Last valid downloaded event file hash', 'LAST_VALID_DOWNLOADED_EVENT_FILE_HASH');
insert into t_application_status (status_name, status_code) values ('Event hash mismatch bypass until after', 'EVENT_HASH_MISMATCH_BYPASS_UNTIL_AFTER');
insert into t_application_status (status_name, status_code) values ('Record hash mismatch bypass until after', 'RECORD_HASH_MISMATCH_BYPASS_UNTIL_AFTER');
insert into t_application_status (status_name, status_code) values ('Last processed record hash', 'LAST_PROCESSED_RECORD_HASH');
insert into t_application_status (status_name, status_code) values ('Last processed event hash', 'LAST_PROCESSED_EVENT_HASH');

-- t_entities
create table if not exists t_entities (
    entity_num              bigint      not null,
    entity_realm            bigint      not null,
    entity_shard            bigint      not null,
    fk_entity_type_id       integer     not null,
    auto_renew_period       bigint,
    key                     bytea,
    deleted                 boolean     default false,
    exp_time_ns             bigint,
    ed25519_public_key_hex  character   varying,
    submit_key              bytea,
    memo                    text,
    auto_renew_account_id   bigint,
    id                      bigint      not null,
    proxy_account_id        entity_id
);

-- t_entity_types
create table if not exists t_entity_types (
    id      integer     not null,
    name    character   varying(8)
);
insert into t_entity_types (id, name) values (1, 'account');
insert into t_entity_types (id, name) values (2, 'contract');
insert into t_entity_types (id, name) values (3, 'file');
insert into t_entity_types (id, name) values (4, 'topic');
insert into t_entity_types (id, name) values (5, 'token');

-- t_transaction_results
create table if not exists t_transaction_results (
    proto_id    integer     not null,
    result      character   varying(100)
);
insert into t_transaction_results (result, proto_id) values ('OK', 0);
insert into t_transaction_results (result, proto_id) values ('INVALID_TRANSACTION',1);
insert into t_transaction_results (result, proto_id) values ('PAYER_ACCOUNT_NOT_FOUND',2);
insert into t_transaction_results (result, proto_id) values ('INVALID_NODE_ACCOUNT',3);
insert into t_transaction_results (result, proto_id) values ('TRANSACTION_EXPIRED',4);
insert into t_transaction_results (result, proto_id) values ('INVALID_TRANSACTION_START',5);
insert into t_transaction_results (result, proto_id) values ('INVALID_TRANSACTION_DURATION',6);
insert into t_transaction_results (result, proto_id) values ('INVALID_SIGNATURE',7);
insert into t_transaction_results (result, proto_id) values ('MEMO_TOO_LONG',8);
insert into t_transaction_results (result, proto_id) values ('INSUFFICIENT_TX_FEE',9);
insert into t_transaction_results (result, proto_id) values ('INSUFFICIENT_PAYER_BALANCE',10);
insert into t_transaction_results (result, proto_id) values ('DUPLICATE_TRANSACTION',11);
insert into t_transaction_results (result, proto_id) values ('BUSY',12);
insert into t_transaction_results (result, proto_id) values ('NOT_SUPPORTED',13);
insert into t_transaction_results (result, proto_id) values ('INVALID_FILE_ID',14);
insert into t_transaction_results (result, proto_id) values ('INVALID_ACCOUNT_ID',15);
insert into t_transaction_results (result, proto_id) values ('INVALID_CONTRACT_ID',16);
insert into t_transaction_results (result, proto_id) values ('INVALID_TRANSACTION_ID',17);
insert into t_transaction_results (result, proto_id) values ('RECEIPT_NOT_FOUND',18);
insert into t_transaction_results (result, proto_id) values ('RECORD_NOT_FOUND',19);
insert into t_transaction_results (result, proto_id) values ('INVALID_SOLIDITY_ID',20);
insert into t_transaction_results (result, proto_id) values ('UNKNOWN',21);
insert into t_transaction_results (result, proto_id) values ('SUCCESS',22);
insert into t_transaction_results (result, proto_id) values ('FAIL_INVALID',23);
insert into t_transaction_results (result, proto_id) values ('FAIL_FEE',24);
insert into t_transaction_results (result, proto_id) values ('FAIL_BALANCE',25);
insert into t_transaction_results (result, proto_id) values ('KEY_REQUIRED',26);
insert into t_transaction_results (result, proto_id) values ('BAD_ENCODING',27);
insert into t_transaction_results (result, proto_id) values ('INSUFFICIENT_ACCOUNT_BALANCE',28);
insert into t_transaction_results (result, proto_id) values ('INVALID_SOLIDITY_ADDRESS',29);
insert into t_transaction_results (result, proto_id) values ('INSUFFICIENT_GAS',30);
insert into t_transaction_results (result, proto_id) values ('CONTRACT_SIZE_LIMIT_EXCEEDED',31);
insert into t_transaction_results (result, proto_id) values ('LOCAL_CALL_MODIFICATION_EXCEPTION',32);
insert into t_transaction_results (result, proto_id) values ('CONTRACT_REVERT_EXECUTED',33);
insert into t_transaction_results (result, proto_id) values ('CONTRACT_EXECUTION_EXCEPTION',34);
insert into t_transaction_results (result, proto_id) values ('INVALID_RECEIVING_NODE_ACCOUNT',35);
insert into t_transaction_results (result, proto_id) values ('MISSING_QUERY_HEADER',36);
insert into t_transaction_results (result, proto_id) values ('ACCOUNT_UPDATE_FAILED',37);
insert into t_transaction_results (result, proto_id) values ('INVALID_KEY_ENCODING',38);
insert into t_transaction_results (result, proto_id) values ('NULL_SOLIDITY_ADDRESS',39);
insert into t_transaction_results (result, proto_id) values ('CONTRACT_UPDATE_FAILED',40);
insert into t_transaction_results (result, proto_id) values ('INVALID_QUERY_HEADER',41);
insert into t_transaction_results (result, proto_id) values ('INVALID_FEE_SUBMITTED',42);
insert into t_transaction_results (result, proto_id) values ('INVALID_PAYER_SIGNATURE',43);
insert into t_transaction_results (result, proto_id) values ('KEY_NOT_PROVIDED',44);
insert into t_transaction_results (result, proto_id) values ('INVALID_EXPIRATION_TIME',45);
insert into t_transaction_results (result, proto_id) values ('NO_WACL_KEY',46);
insert into t_transaction_results (result, proto_id) values ('FILE_CONTENT_EMPTY',47);
insert into t_transaction_results (result, proto_id) values ('INVALID_ACCOUNT_AMOUNTS',48);
insert into t_transaction_results (result, proto_id) values ('EMPTY_TRANSACTION_BODY',49);
insert into t_transaction_results (result, proto_id) values ('INVALID_TRANSACTION_BODY',50);
insert into t_transaction_results (result, proto_id) values ('INVALID_SIGNATURE_TYPE_MISMATCHING_KEY',51);
insert into t_transaction_results (result, proto_id) values ('INVALID_SIGNATURE_COUNT_MISMATCHING_KEY',52);
insert into t_transaction_results (result, proto_id) values ('EMPTY_CLAIM_BODY',53);
insert into t_transaction_results (result, proto_id) values ('EMPTY_CLAIM_HASH',54);
insert into t_transaction_results (result, proto_id) values ('EMPTY_CLAIM_KEYS',55);
insert into t_transaction_results (result, proto_id) values ('INVALID_CLAIM_HASH_SIZE',56);
insert into t_transaction_results (result, proto_id) values ('EMPTY_QUERY_BODY',57);
insert into t_transaction_results (result, proto_id) values ('EMPTY_CLAIM_QUERY',58);
insert into t_transaction_results (result, proto_id) values ('CLAIM_NOT_FOUND',59);
insert into t_transaction_results (result, proto_id) values ('ACCOUNT_ID_DOES_NOT_EXIST',60);
insert into t_transaction_results (result, proto_id) values ('CLAIM_ALREADY_EXISTS',61);
insert into t_transaction_results (result, proto_id) values ('INVALID_FILE_WACL',62);
insert into t_transaction_results (result, proto_id) values ('SERIALIZATION_FAILED',63);
insert into t_transaction_results (result, proto_id) values ('TRANSACTION_OVERSIZE',64);
insert into t_transaction_results (result, proto_id) values ('TRANSACTION_TOO_MANY_LAYERS',65);
insert into t_transaction_results (result, proto_id) values ('CONTRACT_DELETED',66);
insert into t_transaction_results (result, proto_id) values ('PLATFORM_NOT_ACTIVE',67);
insert into t_transaction_results (result, proto_id) values ('KEY_PREFIX_MISMATCH',68);
insert into t_transaction_results (result, proto_id) values ('PLATFORM_TRANSACTION_NOT_CREATED',69);
insert into t_transaction_results (result, proto_id) values ('INVALID_RENEWAL_PERIOD',70);
insert into t_transaction_results (result, proto_id) values ('INVALID_PAYER_ACCOUNT_ID',71);
insert into t_transaction_results (result, proto_id) values ('ACCOUNT_DELETED',72);
insert into t_transaction_results (result, proto_id) values ('FILE_DELETED',73);
insert into t_transaction_results (result, proto_id) values ('ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS',74);
insert into t_transaction_results (result, proto_id) values ('SETTING_NEGATIVE_ACCOUNT_BALANCE',75);
insert into t_transaction_results (result, proto_id) values ('OBTAINER_REQUIRED',76);
insert into t_transaction_results (result, proto_id) values ('OBTAINER_SAME_CONTRACT_ID',77);
insert into t_transaction_results (result, proto_id) values ('OBTAINER_DOES_NOT_EXIST',78);
insert into t_transaction_results (result, proto_id) values ('MODIFYING_IMMUTABLE_CONTRACT',79);
insert into t_transaction_results (result, proto_id) values ('FILE_SYSTEM_EXCEPTION',80);
insert into t_transaction_results (result, proto_id) values ('AUTORENEW_DURATION_NOT_IN_RANGE',81);
insert into t_transaction_results (result, proto_id) values ('ERROR_DECODING_BYTESTRING',82);
insert into t_transaction_results (result, proto_id) values ('CONTRACT_FILE_EMPTY',83);
insert into t_transaction_results (result, proto_id) values ('CONTRACT_BYTECODE_EMPTY',84);
insert into t_transaction_results (result, proto_id) values ('INVALID_INITIAL_BALANCE',85);
insert into t_transaction_results (result, proto_id) values ('INVALID_RECEIVE_RECORD_THRESHOLD',86);
insert into t_transaction_results (result, proto_id) values ('INVALID_SEND_RECORD_THRESHOLD',87);
insert into t_transaction_results (result, proto_id) values ('ACCOUNT_IS_NOT_GENESIS_ACCOUNT',88);
insert into t_transaction_results (proto_id, result) values (89,'PAYER_ACCOUNT_UNAUTHORIZED');
insert into t_transaction_results (proto_id, result) values (90,'INVALID_FREEZE_TRANSACTION_BODY');
insert into t_transaction_results (proto_id, result) values (91,'FREEZE_TRANSACTION_BODY_NOT_FOUND');
insert into t_transaction_results (proto_id, result) values (92,'TRANSFER_LIST_SIZE_LIMIT_EXCEEDED');
insert into t_transaction_results (proto_id, result) values (93,'RESULT_SIZE_LIMIT_EXCEEDED');
insert into t_transaction_results (proto_id, result) values (94,'NOT_SPECIAL_ACCOUNT');
insert into t_transaction_results (proto_id, result) values (95,'CONTRACT_NEGATIVE_GAS');
insert into t_transaction_results (proto_id, result) values (96,'CONTRACT_NEGATIVE_VALUE');
insert into t_transaction_results (proto_id, result) values (97,'INVALID_FEE_FILE');
insert into t_transaction_results (proto_id, result) values (98,'INVALID_EXCHANGE_RATE_FILE');
insert into t_transaction_results (proto_id, result) values (99,'INSUFFICIENT_LOCAL_CALL_GAS');
insert into t_transaction_results (proto_id, result) values (100,'ENTITY_NOT_ALLOWED_TO_DELETE');
insert into t_transaction_results (proto_id, result) values (101,'AUTHORIZATION_FAILED');
insert into t_transaction_results (proto_id, result) values (102,'FILE_UPLOADED_PROTO_INVALID');
insert into t_transaction_results (proto_id, result) values (103,'FILE_UPLOADED_PROTO_NOT_SAVED_TO_DISK');
insert into t_transaction_results (proto_id, result) values (104,'FEE_SCHEDULE_FILE_PART_UPLOADED');
insert into t_transaction_results (proto_id, result) values (105,'EXCHANGE_RATE_CHANGE_LIMIT_EXCEEDED');
insert into t_transaction_results (proto_id, result) values (106,'MAX_CONTRACT_STORAGE_EXCEEDED');
insert into t_transaction_results (proto_id, result) values (111,'MAX_GAS_LIMIT_EXCEEDED');
insert into t_transaction_results (proto_id, result) values (112,'MAX_FILE_SIZE_EXCEEDED');
insert into t_transaction_results (proto_id, result) values (150, 'INVALID_TOPIC_ID');
insert into t_transaction_results (proto_id, result) values (155, 'INVALID_ADMIN_KEY'); -- 151-154 were deleted in proto
insert into t_transaction_results (proto_id, result) values (156, 'INVALID_SUBMIT_KEY');
insert into t_transaction_results (proto_id, result) values (157, 'UNAUTHORIZED');
insert into t_transaction_results (proto_id, result) values (158, 'INVALID_TOPIC_MESSAGE');
insert into t_transaction_results (proto_id, result) values (159, 'INVALID_AUTORENEW_ACCOUNT');
insert into t_transaction_results (proto_id, result) values (160, 'AUTORENEW_ACCOUNT_NOT_ALLOWED');
insert into t_transaction_results (proto_id, result) values (162, 'TOPIC_EXPIRED');
insert into t_transaction_results (proto_id, result) values (163,'INVALID_CHUNK_NUMBER');
insert into t_transaction_results (proto_id, result) values (164,'INVALID_CHUNK_TRANSACTION_ID');
insert into t_transaction_results (proto_id, result) values
    (165, 'ACCOUNT_FROZEN_FOR_TOKEN'),
    (166, 'TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED'),
    (167, 'INVALID_TOKEN_ID'),
    (168, 'INVALID_TOKEN_DECIMALS'),
    (169, 'INVALID_TOKEN_INITIAL_SUPPLY'),
    (170, 'INVALID_TREASURY_ACCOUNT_FOR_TOKEN'),
    (171, 'INVALID_TOKEN_SYMBOL'),
    (172, 'TOKEN_HAS_NO_FREEZE_KEY'),
    (173, 'TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN'),
    (174, 'MISSING_TOKEN_SYMBOL'),
    (175, 'TOKEN_SYMBOL_TOO_LONG'),
    (176, 'ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN'),
    (177, 'TOKEN_HAS_NO_KYC_KEY'),
    (178, 'INSUFFICIENT_TOKEN_BALANCE'),
    (179, 'TOKEN_WAS_DELETED'),
    (180, 'TOKEN_HAS_NO_SUPPLY_KEY'),
    (181, 'TOKEN_HAS_NO_WIPE_KEY'),
    (182, 'INVALID_TOKEN_MINT_AMOUNT'),
    (183, 'INVALID_TOKEN_BURN_AMOUNT'),
    (184, 'TOKEN_NOT_ASSOCIATED_TO_ACCOUNT'),
    (185, 'CANNOT_WIPE_TOKEN_TREASURY_ACCOUNT'),
    (186, 'INVALID_KYC_KEY'),
    (187, 'INVALID_WIPE_KEY'),
    (188, 'INVALID_FREEZE_KEY'),
    (189, 'INVALID_SUPPLY_KEY'),
    (190, 'MISSING_TOKEN_NAME'),
    (191, 'TOKEN_NAME_TOO_LONG'),
    (192, 'INVALID_WIPING_AMOUNT'),
    (193, 'TOKEN_IS_IMMUTABLE'),
    (194, 'TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT'),
    (195, 'TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES'),
    (196, 'ACCOUNT_IS_TREASURY');

-- t_transaction_types
create table if not exists t_transaction_types (
    proto_id    integer     not null,
    name        character   varying(30),
    entity_type integer     null
);
insert into t_transaction_types (proto_id, name, entity_type) values (7,'CONTRACTCALL', 2);
insert into t_transaction_types (proto_id, name, entity_type) values (8,'CONTRACTCREATEINSTANCE', 2);
insert into t_transaction_types (proto_id, name, entity_type) values (9,'CONTRACTUPDATEINSTANCE', 2);
insert into t_transaction_types (proto_id, name) values (10,'CRYPTOADDLIVEHASH');
insert into t_transaction_types (proto_id, name, entity_type) values (11,'CRYPTOCREATEACCOUNT', 1);
insert into t_transaction_types (proto_id, name, entity_type) values (12,'CRYPTODELETE', 1);
insert into t_transaction_types (proto_id, name) values (13,'CRYPTODELETELIVEHASH');
insert into t_transaction_types (proto_id, name) values (14,'CRYPTOTRANSFER');
insert into t_transaction_types (proto_id, name, entity_type) values (15,'CRYPTOUPDATEACCOUNT', 1);
insert into t_transaction_types (proto_id, name, entity_type) values (16,'FILEAPPEND', 3);
insert into t_transaction_types (proto_id, name, entity_type) values (17,'FILECREATE', 3);
insert into t_transaction_types (proto_id, name, entity_type) values (18,'FILEDELETE', 3);
insert into t_transaction_types (proto_id, name, entity_type) values (19,'FILEUPDATE', 3);
insert into t_transaction_types (proto_id, name) values (20,'SYSTEMDELETE');
insert into t_transaction_types (proto_id, name) values (21,'SYSTEMUNDELETE');
insert into t_transaction_types (proto_id, name, entity_type) values (22,'CONTRACTDELETEINSTANCE', 2);
insert into t_transaction_types (proto_id, name) values (23,'FREEZE');
insert into t_transaction_types (proto_id, name, entity_type) values (24,'CONSENSUSCREATETOPIC', 4);
insert into t_transaction_types (proto_id, name, entity_type) values (25,'CONSENSUSUPDATETOPIC', 4);
insert into t_transaction_types (proto_id, name, entity_type) values (26,'CONSENSUSDELETETOPIC', 4);
insert into t_transaction_types (proto_id, name, entity_type) values (27,'CONSENSUSSUBMITMESSAGE', 4);
insert into t_transaction_types (proto_id, name, entity_type) values
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
    (41, 'TOKENDISSOCIATE', 1);

-- token
create table if not exists token
(
    token_id                bigint                  primary key,
    created_timestamp       bigint                  not null,
    decimals                bigint                  not null,
    freeze_default          boolean                 not null default false,
    freeze_key              bytea,
    freeze_key_ed25519_hex  varchar                 null,
    initial_supply          bigint                  not null,
    kyc_key                 bytea,
    kyc_key_ed25519_hex     varchar                 null,
    modified_timestamp      bigint                  not null,
    name                    character varying(100)  not null,
    supply_key              bytea,
    supply_key_ed25519_hex  varchar                 null,
    symbol                  character varying(100)  not null,
    total_supply            bigint                  not null default 0,
    treasury_account_id     entity_id               not null,
    wipe_key                bytea,
    wipe_key_ed25519_hex    varchar                 null
);

--- token_account
create table if not exists token_account
(
    id                  serial              primary key,
    account_id          entity_id           not null,
    associated          boolean             not null default false,
    created_timestamp   bigint              not null,
    freeze_status       smallint            not null default 0,
    kyc_status          smallint            not null default 0,
    modified_timestamp  bigint              not null,
    token_id            entity_id           not null
);

--- token_balance
create table if not exists token_balance
(
    consensus_timestamp bigint              not null,
    account_id          entity_id           not null,
    balance             bigint              not null,
    token_id            entity_id           not null
);

--- token_transfer
create table if not exists token_transfer
(
    token_id            entity_id       not null,
    account_id          entity_id       not null,
    consensus_timestamp bigint          not null,
    amount              hbar_tinybars   not null
);

-- topic_message
create table if not exists topic_message
(
    consensus_timestamp     bigint              primary key not null,
    realm_num               entity_realm_num    not null,
    topic_num               entity_num          not null,
    message                 bytea               not null,
    running_hash            bytea               not null,
    sequence_number         bigint              not null,
    running_hash_version    smallint            not null,
    chunk_num               integer,
    chunk_total             integer,
    payer_account_id        entity_id,
    valid_start_timestamp   nanos_timestamp
);

-- transaction
create table if not exists transaction (
    consensus_ns            bigint              not null,
    type                    smallint            not null,
    result                  smallint            not null,
    payer_account_id        entity_id           not null,
    valid_start_ns          bigint              not null,
    valid_duration_seconds  bigint,
    node_account_id         entity_id           not null,
    entity_id               entity_id,
    initial_balance         bigint              default 0,
    max_fee                 hbar_tinybars,
    charged_tx_fee          bigint,
    memo                    bytea,
    transaction_hash        bytea,
    transaction_bytes       bytea
);
