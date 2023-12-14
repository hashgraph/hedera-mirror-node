---- ${flyway:timestamp} trigger to always run

drop table if exists temporary.contract_state_temp;
drop table if exists temporary.crypto_allowance_temp;
drop table if exists temporary.custom_fee_temp;
drop table if exists temporary.entity_temp;
drop table if exists temporary.entity_stake_temp;
drop table if exists temporary.nft_allowance_temp;
drop table if exists temporary.nft_temp;
drop table if exists temporary.schedule_temp;
drop table if exists temporary.token_account_temp;
drop table if exists temporary.token_allowance_temp;
drop table if exists temporary.token_dissociate_transfer;
drop table if exists temporary.token_temp;
drop table if exists temporary.topic_message_lookup_temp;

create unlogged table if not exists temporary.contract_state_temp as table contract_state limit 0;
create unlogged table if not exists temporary.crypto_allowance_temp as table crypto_allowance limit 0;
create unlogged table if not exists temporary.custom_fee_temp as table custom_fee limit 0;
create unlogged table if not exists temporary.entity_stake_temp as table entity_stake limit 0;
create unlogged table if not exists temporary.entity_temp as table entity limit 0;
create unlogged table if not exists temporary.nft_allowance_temp as table nft_allowance limit 0;
create unlogged table if not exists temporary.nft_temp as table nft limit 0;
create unlogged table if not exists temporary.schedule_temp as table schedule limit 0;
create unlogged table if not exists temporary.token_account_temp as table token_account limit 0;
create unlogged table if not exists temporary.token_allowance_temp as table token_allowance limit 0;
create unlogged table if not exists temporary.token_dissociate_transfer as table token_transfer limit 0;
create unlogged table if not exists temporary.token_temp as table token limit 0;
create unlogged table if not exists temporary.topic_message_lookup_temp as table topic_message_lookup limit 0;

alter table if exists temporary.contract_state_temp owner to schemaadmin;
alter table if exists temporary.crypto_allowance_temp owner to schemaadmin;
alter table if exists temporary.custom_fee_temp owner to schemaadmin;
alter table if exists temporary.entity_temp owner to schemaadmin;
alter table if exists temporary.entity_stake_temp owner to schemaadmin;
alter table if exists temporary.nft_allowance_temp owner to schemaadmin;
alter table if exists temporary.nft_temp owner to schemaadmin;
alter table if exists temporary.schedule_temp owner to schemaadmin;
alter table if exists temporary.token_account_temp owner to schemaadmin;
alter table if exists temporary.token_allowance_temp owner to schemaadmin;
alter table if exists temporary.token_dissociate_transfer owner to schemaadmin;
alter table if exists temporary.token_temp owner to schemaadmin;
alter table if exists temporary.topic_message_lookup_temp owner to schemaadmin;

create index if not exists contract_state_temp_idx on temporary.contract_state_temp (contract_id,slot);
create index if not exists crypto_allowance_temp_idx on temporary.crypto_allowance_temp (owner, spender);
create index if not exists custom_fee_temp_idx on temporary.custom_fee_temp (token_id);
create index if not exists entity_stake_temp_idx on temporary.entity_stake_temp (id);
create index if not exists entity_temp_idx on temporary.entity_temp (id);
create index if not exists nft_allowance_temp_idx on temporary.nft_allowance_temp (owner, spender, token_id);
create index if not exists nft_temp_idx on temporary.nft_temp (token_id, serial_number);
create index if not exists schedule_temp_idx on temporary.schedule_temp (schedule_id);
create index if not exists token_account_temp_idx on temporary.token_account_temp (account_id, token_id);
create index if not exists token_allowance_temp_idx on temporary.token_allowance_temp (owner, spender, token_id);
create index if not exists token_dissociate_transfer_idx on temporary.token_dissociate_transfer (token_id, account_id);
create index if not exists token_temp_idx on temporary.token_temp (token_id);
create index if not exists topic_message_lookup_temp_idx on temporary.topic_message_lookup_temp (topic_id, partition);

alter table if exists temporary.contract_state_temp set (
    autovacuum_enabled = false
    );

alter table if exists temporary.crypto_allowance_temp set (
    autovacuum_enabled = false
    );
alter table if exists temporary.custom_fee_temp set (
    autovacuum_enabled = false
    );

alter table if exists temporary.entity_stake_temp set (
    autovacuum_enabled = false
    );

alter table if exists temporary.entity_temp set (
    autovacuum_enabled = false
    );

alter table if exists temporary.nft_allowance_temp set (
    autovacuum_enabled = false
    );

alter table if exists temporary.nft_temp set (
    autovacuum_enabled = false
    );

alter table if exists temporary.schedule_temp set (
    autovacuum_enabled = false
    );

alter table if exists temporary.token_account_temp set (
    autovacuum_enabled = false
    );

alter table if exists temporary.token_allowance_temp set (
    autovacuum_enabled = false
    );

alter table if exists temporary.token_dissociate_transfer set (
    autovacuum_enabled = false
    );

alter table if exists temporary.token_temp set (
    autovacuum_enabled = false
    );

alter table if exists temporary.topic_message_lookup_temp set (
    autovacuum_enabled = false
    );
