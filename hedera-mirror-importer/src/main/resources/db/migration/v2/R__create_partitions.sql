-------------------
-- Add repeatable partitioning logic to large tables.
-------------------

--This unschedules all existing cron jobs.
SELECT cron.unschedule(jobid) FROM cron.job;

--Creating repeatable partitions
SELECT cron.schedule('create-partitions-account-balance',
                     ${cronSchedule},
                     $$select partman.create_parent('${schema}.account_balance', 'consensus_timestamp', 'native', 'yearly', p_epoch := 'nanoseconds' , p_premake := 2,
                      p_start_partition := to_char(CURRENT_TIMESTAMP-${partitionStartDate}::interval, 'YYYY-MM-DD HH24:MI:SS')) $$);

SELECT cron.schedule('create-partitions-assessed_custom_fee',
                     ${cronSchedule},
                     $$select partman.create_parent('${schema}.assessed_custom_fee', 'consensus_timestamp', 'native', 'yearly', p_epoch := 'nanoseconds' , p_premake := 2,
                      p_start_partition := to_char(CURRENT_TIMESTAMP-${partitionStartDate}::interval, 'YYYY-MM-DD HH24:MI:SS')) $$);
SELECT cron.schedule('create-partitions-contract',
                     ${cronSchedule},
                     $$select partman.create_parent('${schema}.contract', 'id', 'native', ${partitionIdInterval}) $$);
SELECT cron.schedule('create-partitions-contract_action',
                     ${cronSchedule},
                     $$select partman.create_parent('${schema}.contract_action', 'consensus_timestamp', 'native', 'yearly', p_epoch := 'nanoseconds' , p_premake := 2,
                      p_start_partition := to_char(CURRENT_TIMESTAMP-${partitionStartDate}::interval, 'YYYY-MM-DD HH24:MI:SS')) $$);
SELECT cron.schedule('create-partitions-contract_log',
                     ${cronSchedule},
                     $$select partman.create_parent('${schema}.contract_log', 'consensus_timestamp', 'native', 'yearly', p_epoch := 'nanoseconds' , p_premake := 2,
                      p_start_partition := to_char(CURRENT_TIMESTAMP-${partitionStartDate}::interval, 'YYYY-MM-DD HH24:MI:SS')) $$);
SELECT cron.schedule('create-partitions-contract_result',
                     ${cronSchedule},
                     $$select partman.create_parent('${schema}.contract_result', 'consensus_timestamp', 'native', 'yearly', p_epoch := 'nanoseconds' , p_premake := 2,
                      p_start_partition := to_char(CURRENT_TIMESTAMP-${partitionStartDate}::interval, 'YYYY-MM-DD HH24:MI:SS')) $$);
SELECT cron.schedule('create-partitions-contract_state',
                     ${cronSchedule},
                     $$select partman.create_parent('${schema}.contract_state', 'contract_id', 'native', ${partitionIdInterval}) $$);
SELECT cron.schedule('create-partitions-contract_state_change',
                     ${cronSchedule},
                     $$select partman.create_parent('${schema}.contract_state_change', 'consensus_timestamp', 'native', ${partitionIdInterval}) $$);
SELECT cron.schedule('create-partitions-crypto_allowance',
                     ${cronSchedule},
                     $$select partman.create_parent('${schema}.nft_allowance', 'owner', 'native', ${partitionIdInterval}) $$);
SELECT cron.schedule('create-partitions-crypto_allowance_history',
                     ${cronSchedule},
                     $$select partman.create_parent('${schema}.nft_allowance_history', 'owner', 'native', ${partitionIdInterval}) $$);
SELECT cron.schedule('create-partitions-crypto-transfer',
                     ${cronSchedule},
                     $$select partman.create_parent('${schema}.crypto_transfer', 'consensus_timestamp', 'native', 'yearly', p_epoch := 'nanoseconds' ,
                    p_premake := 2, p_start_partition := to_char(CURRENT_TIMESTAMP-${partitionStartDate}::interval, 'YYYY-MM-DD HH24:MI:SS')) $$);
SELECT cron.schedule('create-partitions-custom_fee',
                     ${cronSchedule},
                     $$select partman.create_parent('${schema}.custom_fee', 'created_timestamp', 'native', 'yearly', p_epoch := 'nanoseconds' , p_premake := 2,
                     p_start_partition := to_char(CURRENT_TIMESTAMP-${partitionStartDate}::interval, 'YYYY-MM-DD HH24:MI:SS')) $$);
SELECT cron.schedule('create-partitions-entity',
                     ${cronSchedule},
                     $$select partman.create_parent('${schema}.entity', 'id', 'native', ${partitionIdInterval}) $$);
SELECT cron.schedule('create-partitions-entity-history',
                     ${cronSchedule},
                     $$select partman.create_parent('${schema}.entity_history', 'id', 'native', ${partitionIdInterval}) $$);
SELECT cron.schedule('create-partitions-entity_stake',
                     ${cronSchedule},
                     $$select partman.create_parent('${schema}.entity_stake', 'id', 'native', ${partitionIdInterval}) $$);
SELECT cron.schedule('create-partitions-ethereum_transaction',
                     ${cronSchedule},
                     $$select partman.create_parent('${schema}.ethereum_transaction', 'created_timestamp', 'native', 'yearly', p_epoch := 'nanoseconds' , p_premake := 2,
                     p_start_partition := to_char(CURRENT_TIMESTAMP-${partitionStartDate}::interval, 'YYYY-MM-DD HH24:MI:SS')) $$);
SELECT cron.schedule('create-partitions-event_file',
                     ${cronSchedule},
                     $$select partman.create_parent('${schema}.event_file', 'consensus_end', 'native', 'yearly', p_epoch := 'nanoseconds' , p_premake := 2,
                     p_start_partition := to_char(CURRENT_TIMESTAMP-${partitionStartDate}::interval, 'YYYY-MM-DD HH24:MI:SS')) $$);
SELECT cron.schedule('create-partitions-file_data',
                     ${cronSchedule},
                     $$select partman.create_parent('${schema}.file_data', 'consensus_timestamp', 'native', 'yearly', p_epoch := 'nanoseconds' , p_premake := 2,
                     p_start_partition := to_char(CURRENT_TIMESTAMP-${partitionStartDate}::interval, 'YYYY-MM-DD HH24:MI:SS')) $$);
SELECT cron.schedule('create-partitions-nft',
                     ${cronSchedule},
                     $$select partman.create_parent('${schema}.nft', 'token_id', 'native', ${partitionIdInterval}) $$);
SELECT cron.schedule('create-partitions-nft_allowance',
                     ${cronSchedule},
                     $$select partman.create_parent('${schema}.nft_allowance', 'owner', 'native', ${partitionIdInterval}) $$);
SELECT cron.schedule('create-partitions-nft_allowance_history',
                     ${cronSchedule},
                     $$select partman.create_parent('${schema}.nft_allowance_history', 'owner', 'native', ${partitionIdInterval}) $$);
SELECT cron.schedule('create-partitions-nft-transfer',
                     ${cronSchedule},
                     $$select partman.create_parent('${schema}.nft_transfer', 'consensus_timestamp', 'native', 'yearly', p_epoch := 'nanoseconds' , p_premake := 2,
                    p_start_partition := to_char(CURRENT_TIMESTAMP-${partitionStartDate}::interval, 'YYYY-MM-DD HH24:MI:SS')) $$);
SELECT cron.schedule('create-partitions-non-fee-transfer',
                     ${cronSchedule},
                     $$select partman.create_parent('${schema}.non_fee_transfer', 'consensus_timestamp', 'native', 'yearly', p_epoch := 'nanoseconds' , p_premake := 2,
                     p_start_partition := to_char(CURRENT_TIMESTAMP-${partitionStartDate}::interval, 'YYYY-MM-DD HH24:MI:SS')) $$);

SELECT cron.schedule('create-partitions-record-file',
                     ${cronSchedule},
                     $$select partman.create_parent('${schema}.record_file', 'consensus_end', 'native', 'yearly', p_epoch := 'nanoseconds' , p_premake := 2,
                     p_start_partition := to_char(CURRENT_TIMESTAMP-${partitionStartDate}::interval, 'YYYY-MM-DD HH24:MI:SS')) $$);

SELECT cron.schedule('create-partitions-schedule',
                     ${cronSchedule},
                     $$select partman.create_parent('${schema}.schedule', 'schedule_id', 'native', ${partitionIdInterval}) $$);

SELECT cron.schedule('create-partitions-staking_reward_transfer',
                     ${cronSchedule},
                     $$select partman.create_parent('${schema}.staking_reward_transfer', 'consensus_timestamp', 'native', 'yearly', p_epoch := 'nanoseconds' , p_premake := 2,
                     p_start_partition := to_char(CURRENT_TIMESTAMP-${partitionStartDate}::interval, 'YYYY-MM-DD HH24:MI:SS')) $$);

SELECT cron.schedule('create-partitions-token',
                     ${cronSchedule},
                     $$select partman.create_parent('${schema}.token', 'token_id', 'native', ${partitionIdInterval}) $$);

SELECT cron.schedule('create-partitions-token_account',
                     ${cronSchedule},
                     $$select partman.create_parent('${schema}.token_account', 'token_id', 'native', ${partitionIdInterval}) $$);

SELECT cron.schedule('create-partitions-token_account_history',
                     ${cronSchedule},
                     $$select partman.create_parent('${schema}.token_account_history', 'token_id', 'native', ${partitionIdInterval}) $$);

SELECT cron.schedule('create-partitions-token_allowance',
                     ${cronSchedule},
                     $$select partman.create_parent('${schema}.token_allowance', 'owner', 'native', ${partitionIdInterval}) $$);

SELECT cron.schedule('create-partitions-token_allowance_history',
                     ${cronSchedule},
                     $$select partman.create_parent('${schema}.token_allowance_history', 'owner', 'native', ${partitionIdInterval}) $$);

SELECT cron.schedule('create-partitions-token-balance',
                     ${cronSchedule},
                     $$select partman.create_parent('${schema}.token_balance', 'consensus_timestamp', 'native', 'yearly', p_epoch := 'nanoseconds' , p_premake := 2,
                     p_start_partition := to_char(CURRENT_TIMESTAMP-${partitionStartDate}::interval, 'YYYY-MM-DD HH24:MI:SS')) $$);

SELECT cron.schedule('create-partitions-token-transfer',
                     ${cronSchedule},
                     $$select partman.create_parent('${schema}.token_transfer', 'consensus_timestamp', 'native', 'yearly', p_epoch := 'nanoseconds' , p_premake := 2,
                     p_start_partition := to_char(CURRENT_TIMESTAMP-${partitionStartDate}::interval, 'YYYY-MM-DD HH24:MI:SS')) $$);

SELECT cron.schedule('create-partitions-topic-message',
                     ${cronSchedule},
                     $$select partman.create_parent('${schema}.topic_message', 'consensus_timestamp', 'native', 'yearly', p_epoch := 'nanoseconds' , p_premake := 2,
                     p_start_partition := to_char(CURRENT_TIMESTAMP-${partitionStartDate}::interval, 'YYYY-MM-DD HH24:MI:SS')) $$);


SELECT cron.schedule('create-partitions-transactions',
                     ${cronSchedule},
                     $$select partman.create_parent('${schema}.transaction', 'consensus_timestamp', 'native', 'yearly', p_epoch := 'nanoseconds' , p_premake := 2,
                         p_start_partition := to_char(CURRENT_TIMESTAMP-${partitionStartDate}::interval, 'YYYY-MM-DD HH24:MI:SS')) $$);

SELECT cron.schedule('create-partitions-transaction_hash',
                     ${cronSchedule},
                     $$select partman.create_parent('${schema}.transaction_hash', 'consensus_timestamp', 'native', 'yearly', p_epoch := 'nanoseconds' , p_premake := 2,
                     p_start_partition := to_char(CURRENT_TIMESTAMP-${partitionStartDate}::interval, 'YYYY-MM-DD HH24:MI:SS')) $$);

SELECT cron.schedule('create-partitions-transaction_signature',
                     ${cronSchedule},
                     $$select partman.create_parent('${schema}.transaction_signature', 'entity_id', 'native', ${partitionIdInterval}) $$);
