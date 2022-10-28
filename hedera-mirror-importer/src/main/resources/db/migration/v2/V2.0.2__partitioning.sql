-------------------
-- Add partitioning logic to large tables.
-------------------
select partman.create_parent('public.account_balance', 'consensus_timestamp', 'native', 'monthly', p_epoch := 'nanoseconds' , p_premake := 2, p_start_partition := to_char(CURRENT_TIMESTAMP-'3 years'::interval, 'YYYY-MM-DD HH24:MI:SS'));
select partman.create_parent('public.crypto_transfer', 'consensus_timestamp', 'native', 'monthly', p_epoch := 'nanoseconds' , p_premake := 2, p_start_partition := to_char(CURRENT_TIMESTAMP-'3 years'::interval, 'YYYY-MM-DD HH24:MI:SS'));
select partman.create_parent('public.entity', 'id', 'native', '100000');
select partman.create_parent('public.entity_history', 'id', 'native', '100000');
select partman.create_parent('public.nft_transfer', 'consensus_timestamp', 'native', 'monthly', p_epoch := 'nanoseconds' , p_premake := 2, p_start_partition := to_char(CURRENT_TIMESTAMP-'3 years'::interval, 'YYYY-MM-DD HH24:MI:SS'));
select partman.create_parent('public.non_fee_transfer', 'consensus_timestamp', 'native', 'monthly', p_epoch := 'nanoseconds' , p_premake := 2, p_start_partition := to_char(CURRENT_TIMESTAMP-'3 years'::interval, 'YYYY-MM-DD HH24:MI:SS'));
select partman.create_parent('public.record_file', 'consensus_end', 'native', 'monthly', p_epoch := 'nanoseconds' , p_premake := 2, p_start_partition := to_char(CURRENT_TIMESTAMP-'3 years'::interval, 'YYYY-MM-DD HH24:MI:SS'));
select partman.create_parent('public.token_transfer', 'consensus_timestamp', 'native', 'monthly', p_epoch := 'nanoseconds' , p_premake := 2, p_start_partition := to_char(CURRENT_TIMESTAMP-'3 years'::interval, 'YYYY-MM-DD HH24:MI:SS'));
select partman.create_parent('public.topic_message', 'consensus_timestamp', 'native', 'monthly', p_epoch := 'nanoseconds' , p_premake := 2, p_start_partition := to_char(CURRENT_TIMESTAMP-'3 years'::interval, 'YYYY-MM-DD HH24:MI:SS'));
select partman.create_parent('public.transaction', 'consensus_timestamp', 'native', 'monthly', '{"type"}', p_epoch := 'nanoseconds' , p_premake := 2, p_start_partition := to_char(CURRENT_TIMESTAMP-'3 years'::interval, 'YYYY-MM-DD HH24:MI:SS'));
