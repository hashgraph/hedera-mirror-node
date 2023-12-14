---- ${flyway:timestamp} trigger to always run

drop schema if exists ${tempSchema} cascade;
create schema ${tempSchema} authorization ${tempSchema}_admin;

grant usage on schema ${tempSchema} to public;
revoke create on schema ${tempSchema} from public;
grant select on all tables in schema ${tempSchema} to readonly;
grant select on all sequences in schema ${tempSchema} to readonly;
grant usage on schema ${tempSchema} to readonly;
alter default privileges in schema ${tempSchema} grant select on tables to readonly;
alter default privileges in schema ${tempSchema} grant select on sequences to readonly;

-- Grant readwrite privileges
grant insert, update, delete on all tables in schema ${tempSchema} to readwrite;
grant usage on all sequences in schema ${tempSchema} to readwrite;
alter default privileges in schema ${tempSchema} grant insert, update, delete on tables to readwrite;
alter default privileges in schema ${tempSchema} grant usage on sequences to readwrite;

create unlogged table if not exists ${tempSchema}.contract_state_temp as table contract_state limit 0;
create unlogged table if not exists ${tempSchema}.crypto_allowance_temp as table crypto_allowance limit 0;
create unlogged table if not exists ${tempSchema}.custom_fee_temp as table custom_fee limit 0;
create unlogged table if not exists ${tempSchema}.entity_stake_temp as table entity_stake limit 0;
create unlogged table if not exists ${tempSchema}.entity_temp as table entity limit 0;
create unlogged table if not exists ${tempSchema}.nft_allowance_temp as table nft_allowance limit 0;
create unlogged table if not exists ${tempSchema}.nft_temp as table nft limit 0;
create unlogged table if not exists ${tempSchema}.schedule_temp as table schedule limit 0;
create unlogged table if not exists ${tempSchema}.token_account_temp as table token_account limit 0;
create unlogged table if not exists ${tempSchema}.token_allowance_temp as table token_allowance limit 0;
create unlogged table if not exists ${tempSchema}.dissociate_token_transfer as table token_transfer limit 0;
create unlogged table if not exists ${tempSchema}.token_temp as table token limit 0;
create unlogged table if not exists ${tempSchema}.topic_message_lookup_temp as table topic_message_lookup limit 0;

alter table if exists ${tempSchema}.contract_state_temp owner to ${tempSchema}_admin;
alter table if exists ${tempSchema}.crypto_allowance_temp owner to ${tempSchema}_admin;
alter table if exists ${tempSchema}.custom_fee_temp owner to ${tempSchema}_admin;
alter table if exists ${tempSchema}.entity_temp owner to ${tempSchema}_admin;
alter table if exists ${tempSchema}.entity_stake_temp owner to ${tempSchema}_admin;
alter table if exists ${tempSchema}.nft_allowance_temp owner to ${tempSchema}_admin;
alter table if exists ${tempSchema}.nft_temp owner to ${tempSchema}_admin;
alter table if exists ${tempSchema}.schedule_temp owner to ${tempSchema}_admin;
alter table if exists ${tempSchema}.token_account_temp owner to ${tempSchema}_admin;
alter table if exists ${tempSchema}.token_allowance_temp owner to ${tempSchema}_admin;
alter table if exists ${tempSchema}.dissociate_token_transfer owner to ${tempSchema}_admin;
alter table if exists ${tempSchema}.token_temp owner to ${tempSchema}_admin;
alter table if exists ${tempSchema}.topic_message_lookup_temp owner to ${tempSchema}_admin;

create index if not exists contract_state_temp_idx on ${tempSchema}.contract_state_temp (contract_id,slot);
create index if not exists crypto_allowance_temp_idx on ${tempSchema}.crypto_allowance_temp (owner, spender);
create index if not exists custom_fee_temp_idx on ${tempSchema}.custom_fee_temp (token_id);
create index if not exists entity_stake_temp_idx on ${tempSchema}.entity_stake_temp (id);
create index if not exists entity_temp_idx on ${tempSchema}.entity_temp (id);
create index if not exists nft_allowance_temp_idx on ${tempSchema}.nft_allowance_temp (owner, spender, token_id);
create index if not exists nft_temp_idx on ${tempSchema}.nft_temp (token_id, serial_number);
create index if not exists schedule_temp_idx on ${tempSchema}.schedule_temp (schedule_id);
create index if not exists token_account_temp_idx on ${tempSchema}.token_account_temp (account_id, token_id);
create index if not exists token_allowance_temp_idx on ${tempSchema}.token_allowance_temp (owner, spender, token_id);
create index if not exists dissociate_token_transfer_idx on ${tempSchema}.dissociate_token_transfer (token_id, account_id);
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

alter table if exists ${tempSchema}.entity_stake_temp set (
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

alter table if exists ${tempSchema}.dissociate_token_transfer set (
    autovacuum_enabled = false
    );

alter table if exists ${tempSchema}.token_temp set (
    autovacuum_enabled = false
    );

alter table if exists ${tempSchema}.topic_message_lookup_temp set (
    autovacuum_enabled = false
    );

select create_distributed_table('${tempSchema}.contract_state_temp', 'contract_id', colocate_with => 'contract_state');
select create_distributed_table('${tempSchema}.crypto_allowance_temp', 'owner', colocate_with => 'crypto_allowance');
select create_distributed_table('${tempSchema}.custom_fee_temp', 'token_id', colocate_with => 'custom_fee');
select create_distributed_table('${tempSchema}.entity_stake_temp', 'id', colocate_with => 'entity_stake');
select create_distributed_table('${tempSchema}.entity_temp', 'id', colocate_with := 'entity');
select create_distributed_table('${tempSchema}.nft_allowance_temp', 'owner', colocate_with => 'nft_allowance');
select create_distributed_table('${tempSchema}.nft_temp', 'token_id', colocate_with => 'nft');
select create_distributed_table('${tempSchema}.schedule_temp', 'schedule_id', colocate_with => 'schedule');
select create_distributed_table('${tempSchema}.token_account_temp', 'account_id', colocate_with => 'token_account');
select create_distributed_table('${tempSchema}.token_allowance_temp', 'owner', colocate_with => 'token_allowance');
select create_distributed_table('${tempSchema}.dissociate_token_transfer', 'payer_account_id', colocate_with => 'token_transfer');
select create_distributed_table('${tempSchema}.token_temp', 'token_id', colocate_with => 'token');
select create_distributed_table('${tempSchema}.topic_message_lookup_temp', 'topic_id', colocate_with => 'topic_message_lookup');
