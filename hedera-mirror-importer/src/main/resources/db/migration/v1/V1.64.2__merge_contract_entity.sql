alter table if exists entity
    drop constraint if exists entity_type_enum_check,
    add column if not exists obtainer_id       bigint  null,
    add column if not exists permanent_removal boolean null;

alter table if exists entity_history
    drop constraint if exists entity_history_type_enum_check,
    add column if not exists obtainer_id       bigint  null,
    add column if not exists permanent_removal boolean null;

insert
into entity (auto_renew_account_id,
             auto_renew_period,
             created_timestamp,
             decline_reward,
             deleted,
             evm_address,
             expiration_timestamp,
             id,
             key,
             max_automatic_token_associations,
             memo,
             num,
             obtainer_id,
             permanent_removal,
             proxy_account_id,
             public_key,
             realm,
             shard,
             staked_account_id,
             staked_node_id,
             stake_period_start,
             timestamp_range,
             type)
select auto_renew_account_id,
       auto_renew_period,
       created_timestamp,
       decline_reward,
       deleted,
       evm_address,
       expiration_timestamp,
       id,
       key,
       max_automatic_token_associations,
       memo,
       num,
       obtainer_id,
       permanent_removal,
       proxy_account_id,
       public_key,
       realm,
       shard,
       staked_account_id,
       staked_node_id,
       stake_period_start,
       timestamp_range,
       type
from contract;

insert
into entity_history (auto_renew_account_id,
                     auto_renew_period,
                     created_timestamp,
                     decline_reward,
                     deleted,
                     evm_address,
                     expiration_timestamp,
                     id,
                     key,
                     max_automatic_token_associations,
                     memo,
                     num,
                     obtainer_id,
                     permanent_removal,
                     proxy_account_id,
                     public_key,
                     realm,
                     shard,
                     staked_account_id,
                     staked_node_id,
                     stake_period_start,
                     timestamp_range,
                     type)
select auto_renew_account_id,
       auto_renew_period,
       created_timestamp,
       decline_reward,
       deleted,
       evm_address,
       expiration_timestamp,
       id,
       key,
       max_automatic_token_associations,
       memo,
       num,
       obtainer_id,
       permanent_removal,
       proxy_account_id,
       public_key,
       realm,
       shard,
       staked_account_id,
       staked_node_id,
       stake_period_start,
       timestamp_range,
       type
from contract_history;

alter table if exists contract
    drop constraint if exists contract_type_check,
    drop column if exists auto_renew_account_id,
    drop column if exists auto_renew_period,
    drop column if exists created_timestamp,
    drop column if exists decline_reward,
    drop column if exists deleted,
    drop column if exists evm_address,
    drop column if exists expiration_timestamp,
    drop column if exists key,
    drop column if exists max_automatic_token_associations,
    drop column if exists memo,
    drop column if exists num,
    drop column if exists obtainer_id,
    drop column if exists permanent_removal,
    drop column if exists proxy_account_id,
    drop column if exists public_key,
    drop column if exists realm,
    drop column if exists shard,
    drop column if exists staked_account_id,
    drop column if exists staked_node_id,
    drop column if exists stake_period_start,
    drop column if exists timestamp_range,
    drop column if exists type;

drop table if exists contract_history;