-------------------
-- Create distributed tables for tables that can be distributed around an entity.
-- Colocate where possible with matching entity
-------------------


-- Entity tables
SELECT create_distributed_table('entity', 'id');
SELECT create_distributed_table('contract', 'id');
SELECT create_distributed_table('schedule', 'schedule_id', colocate_with => 'entity');
SELECT create_distributed_table('token', 'token_id', colocate_with => 'entity');


SELECT create_distributed_table('entity_history', 'id', colocate_with => 'entity');
SELECT create_distributed_table('contract_history', 'id', colocate_with => 'contract');

-- Entity sub-tables


SELECT create_distributed_table('account_balance', 'account_id', colocate_with => 'entity');

SELECT create_distributed_table('account_balance_file', 'node_account_id', colocate_with => 'entity');

SELECT create_distributed_table('contract_log', 'payer_account_id', colocate_with => 'entity');

SELECT create_distributed_table('contract_result', 'payer_account_id', colocate_with => 'entity');

SELECT create_distributed_table('custom_fee', 'token_id', colocate_with => 'token');

SELECT create_distributed_table('event_file', 'node_account_id', colocate_with => 'entity');

SELECT create_distributed_table('file_data', 'entity_id', colocate_with => 'contract');

SELECT create_distributed_table('nft', 'token_id', colocate_with => 'token');

SELECT create_distributed_table('record_file', 'node_account_id', colocate_with => 'entity');

SELECT create_distributed_table('token_account', 'token_id', colocate_with => 'token');

SELECT create_distributed_table('token_balance', 'account_id', colocate_with => 'entity');

SELECT create_distributed_table('topic_message', 'topic_id', colocate_with => 'entity');

SELECT create_distributed_table('transaction', 'payer_account_id', colocate_with => 'entity');

SELECT create_distributed_table('transaction_signature', 'entity_id', colocate_with => 'transaction');
