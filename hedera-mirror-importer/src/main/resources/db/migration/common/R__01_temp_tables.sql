---- ${flyway:timestamp} trigger to always run

drop table if exists ${tempSchema}.contract_state_temp;
drop table if exists ${tempSchema}.crypto_allowance_temp;
drop table if exists ${tempSchema}.custom_fee_temp;
drop table if exists ${tempSchema}.dissociate_token_transfer;
drop table if exists ${tempSchema}.entity_stake_temp;
drop table if exists ${tempSchema}.entity_state_start;
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
create unlogged table if not exists ${tempSchema}.entity_stake_temp as table entity_stake limit 0;
create unlogged table if not exists ${tempSchema}.entity_temp as table entity limit 0;
create unlogged table if not exists ${tempSchema}.nft_allowance_temp as table nft_allowance limit 0;
create unlogged table if not exists ${tempSchema}.nft_temp as table nft limit 0;
create unlogged table if not exists ${tempSchema}.schedule_temp as table schedule limit 0;
create unlogged table if not exists ${tempSchema}.token_account_temp as table token_account limit 0;
create unlogged table if not exists ${tempSchema}.token_allowance_temp as table token_allowance limit 0;
create unlogged table if not exists ${tempSchema}.token_temp as table token limit 0;
create unlogged table if not exists ${tempSchema}.topic_message_lookup_temp as table topic_message_lookup limit 0;

create unlogged table if not exists ${tempSchema}.entity_state_start (
  balance            bigint not null,
  id                 bigint not null,
  staked_account_id  bigint not null,
  staked_node_id     bigint not null,
  stake_period_start bigint not null
);

alter table if exists ${tempSchema}.contract_state_temp owner to temporary_admin;
alter table if exists ${tempSchema}.crypto_allowance_temp owner to temporary_admin;
alter table if exists ${tempSchema}.custom_fee_temp owner to temporary_admin;
alter table if exists ${tempSchema}.dissociate_token_transfer owner to temporary_admin;
alter table if exists ${tempSchema}.entity_stake_temp owner to temporary_admin;
alter table if exists ${tempSchema}.entity_state_start owner to temporary_admin;
alter table if exists ${tempSchema}.entity_temp owner to temporary_admin;
alter table if exists ${tempSchema}.nft_allowance_temp owner to temporary_admin;
alter table if exists ${tempSchema}.nft_temp owner to temporary_admin;
alter table if exists ${tempSchema}.schedule_temp owner to temporary_admin;
alter table if exists ${tempSchema}.token_account_temp owner to temporary_admin;
alter table if exists ${tempSchema}.token_allowance_temp owner to temporary_admin;
alter table if exists ${tempSchema}.token_temp owner to temporary_admin;
alter table if exists ${tempSchema}.topic_message_lookup_temp owner to temporary_admin;

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

alter table if exists ${tempSchema}.entity_stake_temp set (
    autovacuum_enabled = false
    );

alter table if exists ${tempSchema}.entity_state_start set (
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
