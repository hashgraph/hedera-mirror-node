-------------------
-- Add repeatable partitioning logic to large tables.
-------------------

-- unschedule the partman maintenance job
select cron.unschedule(jobid) from cron.job where jobname = 'partman-maintenance';

--Changing partition intervals
update partman.part_config set partition_interval = ${partitionTimeInterval} where parent_table = '${schema}.account_balance';
update partman.part_config set partition_interval = ${partitionTimeInterval} where parent_table = '${schema}.assessed_custom_fee';
update partman.part_config set partition_interval = ${partitionIdInterval} where parent_table = '${schema}.contract';
update partman.part_config set partition_interval = ${partitionTimeInterval} where parent_table = '${schema}.contract_action';
update partman.part_config set partition_interval = ${partitionTimeInterval} where parent_table = '${schema}.contract_log';
update partman.part_config set partition_interval = ${partitionTimeInterval} where parent_table = '${schema}.contract_result';
update partman.part_config set partition_interval = ${partitionIdInterval} where parent_table = '${schema}.contract_state';
update partman.part_config set partition_interval = ${partitionTimeInterval} where parent_table = '${schema}.contract_state_change';
update partman.part_config set partition_interval = ${partitionIdInterval} where parent_table = '${schema}.crypto_allowance';
update partman.part_config set partition_interval = ${partitionIdInterval} where parent_table = '${schema}.crypto_allowance_history';
update partman.part_config set partition_interval = ${partitionTimeInterval} where parent_table = '${schema}.crypto_transfer';
update partman.part_config set partition_interval = ${partitionTimeInterval} where parent_table = '${schema}.custom_fee';
update partman.part_config set partition_interval = ${partitionIdInterval} where parent_table = '${schema}.entity';
update partman.part_config set partition_interval = ${partitionIdInterval} where parent_table = '${schema}.entity_history';
update partman.part_config set partition_interval = ${partitionIdInterval} where parent_table = '${schema}.entity_stake';
update partman.part_config set partition_interval = ${partitionIdInterval} where parent_table = '${schema}.entity_stake_history';
update partman.part_config set partition_interval = ${partitionTimeInterval} where parent_table = '${schema}.entity_transaction';
update partman.part_config set partition_interval = ${partitionTimeInterval} where parent_table = '${schema}.ethereum_transaction';
update partman.part_config set partition_interval = ${partitionTimeInterval} where parent_table = '${schema}.event_file';
update partman.part_config set partition_interval = ${partitionTimeInterval} where parent_table = '${schema}.file_data';
update partman.part_config set partition_interval = ${partitionIdInterval} where parent_table = '${schema}.nft';
update partman.part_config set partition_interval = ${partitionIdInterval} where parent_table = '${schema}.nft_history';
update partman.part_config set partition_interval = ${partitionIdInterval} where parent_table = '${schema}.nft_allowance';
update partman.part_config set partition_interval = ${partitionIdInterval} where parent_table = '${schema}.nft_allowance_history';
update partman.part_config set partition_interval = ${partitionTimeInterval} where parent_table = '${schema}.non_fee_transfer';
update partman.part_config set partition_interval = ${partitionTimeInterval} where parent_table = '${schema}.record_file';
update partman.part_config set partition_interval = ${partitionIdInterval} where parent_table = '${schema}.schedule';
update partman.part_config set partition_interval = ${partitionTimeInterval} where parent_table = '${schema}.staking_reward_transfer';
update partman.part_config set partition_interval = ${partitionIdInterval} where parent_table = '${schema}.token';
update partman.part_config set partition_interval = ${partitionIdInterval} where parent_table = '${schema}.token_history';
update partman.part_config set partition_interval = ${partitionIdInterval} where parent_table = '${schema}.token_account';
update partman.part_config set partition_interval = ${partitionIdInterval} where parent_table = '${schema}.token_account_history';
update partman.part_config set partition_interval = ${partitionIdInterval} where parent_table = '${schema}.token_allowance';
update partman.part_config set partition_interval = ${partitionIdInterval} where parent_table = '${schema}.token_allowance_history';
update partman.part_config set partition_interval = ${partitionTimeInterval} where parent_table = '${schema}.token_balance';
update partman.part_config set partition_interval = ${partitionTimeInterval} where parent_table = '${schema}.token_transfer';
update partman.part_config set partition_interval = ${partitionTimeInterval} where parent_table = '${schema}.topic_message';
update partman.part_config set partition_interval = ${partitionTimeInterval} where parent_table = '${schema}.transaction';
update partman.part_config set partition_interval = ${partitionTimeInterval} where parent_table = '${schema}.transaction_hash';
update partman.part_config set partition_interval = ${partitionIdInterval} where parent_table = '${schema}.transaction_signature';

select cron.schedule('partman-maintenance', ${cronSchedule}, $$CALL partman.run_maintenance_proc()$$);
