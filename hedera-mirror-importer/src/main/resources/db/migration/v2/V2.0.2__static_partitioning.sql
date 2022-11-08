-------------------
-- Add non-repeatable partitioning logic to large tables.
-------------------

select partman.create_parent('public.account_balance', 'consensus_timestamp', 'native', 'monthly', p_epoch := 'nanoseconds' , p_premake := 2, p_start_partition := to_char(CURRENT_TIMESTAMP-'3 years'::interval, 'YYYY-MM-DD HH24:MI:SS'));
select partman.create_parent('public.address_book', 'start_consensus_timestamp', 'native', 'monthly', p_epoch := 'nanoseconds' , p_premake := 2, p_start_partition := to_char(CURRENT_TIMESTAMP-'3 years'::interval, 'YYYY-MM-DD HH24:MI:SS'));
select partman.create_parent('public.address_book_entry', 'consensus_timestamp', 'native', 'monthly', p_epoch := 'nanoseconds' , p_premake := 2, p_start_partition := to_char(CURRENT_TIMESTAMP-'3 years'::interval, 'YYYY-MM-DD HH24:MI:SS'));
select partman.create_parent('public.address_book_service_endpoint', 'consensus_timestamp', 'native', 'monthly', p_epoch := 'nanoseconds' , p_premake := 2, p_start_partition := to_char(CURRENT_TIMESTAMP-'3 years'::interval, 'YYYY-MM-DD HH24:MI:SS'));
select partman.create_parent('public.assessed_custom_fee', 'consensus_timestamp', 'native', 'monthly', p_epoch := 'nanoseconds' , p_premake := 2, p_start_partition := to_char(CURRENT_TIMESTAMP-'3 years'::interval, 'YYYY-MM-DD HH24:MI:SS'));
select partman.create_parent('public.contract', 'id', 'native', '100000');
select partman.create_parent('public.contract_action', 'consensus_timestamp', 'native', 'monthly', p_epoch := 'nanoseconds' , p_premake := 2, p_start_partition := to_char(CURRENT_TIMESTAMP-'3 years'::interval, 'YYYY-MM-DD HH24:MI:SS'));
select partman.create_parent('public.contract_log', 'consensus_timestamp', 'native', 'monthly', p_epoch := 'nanoseconds' , p_premake := 2, p_start_partition := to_char(CURRENT_TIMESTAMP-'3 years'::interval, 'YYYY-MM-DD HH24:MI:SS'));
select partman.create_parent('public.contract_result', 'consensus_timestamp', 'native', 'monthly', p_epoch := 'nanoseconds' , p_premake := 2, p_start_partition := to_char(CURRENT_TIMESTAMP-'3 years'::interval, 'YYYY-MM-DD HH24:MI:SS'));
select partman.create_parent('public.contract_state', 'contract_id', 'native', '100000');
select partman.create_parent('public.contract_state_change', 'contract_id', 'native', '100000');
select partman.create_parent('public.crypto_allowance', 'owner', 'native', '100000');
select partman.create_parent('public.crypto_allowance_history', 'owner', 'native', '100000');
select partman.create_parent('public.crypto_transfer', 'consensus_timestamp', 'native', 'monthly', p_epoch := 'nanoseconds' , p_premake := 2, p_start_partition := to_char(CURRENT_TIMESTAMP-'3 years'::interval, 'YYYY-MM-DD HH24:MI:SS'));
select partman.create_parent('public.custom_fee', 'created_timestamp', 'native', 'monthly', p_epoch := 'nanoseconds' , p_premake := 2, p_start_partition := to_char(CURRENT_TIMESTAMP-'3 years'::interval, 'YYYY-MM-DD HH24:MI:SS'));
select partman.create_parent('public.entity', 'id', 'native', '100000');
select partman.create_parent('public.entity_history', 'id', 'native', '100000');
select partman.create_parent('public.entity_stake', 'id', 'native', '100000');
select partman.create_parent('public.ethereum_transaction', 'consensus_timestamp', 'native', 'monthly', p_epoch := 'nanoseconds' , p_premake := 2, p_start_partition := to_char(CURRENT_TIMESTAMP-'3 years'::interval, 'YYYY-MM-DD HH24:MI:SS'));
select partman.create_parent('public.event_file', 'consensus_end', 'native', 'monthly', p_epoch := 'nanoseconds' , p_premake := 2, p_start_partition := to_char(CURRENT_TIMESTAMP-'3 years'::interval, 'YYYY-MM-DD HH24:MI:SS'));
select partman.create_parent('public.file_data', 'consensus_timestamp', 'native', 'monthly', p_epoch := 'nanoseconds' , p_premake := 2, p_start_partition := to_char(CURRENT_TIMESTAMP-'3 years'::interval, 'YYYY-MM-DD HH24:MI:SS'));
select partman.create_parent('public.nft', 'token_id', 'native', '100000');
select partman.create_parent('public.nft_allowance', 'owner', 'native', '100000');
select partman.create_parent('public.nft_allowance_history', 'owner', 'native', '100000');
select partman.create_parent('public.nft_transfer', 'consensus_timestamp', 'native', 'monthly', p_epoch := 'nanoseconds' , p_premake := 2, p_start_partition := to_char(CURRENT_TIMESTAMP-'3 years'::interval, 'YYYY-MM-DD HH24:MI:SS'));
select partman.create_parent('public.non_fee_transfer', 'consensus_timestamp', 'native', 'monthly', p_epoch := 'nanoseconds' , p_premake := 2, p_start_partition := to_char(CURRENT_TIMESTAMP-'3 years'::interval, 'YYYY-MM-DD HH24:MI:SS'));
select partman.create_parent('public.record_file', 'consensus_end', 'native', 'monthly', p_epoch := 'nanoseconds' , p_premake := 2, p_start_partition := to_char(CURRENT_TIMESTAMP-'3 years'::interval, 'YYYY-MM-DD HH24:MI:SS'));
select partman.create_parent('public.schedule', 'schedule_id', 'native', '100000');
select partman.create_parent('public.staking_reward_transfer', 'consensus_timestamp', 'native', 'monthly', p_epoch := 'nanoseconds' , p_premake := 2, p_start_partition := to_char(CURRENT_TIMESTAMP-'3 years'::interval, 'YYYY-MM-DD HH24:MI:SS'));
select partman.create_parent('public.token', 'token_id', 'native', '100000');
select partman.create_parent('public.token_account', 'token_id', 'native', '100000');
select partman.create_parent('public.token_account_history', 'token_id', 'native', '100000');
select partman.create_parent('public.token_allowance', 'owner', 'native', '100000');
select partman.create_parent('public.token_allowance_history', 'owner', 'native', '100000');
select partman.create_parent('public.token_balance', 'consensus_timestamp', 'native', 'monthly', p_epoch := 'nanoseconds' , p_premake := 2, p_start_partition := to_char(CURRENT_TIMESTAMP-'3 years'::interval, 'YYYY-MM-DD HH24:MI:SS'));
select partman.create_parent('public.token_transfer', 'consensus_timestamp', 'native', 'monthly', p_epoch := 'nanoseconds' , p_premake := 2, p_start_partition := to_char(CURRENT_TIMESTAMP-'3 years'::interval, 'YYYY-MM-DD HH24:MI:SS'));
select partman.create_parent('public.topic_message', 'consensus_timestamp', 'native', 'monthly', p_epoch := 'nanoseconds' , p_premake := 2, p_start_partition := to_char(CURRENT_TIMESTAMP-'3 years'::interval, 'YYYY-MM-DD HH24:MI:SS'));
select partman.create_parent('public.transaction', 'consensus_timestamp', 'native', 'monthly', '{"type"}', p_epoch := 'nanoseconds' , p_premake := 2, p_start_partition := to_char(CURRENT_TIMESTAMP-'3 years'::interval, 'YYYY-MM-DD HH24:MI:SS'));
select partman.create_parent('public.transaction_hash', 'consensus_timestamp', 'native', 'monthly', p_epoch := 'nanoseconds' , p_premake := 2, p_start_partition := to_char(CURRENT_TIMESTAMP-'3 years'::interval, 'YYYY-MM-DD HH24:MI:SS'));
select partman.create_parent('public.transaction_signature', 'entity_id', 'native', '100000');
