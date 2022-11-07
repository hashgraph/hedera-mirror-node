-------------------
-- Add repeatable partitioning logic to large tables.
-------------------

SELECT cron.unschedule(jobid) FROM cron.job;

SELECT cron.schedule('create-partitions-account-balance',
                     '@monthly',
                     $$select partman.create_parent('public.account_balance', 'consensus_timestamp', 'native', 'yearly', p_epoch := 'nanoseconds' , p_premake := 2,
                      p_start_partition := to_char(CURRENT_TIMESTAMP-'3 years'::interval, 'YYYY-MM-DD HH24:MI:SS')) $$);
SELECT cron.schedule('create-partitions-address_book',
                     '@monthly',
                     $$select partman.create_parent('public.address_book_service_endpoint', 'end_consensus_timestamp', 'native', 'yearly', p_epoch := 'nanoseconds' , p_premake := 2,
                      p_start_partition := to_char(CURRENT_TIMESTAMP-'3 years'::interval, 'YYYY-MM-DD HH24:MI:SS')) $$);
SELECT cron.schedule('create-partitions-address_book_entry',
                     '@monthly',
                     $$select partman.create_parent('public.address_book_service_endpoint', 'consensus_timestamp', 'native', 'yearly', p_epoch := 'nanoseconds' , p_premake := 2,
                      p_start_partition := to_char(CURRENT_TIMESTAMP-'3 years'::interval, 'YYYY-MM-DD HH24:MI:SS')) $$);
SELECT cron.schedule('create-partitions-address_book_service_endpoint',
                     '@monthly',
                     $$select partman.create_parent('public.address_book_service_endpoint', 'consensus_timestamp', 'native', 'yearly', p_epoch := 'nanoseconds' , p_premake := 2,
                      p_start_partition := to_char(CURRENT_TIMESTAMP-'3 years'::interval, 'YYYY-MM-DD HH24:MI:SS')) $$);
SELECT cron.schedule('create-partitions-assessed_custom_fee',
                     '@monthly',
                     $$select partman.create_parent('public.assessed_custom_fee', 'consensus_timestamp', 'native', 'yearly', p_epoch := 'nanoseconds' , p_premake := 2,
                      p_start_partition := to_char(CURRENT_TIMESTAMP-'3 years'::interval, 'YYYY-MM-DD HH24:MI:SS')) $$);
SELECT cron.schedule('create-partitions-contract',
                     '@daily',
                     $$select partman.create_parent('public.contract', 'id', 'native', '100000') $$);
SELECT cron.schedule('create-partitions-contract_action',
                     '@monthly',
                     $$select partman.create_parent('public.contract_action', 'consensus_timestamp', 'native', 'yearly', p_epoch := 'nanoseconds' , p_premake := 2,
                      p_start_partition := to_char(CURRENT_TIMESTAMP-'3 years'::interval, 'YYYY-MM-DD HH24:MI:SS')) $$);
SELECT cron.schedule('create-partitions-contract_log',
                     '@monthly',
                     $$select partman.create_parent('public.contract_log', 'consensus_timestamp', 'native', 'yearly', p_epoch := 'nanoseconds' , p_premake := 2,
                      p_start_partition := to_char(CURRENT_TIMESTAMP-'3 years'::interval, 'YYYY-MM-DD HH24:MI:SS')) $$);
SELECT cron.schedule('create-partitions-contract_result',
                     '@monthly',
                     $$select partman.create_parent('public.contract_result', 'consensus_timestamp', 'native', 'yearly', p_epoch := 'nanoseconds' , p_premake := 2,
                      p_start_partition := to_char(CURRENT_TIMESTAMP-'3 years'::interval, 'YYYY-MM-DD HH24:MI:SS')) $$);
SELECT cron.schedule('create-partitions-contract_state',
                     '@daily',
                     $$select partman.create_parent('public.contract_state', 'contract_id', 'native', '100000') $$);
SELECT cron.schedule('create-partitions-contract_state_change',
                     '@daily',
                     $$select partman.create_parent('public.contract_state_change', 'contract_id', 'native', '100000') $$);
SELECT cron.schedule('create-partitions-crypto_allowance',
                     '@daily',
                     $$select partman.create_parent('public.nft_allowance', 'owner', 'native', '100000') $$);
SELECT cron.schedule('create-partitions-crypto_allowance_history',
                     '@daily',
                     $$select partman.create_parent('public.nft_allowance_history', 'owner', 'native', '100000') $$);
SELECT cron.schedule('create-partitions-crypto-transfer',
                     '@daily',
                     $$select partman.create_parent('public.crypto_transfer', 'consensus_timestamp', 'native', 'yearly', p_epoch := 'nanoseconds' ,
                    p_premake := 2, p_start_partition := to_char(CURRENT_TIMESTAMP-'3 years'::interval, 'YYYY-MM-DD HH24:MI:SS')) $$);
SELECT cron.schedule('create-partitions-custom_fee',
                     '@daily',
                     $$select partman.create_parent('public.custom_fee', 'created_timestamp', 'native', 'yearly', p_epoch := 'nanoseconds' , p_premake := 2,
                     p_start_partition := to_char(CURRENT_TIMESTAMP-'3 years'::interval, 'YYYY-MM-DD HH24:MI:SS')) $$);
SELECT cron.schedule('create-partitions-entity',
                     '@daily',
                     $$select partman.create_parent('public.entity', 'id', 'native', '100000') $$);
SELECT cron.schedule('create-partitions-entity-history',
                     '@daily',
                     $$select partman.create_parent('public.entity_history', 'id', 'native', '100000') $$);
SELECT cron.schedule('create-partitions-entity_stake',
                     '@daily',
                     $$select partman.create_parent('public.entity_stake', 'id', 'native', '100000') $$);
SELECT cron.schedule('create-partitions-ethereum_transaction',
                     '@daily',
                     $$select partman.create_parent('public.ethereum_transaction', 'created_timestamp', 'native', 'yearly', p_epoch := 'nanoseconds' , p_premake := 2,
                     p_start_partition := to_char(CURRENT_TIMESTAMP-'3 years'::interval, 'YYYY-MM-DD HH24:MI:SS')) $$);
SELECT cron.schedule('create-partitions-event_file',
                     '@daily',
                     $$select partman.create_parent('public.event_file', 'consensus_end', 'native', 'yearly', p_epoch := 'nanoseconds' , p_premake := 2,
                     p_start_partition := to_char(CURRENT_TIMESTAMP-'3 years'::interval, 'YYYY-MM-DD HH24:MI:SS')) $$);
SELECT cron.schedule('create-partitions-file_data',
                     '@daily',
                     $$select partman.create_parent('public.file_data', 'consensus_timestamp', 'native', 'yearly', p_epoch := 'nanoseconds' , p_premake := 2,
                     p_start_partition := to_char(CURRENT_TIMESTAMP-'3 years'::interval, 'YYYY-MM-DD HH24:MI:SS')) $$);
SELECT cron.schedule('create-partitions-nft',
                     '@daily',
                     $$select partman.create_parent('public.nft', 'token_id', 'native', '100000') $$);
SELECT cron.schedule('create-partitions-nft_allowance',
                     '@daily',
                     $$select partman.create_parent('public.nft_allowance', 'owner', 'native', '100000') $$);
SELECT cron.schedule('create-partitions-nft_allowance_history',
                     '@daily',
                     $$select partman.create_parent('public.nft_allowance_history', 'owner', 'native', '100000') $$);
SELECT cron.schedule('create-partitions-nft-transfer',
                     '@daily',
                     $$select partman.create_parent('public.nft_transfer', 'consensus_timestamp', 'native', 'yearly', p_epoch := 'nanoseconds' , p_premake := 2,
                    p_start_partition := to_char(CURRENT_TIMESTAMP-'3 years'::interval, 'YYYY-MM-DD HH24:MI:SS')) $$);
SELECT cron.schedule('create-partitions-non-fee-transfer',
                     '@daily',
                     $$select partman.create_parent('public.non_fee_transfer', 'consensus_timestamp', 'native', 'yearly', p_epoch := 'nanoseconds' , p_premake := 2,
                     p_start_partition := to_char(CURRENT_TIMESTAMP-'3 years'::interval, 'YYYY-MM-DD HH24:MI:SS')) $$);

SELECT cron.schedule('create-partitions-record-file',
                     '@daily',
                     $$select partman.create_parent('public.record_file', 'consensus_end', 'native', 'yearly', p_epoch := 'nanoseconds' , p_premake := 2,
                     p_start_partition := to_char(CURRENT_TIMESTAMP-'3 years'::interval, 'YYYY-MM-DD HH24:MI:SS')) $$);

SELECT cron.schedule('create-partitions-schedule',
                     '@daily',
                     $$select partman.create_parent('public.schedule', 'schedule_id', 'native', '100000') $$);

SELECT cron.schedule('create-partitions-staking_reward_transfer',
                     '@daily',
                     $$select partman.create_parent('public.staking_reward_transfer', 'consensus_timestamp', 'native', 'yearly', p_epoch := 'nanoseconds' , p_premake := 2,
                     p_start_partition := to_char(CURRENT_TIMESTAMP-'3 years'::interval, 'YYYY-MM-DD HH24:MI:SS')) $$);

SELECT cron.schedule('create-partitions-token',
                     '@daily',
                     $$select partman.create_parent('public.token', 'token_id', 'native', '100000') $$);

SELECT cron.schedule('create-partitions-token_account',
                     '@daily',
                     $$select partman.create_parent('public.token_account', 'token_id', 'native', '100000') $$);

SELECT cron.schedule('create-partitions-token_account_history',
                     '@daily',
                     $$select partman.create_parent('public.token_account_history', 'token_id', 'native', '100000') $$);

SELECT cron.schedule('create-partitions-token_allowance',
                     '@daily',
                     $$select partman.create_parent('public.token_allowance', 'owner', 'native', '100000') $$);

SELECT cron.schedule('create-partitions-token_allowance_history',
                     '@daily',
                     $$select partman.create_parent('public.token_allowance_history', 'owner', 'native', '100000') $$);

SELECT cron.schedule('create-partitions-token-balance',
                     '@daily',
                     $$select partman.create_parent('public.token_balance', 'consensus_timestamp', 'native', 'yearly', p_epoch := 'nanoseconds' , p_premake := 2,
                     p_start_partition := to_char(CURRENT_TIMESTAMP-'3 years'::interval, 'YYYY-MM-DD HH24:MI:SS')) $$);

SELECT cron.schedule('create-partitions-token-transfer',
                     '@daily',
                     $$select partman.create_parent('public.token_transfer', 'consensus_timestamp', 'native', 'yearly', p_epoch := 'nanoseconds' , p_premake := 2,
                     p_start_partition := to_char(CURRENT_TIMESTAMP-'3 years'::interval, 'YYYY-MM-DD HH24:MI:SS')) $$);

SELECT cron.schedule('create-partitions-topic-message',
                     '@daily',
                     $$select partman.create_parent('public.topic_message', 'consensus_timestamp', 'native', 'yearly', p_epoch := 'nanoseconds' , p_premake := 2,
                     p_start_partition := to_char(CURRENT_TIMESTAMP-'3 years'::interval, 'YYYY-MM-DD HH24:MI:SS')) $$);


SELECT cron.schedule('create-partitions-transactions',
                     '@daily',
                     $$select partman.create_parent('public.transaction', 'consensus_timestamp', 'native', 'yearly', '{"type"}', p_epoch := 'nanoseconds' , p_premake := 2,
                         p_start_partition := to_char(CURRENT_TIMESTAMP-'3 years'::interval, 'YYYY-MM-DD HH24:MI:SS')) $$);

SELECT cron.schedule('create-partitions-transaction_hash',
                     '@daily',
                     $$select partman.create_parent('public.transaction_hash', 'consensus_timestamp', 'native', 'yearly', p_epoch := 'nanoseconds' , p_premake := 2,
                     p_start_partition := to_char(CURRENT_TIMESTAMP-'3 years'::interval, 'YYYY-MM-DD HH24:MI:SS')) $$);

SELECT cron.schedule('create-partitions-transaction_signature',
                     '@daily',
                     $$select partman.create_parent('public.transaction_signature', 'entity_id', 'native', '100000') $$);
