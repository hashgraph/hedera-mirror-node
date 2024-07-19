create type airdrop_state as enum ('PENDING', 'CANCELLED', 'CLAIMED');

create table if not exists token_airdrop
(
    amount              bigint,
    receiver_account_id bigint         not null,
    sender_account_id   bigint         not null,
    serial_number       bigint,
    state               airdrop_state  not null default 'PENDING',
    timestamp_range     int8range      not null,
    token_id            bigint         not null
);

create unique index if not exists token_airdrop__sender_id on token_airdrop (sender_account_id, receiver_account_id, token_id, serial_number);
create index if not exists token_airdrop__receiver_id on token_airdrop (receiver_account_id, sender_account_id, token_id, serial_number);

create table if not exists token_airdrop_history
(
    like token_airdrop including defaults
);

create index if not exists token_airdrop_history__token_serial_lower_timestamp
    on token_airdrop_history using gist (timestamp_range);

drop table if exists ${tempSchema}.token_airdrop_temp;
create unlogged table if not exists ${tempSchema}.token_airdrop_temp as table token_airdrop limit 0;
alter table if exists ${tempSchema}.token_airdrop_temp owner to temporary_admin;
create index if not exists token_airdrop_temp_idx on ${tempSchema}.token_airdrop_temp (sender_account_id, receiver_account_id, token_id, serial_number  );
alter table if exists ${tempSchema}.token_airdrop_temp set (
    autovacuum_enabled = false
    );