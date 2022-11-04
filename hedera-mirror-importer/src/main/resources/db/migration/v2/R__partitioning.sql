-------------------
-- Add repeatable partitioning logic to large tables.
-------------------

SELECT cron.unschedule(jobid) FROM cron.job;

SELECT cron.schedule('create-partitions-account-balance',
                     '@monthly',
                     $$select partman.create_parent('public.account_balance', 'consensus_timestamp', 'native', 'yearly', p_epoch := 'nanoseconds' , p_premake := 2,
                      p_start_partition := to_char(CURRENT_TIMESTAMP-'3 years'::interval, 'YYYY-MM-DD HH24:MI:SS')) $$);

SELECT cron.schedule('create-partitions-crypto-transfer',
                     '@daily',
                     $$select partman.create_parent('public.crypto_transfer', 'consensus_timestamp', 'native', 'yearly', p_epoch := 'nanoseconds' ,
                    p_premake := 2, p_start_partition := to_char(CURRENT_TIMESTAMP-'3 years'::interval, 'YYYY-MM-DD HH24:MI:SS')) $$);

SELECT cron.schedule('create-partitions-entity',
                     '@daily',
                     $$select partman.create_parent('public.entity', 'id', 'native', '100000') $$);

SELECT cron.schedule('create-partitions-entity-history',
                     '@daily',
                     $$select partman.create_parent('public.entity_history', 'id', 'native', '100000') $$);

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
