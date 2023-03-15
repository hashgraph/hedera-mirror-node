create table if not exists transaction_hash_sharded_00
(
    like transaction_hash including indexes including constraints
);
create table if not exists transaction_hash_sharded_01
(
    like transaction_hash including indexes including constraints
);
create table if not exists transaction_hash_sharded_02
(
    like transaction_hash including indexes including constraints
);
create table if not exists transaction_hash_sharded_03
(
    like transaction_hash including indexes including constraints
);
create table if not exists transaction_hash_sharded_04
(
    like transaction_hash including indexes including constraints
);
create table if not exists transaction_hash_sharded_05
(
    like transaction_hash including indexes including constraints
);
create table if not exists transaction_hash_sharded_06
(
    like transaction_hash including indexes including constraints
);
create table if not exists transaction_hash_sharded_07
(
    like transaction_hash including indexes including constraints
);
create table if not exists transaction_hash_sharded_08
(
    like transaction_hash including indexes including constraints
);
create table if not exists transaction_hash_sharded_09
(
    like transaction_hash including indexes including constraints
);
create table if not exists transaction_hash_sharded_10
(
    like transaction_hash including indexes including constraints
);
create table if not exists transaction_hash_sharded_11
(
    like transaction_hash including indexes including constraints
);
create table if not exists transaction_hash_sharded_12
(
    like transaction_hash including indexes including constraints
);
create table if not exists transaction_hash_sharded_13
(
    like transaction_hash including indexes including constraints
);
create table if not exists transaction_hash_sharded_14
(
    like transaction_hash including indexes including constraints
);
create table if not exists transaction_hash_sharded_15
(
    like transaction_hash including indexes including constraints
);
create table if not exists transaction_hash_sharded_16
(
    like transaction_hash including indexes including constraints
);
create table if not exists transaction_hash_sharded_17
(
    like transaction_hash including indexes including constraints
);
create table if not exists transaction_hash_sharded_18
(
    like transaction_hash including indexes including constraints
);
create table if not exists transaction_hash_sharded_19
(
    like transaction_hash including indexes including constraints
);
create table if not exists transaction_hash_sharded_20
(
    like transaction_hash including indexes including constraints
);
create table if not exists transaction_hash_sharded_21
(
    like transaction_hash including indexes including constraints
);
create table if not exists transaction_hash_sharded_22
(
    like transaction_hash including indexes including constraints
);
create table if not exists transaction_hash_sharded_23
(
    like transaction_hash including indexes including constraints
);
create table if not exists transaction_hash_sharded_24
(
    like transaction_hash including indexes including constraints
);
create table if not exists transaction_hash_sharded_25
(
    like transaction_hash including indexes including constraints
);
create table if not exists transaction_hash_sharded_26
(
    like transaction_hash including indexes including constraints
);
create table if not exists transaction_hash_sharded_27
(
    like transaction_hash including indexes including constraints
);
create table if not exists transaction_hash_sharded_28
(
    like transaction_hash including indexes including constraints
);
create table if not exists transaction_hash_sharded_29
(
    like transaction_hash including indexes including constraints
);
create table if not exists transaction_hash_sharded_30
(
    like transaction_hash including indexes including constraints
);
create table if not exists transaction_hash_sharded_31
(
    like transaction_hash including indexes including constraints
);

CREATE OR REPLACE FUNCTION get_transaction_info_by_hash(bytea)
    returns TABLE
            (
                consensus_timestamp bigint,
                payer_account_id    bigint
            )
AS
$$
DECLARE
    shard varchar;
BEGIN
    shard
        := concat('transaction_hash_sharded_', to_char(get_byte($1, 0) % 32, 'fm00'));
    RETURN QUERY EXECUTE 'SELECT consensus_timestamp, payer_account_id from transaction_hash where hash = $1 ' ||
                         'UNION ALL SELECT consensus_timestamp,payer_account_id from ' || shard ||
                         ' WHERE hash = $1'
        USING $1;
END
$$ LANGUAGE plpgsql;

