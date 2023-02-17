ALTER TABLE transaction_hash RENAME TO transaction_hash_old;
create table if not exists transaction_hash_sharded (
                                                        consensus_timestamp bigint not null,
                                                        hash                bytea not null,
                                                        payer_account_id    bigint null
) partition by hash (hash);
create table if not exists transaction_hash_sharded_00 partition of transaction_hash_sharded for values with (modulus 32, remainder 0);
create table if not exists transaction_hash_sharded_01 partition of transaction_hash_sharded for values with (modulus 32, remainder 1);
create table if not exists transaction_hash_sharded_02 partition of transaction_hash_sharded for values with (modulus 32, remainder 2);
create table if not exists transaction_hash_sharded_03 partition of transaction_hash_sharded for values with (modulus 32, remainder 3);
create table if not exists transaction_hash_sharded_04 partition of transaction_hash_sharded for values with (modulus 32, remainder 4);
create table if not exists transaction_hash_sharded_05 partition of transaction_hash_sharded for values with (modulus 32, remainder 5);
create table if not exists transaction_hash_sharded_06 partition of transaction_hash_sharded for values with (modulus 32, remainder 6);
create table if not exists transaction_hash_sharded_07 partition of transaction_hash_sharded for values with (modulus 32, remainder 7);
create table if not exists transaction_hash_sharded_08 partition of transaction_hash_sharded for values with (modulus 32, remainder 8);
create table if not exists transaction_hash_sharded_09 partition of transaction_hash_sharded for values with (modulus 32, remainder 9);
create table if not exists transaction_hash_sharded_10 partition of transaction_hash_sharded for values with (modulus 32, remainder 10);
create table if not exists transaction_hash_sharded_11 partition of transaction_hash_sharded for values with (modulus 32, remainder 11);
create table if not exists transaction_hash_sharded_12 partition of transaction_hash_sharded for values with (modulus 32, remainder 12);
create table if not exists transaction_hash_sharded_13 partition of transaction_hash_sharded for values with (modulus 32, remainder 13);
create table if not exists transaction_hash_sharded_14 partition of transaction_hash_sharded for values with (modulus 32, remainder 14);
create table if not exists transaction_hash_sharded_15 partition of transaction_hash_sharded for values with (modulus 32, remainder 15);
create table if not exists transaction_hash_sharded_16 partition of transaction_hash_sharded for values with (modulus 32, remainder 16);
create table if not exists transaction_hash_sharded_17 partition of transaction_hash_sharded for values with (modulus 32, remainder 17);
create table if not exists transaction_hash_sharded_18 partition of transaction_hash_sharded for values with (modulus 32, remainder 18);
create table if not exists transaction_hash_sharded_19 partition of transaction_hash_sharded for values with (modulus 32, remainder 19);
create table if not exists transaction_hash_sharded_20 partition of transaction_hash_sharded for values with (modulus 32, remainder 20);
create table if not exists transaction_hash_sharded_21 partition of transaction_hash_sharded for values with (modulus 32, remainder 21);
create table if not exists transaction_hash_sharded_22 partition of transaction_hash_sharded for values with (modulus 32, remainder 22);
create table if not exists transaction_hash_sharded_23 partition of transaction_hash_sharded for values with (modulus 32, remainder 23);
create table if not exists transaction_hash_sharded_24 partition of transaction_hash_sharded for values with (modulus 32, remainder 24);
create table if not exists transaction_hash_sharded_25 partition of transaction_hash_sharded for values with (modulus 32, remainder 25);
create table if not exists transaction_hash_sharded_26 partition of transaction_hash_sharded for values with (modulus 32, remainder 26);
create table if not exists transaction_hash_sharded_27 partition of transaction_hash_sharded for values with (modulus 32, remainder 27);
create table if not exists transaction_hash_sharded_28 partition of transaction_hash_sharded for values with (modulus 32, remainder 28);
create table if not exists transaction_hash_sharded_29 partition of transaction_hash_sharded for values with (modulus 32, remainder 29);
create table if not exists transaction_hash_sharded_30 partition of transaction_hash_sharded for values with (modulus 32, remainder 30);
create table if not exists transaction_hash_sharded_31 partition of transaction_hash_sharded for values with (modulus 32, remainder 31);
create index if not exists transaction_hash_sharded__hash on transaction_hash_sharded using hash (hash);

create or replace view transaction_hash as
select * from transaction_hash_old union all select * from transaction_hash_sharded;
