drop table if exists transaction_hash;

create table if not exists transaction_hash
(
    consensus_timestamp bigint not null,
    distribution_id     bigint not null,
    hash                bytea  not null,
    payer_account_id    bigint not null
);
comment on table transaction_hash is 'Network transaction hash to consensus timestamp mapping';

select create_distributed_table('transaction_hash', 'distribution_id',  shard_count := ${hashShardCount});
