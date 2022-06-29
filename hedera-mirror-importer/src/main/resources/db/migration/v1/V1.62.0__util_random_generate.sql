create table if not exists util_random_generate
(
    consensus_timestamp bigint  not null,
    range               integer not null,
    pseudorandom_bytes  bytea   null,
    pseudorandom_number integer null,
    primary key (consensus_timestamp)
)
