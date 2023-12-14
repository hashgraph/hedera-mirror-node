---- ${flyway:timestamp} trigger to always run

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
