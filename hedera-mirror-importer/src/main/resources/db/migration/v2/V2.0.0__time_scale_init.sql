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
    bytes               bytea        null,
    consensus_timestamp bigint       not null,
    count               bigint       not null,
    file_hash           varchar(96)  null,
    load_start          bigint       not null,
    load_end            bigint       not null,
    name                varchar(250) not null,
    node_account_id     bigint       not null
);
comment on table account_balance_file is 'Account balances stream files';

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
    consensus_timestamp bigint        not null,
    description         varchar(100)  null,
    memo                varchar(128)  null,
    public_key          varchar(1024) null,
    node_id             bigint        not null,
    node_account_id     bigint        not null,
    node_cert_hash      bytea         null,
    stake               bigint        null
);
comment on table address_book_entry is 'Network address book node entries';

-- address_book_service_endpoint
create table if not exists address_book_service_endpoint
(
    consensus_timestamp bigint      not null,
    ip_address_v4       varchar(15) not null,
    node_id             bigint      not null,
    port                integer     default -1 not null
);
comment on table address_book_service_endpoint is 'Network address book node service endpoints';

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

-- entity
create table if not exists entity
(
    auto_renew_account_id bigint,
    auto_renew_period     bigint,
    created_timestamp     bigint,
    deleted               boolean default false not null,
    expiration_timestamp  bigint,
    id                    bigint                not null,
    key                   bytea,
    memo                  text    default ''    not null,
    modified_timestamp    bigint,
    num                   bigint                not null,
    proxy_account_id      bigint,
    public_key            character varying,
    realm                 bigint                not null,
    shard                 bigint                not null,
    submit_key            bytea,
    type                  integer               not null
);
comment on table entity is 'Network entity with state';

-- event_file
create table if not exists event_file
(
    bytes            bytea                  null,
    consensus_start  bigint                 not null,
    consensus_end    bigint                 not null,
    count            bigint                 not null,
    digest_algorithm int                    not null,
    file_hash        character varying(96)  not null,
    hash             character varying(96)  not null,
    load_start       bigint                 not null,
    load_end         bigint                 not null,
    name             character varying(250) not null,
    node_account_id  bigint                 not null,
    previous_hash    character varying(96)  not null,
    version          integer                not null
);

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
create table if not exists record_file
(
    bytes              bytea                  null,
    consensus_start    bigint                 not null,
    consensus_end      bigint                 not null,
    count              bigint                 not null,
    digest_algorithm   int                    not null,
    file_hash          character varying(96)  not null,
    hapi_version_major int,
    hapi_version_minor int,
    hapi_version_patch int,
    hash               character varying(96)  not null,
    index              bigint                 not null,
    load_start         bigint                 not null,
    load_end           bigint                 not null,
    name               character varying(250) not null,
    node_account_id    bigint                 not null,
    prev_hash          character varying(96)  not null,
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

-- transaction_signature
create table if not exists transaction_signature
(
    consensus_timestamp bigint not null,
    public_key_prefix   bytea  not null,
    entity_id           bigint null,
    signature           bytea  not null
);
comment on table transaction_signature is 'Transaction signatories';

-- t_entity_types
create table if not exists t_entity_types
(
    id   integer not null,
    name character varying(8)
);
comment on table t_entity_types is 'Network entity types';

-- t_transaction_results
create table if not exists t_transaction_results
(
    proto_id integer not null,
    result   character varying(100)
);
comment on table t_transaction_results is 'Transaction result types';

-- t_transaction_types
create table if not exists t_transaction_types
(
    proto_id    integer not null,
    name        character varying(30),
    entity_type integer null
);
comment on table t_transaction_types is 'Transaction types';

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
comment on table token is 'Token entity';

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
comment on table token is 'Token account entity';

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
    node_account_id        bigint,
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
