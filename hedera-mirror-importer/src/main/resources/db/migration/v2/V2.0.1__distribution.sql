-------------------
-- Create distributed tables for tables that have mostly insert logic.
-- Colocate where possible with matching entity.
-- Create reference tables for tables used only to describe with rare insert/updates.
-------------------


-- Tables distributed on id
select create_distributed_table('contract', 'id');

select create_distributed_table('entity', 'id');

-- Tables that will be colocated with tables above
select create_distributed_table('account_balance', 'account_id', colocate_with => 'entity');

select create_distributed_table('contract_history', 'id', colocate_with => 'contract');

select create_distributed_table('contract_log', 'payer_account_id', colocate_with => 'entity');

select create_distributed_table('contract_result', 'payer_account_id', colocate_with => 'entity');

select create_distributed_table('contract_state_change', 'payer_account_id', colocate_with => 'entity');

select create_distributed_table('crypto_allowance', 'owner', colocate_with => 'entity');

select create_distributed_table('crypto_allowance_history', 'owner', colocate_with => 'crypto_allowance');

select create_distributed_table('custom_fee', 'token_id', colocate_with => 'entity');

select create_distributed_table('entity_history', 'id', colocate_with => 'entity');

select create_distributed_table('event_file', 'node_account_id', colocate_with => 'entity');

select create_distributed_table('file_data', 'entity_id', colocate_with => 'entity');

select create_distributed_table('nft', 'token_id', colocate_with => 'entity');

select create_distributed_table('nft_allowance', 'owner', colocate_with => 'entity');

select create_distributed_table('nft_allowance_history', 'owner', colocate_with => 'nft_allowance');

select create_distributed_table('record_file', 'node_account_id', colocate_with => 'entity');

select create_distributed_table('schedule', 'schedule_id', colocate_with => 'entity');

select create_distributed_table('token', 'token_id', colocate_with => 'entity');

select create_distributed_table('token_account', 'token_id', colocate_with => 'entity');

select create_distributed_table('token_allowance', 'owner', colocate_with => 'entity');

select create_distributed_table('token_allowance_history', 'owner', colocate_with => 'token_allowance');

select create_distributed_table('token_balance', 'account_id', colocate_with => 'entity');

select create_distributed_table('topic_message', 'topic_id', colocate_with => 'entity');

select create_distributed_table('transaction', 'payer_account_id', colocate_with => 'entity');

select create_distributed_table('transaction_signature', 'entity_id', colocate_with => 'entity');

-- Reference tables
select create_reference_table('account_balance_file');
