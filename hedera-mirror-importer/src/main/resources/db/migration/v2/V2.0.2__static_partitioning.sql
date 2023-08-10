-- configure session to ensure partitions are created with bounds in UTC
SET timezone TO 'UTC';

-------------------
-- Add non-repeatable partitioning logic to large tables.
-------------------
select create_time_partitions(table_name :='public.account_balance',
                              partition_interval := INTERVAL ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP-${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + '1 month');
select create_time_partitions(table_name :='public.assessed_custom_fee',
                              partition_interval := INTERVAL ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP-${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + '1 month');
select create_time_partitions(table_name :='public.contract', partition_interval := INTERVAL ${partitionIdInterval},
                              start_from := '1970-01-01 00:00:00.000+00'::timestamptz,
                              end_at :=  '1970-01-01 00:00:00.000+00'::timestamptz + ((INTERVAL ${partitionIdInterval}) * ${partitionIdCount}));
alter table contract_p1970_01_01_000000 rename to contract_p0;
select create_time_partitions(table_name :='public.contract_action',
                              partition_interval := INTERVAL ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP-${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + '1 month');
select create_time_partitions(table_name :='public.contract_log',
                              partition_interval := INTERVAL ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP-${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + '1 month');
select create_time_partitions(table_name :='public.contract_result',
                              partition_interval := INTERVAL ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP-${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + '1 month');
select create_time_partitions(table_name :='public.contract_state', partition_interval := INTERVAL ${partitionIdInterval},
                              start_from := '1970-01-01 00:00:00.000+00'::timestamptz,
                              end_at :=  '1970-01-01 00:00:00.000+00'::timestamptz + ((INTERVAL ${partitionIdInterval}) * ${partitionIdCount}));
alter table contract_state_p1970_01_01_000000 rename to contract_state_p0;
select create_time_partitions(table_name :='public.contract_state_change',
                              partition_interval := INTERVAL ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP-${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + '1 month');
select create_time_partitions(table_name :='public.crypto_allowance', partition_interval := INTERVAL ${partitionIdInterval},
                              start_from := '1970-01-01 00:00:00.000+00'::timestamptz,
                              end_at :=  '1970-01-01 00:00:00.000+00'::timestamptz + ((INTERVAL ${partitionIdInterval}) * ${partitionIdCount}));
alter table crypto_allowance_p1970_01_01_000000 rename to crypto_allowance_p0;
select create_time_partitions(table_name :='public.crypto_allowance_history',
                              partition_interval := INTERVAL ${partitionIdInterval},
                              start_from := '1970-01-01 00:00:00.000+00'::timestamptz,
                              end_at :=  '1970-01-01 00:00:00.000+00'::timestamptz + ((INTERVAL ${partitionIdInterval}) * ${partitionIdCount}));
alter table crypto_allowance_history_p1970_01_01_000000 rename to crypto_allowance_history_p0;
select create_time_partitions(table_name :='public.crypto_transfer',
                              partition_interval := INTERVAL ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP-${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + '1 month');
select create_time_partitions(table_name :='public.custom_fee', partition_interval := INTERVAL ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP-${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + '1 month');
select create_time_partitions(table_name :='public.entity', partition_interval := INTERVAL ${partitionIdInterval},
                              start_from := '1970-01-01 00:00:00.000+00'::timestamptz,
                              end_at :=  '1970-01-01 00:00:00.000+00'::timestamptz + ((INTERVAL ${partitionIdInterval}) * ${partitionIdCount}));
alter table entity_p1970_01_01_000000 rename to entity_p0;
select create_time_partitions(table_name :='public.entity_history', partition_interval := INTERVAL ${partitionIdInterval},
                              start_from := '1970-01-01 00:00:00.000+00'::timestamptz,
                              end_at :=  '1970-01-01 00:00:00.000+00'::timestamptz + ((INTERVAL ${partitionIdInterval}) * ${partitionIdCount}));
alter table entity_history_p1970_01_01_000000 rename to entity_history_p0;
select create_time_partitions(table_name :='public.entity_stake', partition_interval := INTERVAL ${partitionIdInterval},
                              start_from := '1970-01-01 00:00:00.000+00'::timestamptz,
                              end_at :=  '1970-01-01 00:00:00.000+00'::timestamptz + ((INTERVAL ${partitionIdInterval}) * ${partitionIdCount}));
alter table entity_stake_p1970_01_01_000000 rename to entity_stake_p0;
select create_time_partitions(table_name :='public.entity_stake_history',
                              partition_interval := INTERVAL ${partitionIdInterval},
                              start_from := '1970-01-01 00:00:00.000+00'::timestamptz,
                              end_at :=  '1970-01-01 00:00:00.000+00'::timestamptz + ((INTERVAL ${partitionIdInterval}) * ${partitionIdCount}));
alter table entity_stake_history_p1970_01_01_000000 rename to entity_stake_history_p0;
select create_time_partitions(table_name :='public.entity_transaction',
                              partition_interval := INTERVAL ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP-${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + '1 month');
select create_time_partitions(table_name :='public.ethereum_transaction',
                              partition_interval := INTERVAL ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP-${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + '1 month');
select create_time_partitions(table_name :='public.event_file', partition_interval := INTERVAL ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP-${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + '1 month');
select create_time_partitions(table_name :='public.file_data', partition_interval := INTERVAL ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP-${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + '1 month');
select create_time_partitions(table_name :='public.network_freeze',
                              partition_interval := INTERVAL ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP-${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + '1 month');
select create_time_partitions(table_name :='public.nft', partition_interval := INTERVAL ${partitionIdInterval},
                              start_from := '1970-01-01 00:00:00.000+00'::timestamptz,
                              end_at :=  '1970-01-01 00:00:00.000+00'::timestamptz + ((INTERVAL ${partitionIdInterval}) * ${partitionIdCount}));
alter table nft_p1970_01_01_000000 rename to nft_p0;
select create_time_partitions(table_name :='public.nft_history', partition_interval := INTERVAL ${partitionIdInterval},
                              start_from := '1970-01-01 00:00:00.000+00'::timestamptz,
                              end_at :=  '1970-01-01 00:00:00.000+00'::timestamptz + ((INTERVAL ${partitionIdInterval}) * ${partitionIdCount}));
alter table nft_history_p1970_01_01_000000 rename to nft_history_p0;
select create_time_partitions(table_name :='public.nft_allowance', partition_interval := INTERVAL ${partitionIdInterval},
                              start_from := '1970-01-01 00:00:00.000+00'::timestamptz,
                              end_at :=  '1970-01-01 00:00:00.000+00'::timestamptz + ((INTERVAL ${partitionIdInterval}) * ${partitionIdCount}));
alter table nft_allowance_p1970_01_01_000000 rename to nft_allowance_p0;
select create_time_partitions(table_name :='public.nft_allowance_history',
                              partition_interval := INTERVAL ${partitionIdInterval},
                              start_from := '1970-01-01 00:00:00.000+00'::timestamptz,
                              end_at :=  '1970-01-01 00:00:00.000+00'::timestamptz + ((INTERVAL ${partitionIdInterval}) * ${partitionIdCount}));
alter table nft_allowance_history_p1970_01_01_000000 rename to nft_allowance_history_p0;
select create_time_partitions(table_name :='public.record_file',
                              partition_interval := INTERVAL ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP-${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + '1 month');
select create_time_partitions(table_name :='public.schedule', partition_interval := INTERVAL ${partitionIdInterval},
                              start_from := '1970-01-01 00:00:00.000+00'::timestamptz,
                              end_at :=  '1970-01-01 00:00:00.000+00'::timestamptz + ((INTERVAL ${partitionIdInterval}) * ${partitionIdCount}));
alter table schedule_p1970_01_01_000000 rename to schedule_p0;
select create_time_partitions(table_name :='public.staking_reward_transfer',
                              partition_interval := INTERVAL ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP-${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + '1 month');
select create_time_partitions(table_name :='public.token', partition_interval := INTERVAL ${partitionIdInterval},
                              start_from := '1970-01-01 00:00:00.000+00'::timestamptz,
                              end_at :=  '1970-01-01 00:00:00.000+00'::timestamptz + ((INTERVAL ${partitionIdInterval}) * ${partitionIdCount}));
alter table token_p1970_01_01_000000 rename to token_p0;
select create_time_partitions(table_name :='public.token_history', partition_interval := INTERVAL ${partitionIdInterval},
                              start_from := '1970-01-01 00:00:00.000+00'::timestamptz,
                              end_at :=  '1970-01-01 00:00:00.000+00'::timestamptz + ((INTERVAL ${partitionIdInterval}) * ${partitionIdCount}));
alter table token_history_p1970_01_01_000000 rename to token_history_p0;
select create_time_partitions(table_name :='public.token_account', partition_interval := INTERVAL ${partitionIdInterval},
                              start_from := '1970-01-01 00:00:00.000+00'::timestamptz,
                              end_at :=  '1970-01-01 00:00:00.000+00'::timestamptz + ((INTERVAL ${partitionIdInterval}) * ${partitionIdCount}));
alter table token_account_p1970_01_01_000000 rename to token_account_p0;
select create_time_partitions(table_name :='public.token_account_history',
                              partition_interval := INTERVAL ${partitionIdInterval},
                              start_from := '1970-01-01 00:00:00.000+00'::timestamptz,
                              end_at :=  '1970-01-01 00:00:00.000+00'::timestamptz + ((INTERVAL ${partitionIdInterval}) * ${partitionIdCount}));
alter table token_account_history_p1970_01_01_000000 rename to token_account_history_p0;
select create_time_partitions(table_name :='public.token_allowance', partition_interval := INTERVAL ${partitionIdInterval},
                              start_from := '1970-01-01 00:00:00.000+00'::timestamptz,
                              end_at :=  '1970-01-01 00:00:00.000+00'::timestamptz + ((INTERVAL ${partitionIdInterval}) * ${partitionIdCount}));
alter table token_allowance_p1970_01_01_000000 rename to token_allowance_p0;
select create_time_partitions(table_name :='public.token_allowance_history',
                              partition_interval := INTERVAL ${partitionIdInterval},
                              start_from := '1970-01-01 00:00:00.000+00'::timestamptz,
                              end_at :=  '1970-01-01 00:00:00.000+00'::timestamptz + ((INTERVAL ${partitionIdInterval}) * ${partitionIdCount}));
alter table token_allowance_history_p1970_01_01_000000 rename to token_allowance_history_p0;
select create_time_partitions(table_name :='public.token_balance',
                              partition_interval := INTERVAL ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP-${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + '1 month');
select create_time_partitions(table_name :='public.token_transfer',
                              partition_interval := INTERVAL ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP-${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + '1 month');
select create_time_partitions(table_name :='public.topic_message',
                              partition_interval := INTERVAL ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP-${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + '1 month');
select create_time_partitions(table_name :='public.transaction',
                              partition_interval := INTERVAL ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP-${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + '1 month');
select create_time_partitions(table_name :='public.transaction_hash',
                              partition_interval := INTERVAL ${partitionTimeInterval},
                              start_from := CURRENT_TIMESTAMP-${partitionStartDate}:: interval,
                              end_at := CURRENT_TIMESTAMP + '1 month');
select create_time_partitions(table_name :='public.transaction_signature',
                              partition_interval := INTERVAL ${partitionIdInterval},
                              start_from := '1970-01-01 00:00:00.000+00'::timestamptz,
                              end_at :=  '1970-01-01 00:00:00.000+00'::timestamptz + ((INTERVAL ${partitionIdInterval}) * ${partitionIdCount}));
alter table transaction_signature_p1970_01_01_000000 rename to transaction_signature_p0;
