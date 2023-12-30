---- ${flyway:timestamp} trigger to always run

drop table if exists ${tempSchema}.contract_state_temp;
drop table if exists ${tempSchema}.crypto_allowance_temp;
drop table if exists ${tempSchema}.custom_fee_temp;
drop table if exists ${tempSchema}.dissociate_token_transfer;
drop table if exists ${tempSchema}.entity_temp;
drop table if exists ${tempSchema}.nft_allowance_temp;
drop table if exists ${tempSchema}.nft_temp;
drop table if exists ${tempSchema}.schedule_temp;
drop table if exists ${tempSchema}.token_account_temp;
drop table if exists ${tempSchema}.token_allowance_temp;
drop table if exists ${tempSchema}.token_temp;
drop table if exists ${tempSchema}.topic_message_lookup_temp;

create unlogged table if not exists ${tempSchema}.contract_state_temp as table contract_state limit 0;
create unlogged table if not exists ${tempSchema}.crypto_allowance_temp as table crypto_allowance limit 0;
create unlogged table if not exists ${tempSchema}.custom_fee_temp as table custom_fee limit 0;
create unlogged table if not exists ${tempSchema}.dissociate_token_transfer as table token_transfer limit 0;
create unlogged table if not exists ${tempSchema}.entity_temp as table entity limit 0;
create unlogged table if not exists ${tempSchema}.nft_allowance_temp as table nft_allowance limit 0;
create unlogged table if not exists ${tempSchema}.nft_temp as table nft limit 0;
create unlogged table if not exists ${tempSchema}.schedule_temp as table schedule limit 0;
create unlogged table if not exists ${tempSchema}.token_account_temp as table token_account limit 0;
create unlogged table if not exists ${tempSchema}.token_allowance_temp as table token_allowance limit 0;
create unlogged table if not exists ${tempSchema}.token_temp as table token limit 0;
create unlogged table if not exists ${tempSchema}.topic_message_lookup_temp as table topic_message_lookup limit 0;

alter table if exists ${tempSchema}.contract_state_temp owner to temporary_admin;
alter table if exists ${tempSchema}.crypto_allowance_temp owner to temporary_admin;
alter table if exists ${tempSchema}.custom_fee_temp owner to temporary_admin;
alter table if exists ${tempSchema}.dissociate_token_transfer owner to temporary_admin;
alter table if exists ${tempSchema}.entity_temp owner to temporary_admin;
alter table if exists ${tempSchema}.nft_allowance_temp owner to temporary_admin;
alter table if exists ${tempSchema}.nft_temp owner to temporary_admin;
alter table if exists ${tempSchema}.schedule_temp owner to temporary_admin;
alter table if exists ${tempSchema}.token_account_temp owner to temporary_admin;
alter table if exists ${tempSchema}.token_allowance_temp owner to temporary_admin;
alter table if exists ${tempSchema}.token_temp owner to temporary_admin;
alter table if exists ${tempSchema}.topic_message_lookup_temp owner to temporary_admin;

create index if not exists contract_state_temp_idx on ${tempSchema}.contract_state_temp (contract_id,slot);
create index if not exists crypto_allowance_temp_idx on ${tempSchema}.crypto_allowance_temp (owner, spender);
create index if not exists custom_fee_temp_idx on ${tempSchema}.custom_fee_temp (token_id);
create index if not exists dissociate_token_transfer_idx on ${tempSchema}.dissociate_token_transfer (token_id, account_id);
create index if not exists entity_temp_idx on ${tempSchema}.entity_temp (id);
create index if not exists nft_allowance_temp_idx on ${tempSchema}.nft_allowance_temp (owner, spender, token_id);
create index if not exists nft_temp_idx on ${tempSchema}.nft_temp (token_id, serial_number);
create index if not exists schedule_temp_idx on ${tempSchema}.schedule_temp (schedule_id);
create index if not exists token_account_temp_idx on ${tempSchema}.token_account_temp (account_id, token_id);
create index if not exists token_allowance_temp_idx on ${tempSchema}.token_allowance_temp (owner, spender, token_id);
create index if not exists token_temp_idx on ${tempSchema}.token_temp (token_id);
create index if not exists topic_message_lookup_temp_idx on ${tempSchema}.topic_message_lookup_temp (topic_id, partition);

alter table if exists ${tempSchema}.contract_state_temp set (
    autovacuum_enabled = false
    );

alter table if exists ${tempSchema}.crypto_allowance_temp set (
    autovacuum_enabled = false
    );

alter table if exists ${tempSchema}.custom_fee_temp set (
    autovacuum_enabled = false
    );

alter table if exists ${tempSchema}.dissociate_token_transfer set (
    autovacuum_enabled = false
    );

alter table if exists ${tempSchema}.entity_temp set (
    autovacuum_enabled = false
    );

alter table if exists ${tempSchema}.nft_allowance_temp set (
    autovacuum_enabled = false
    );

alter table if exists ${tempSchema}.nft_temp set (
    autovacuum_enabled = false
    );

alter table if exists ${tempSchema}.schedule_temp set (
    autovacuum_enabled = false
    );

alter table if exists ${tempSchema}.token_account_temp set (
    autovacuum_enabled = false
    );

alter table if exists ${tempSchema}.token_allowance_temp set (
    autovacuum_enabled = false
    );

alter table if exists ${tempSchema}.token_temp set (
    autovacuum_enabled = false
    );

alter table if exists ${tempSchema}.topic_message_lookup_temp set (
    autovacuum_enabled = false
    );
