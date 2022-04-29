alter table if exists entity
    add column if not exists ethereum_nonce bigint null,
    add column if not exists evm_address bytea null;

alter table if exists entity_history
    add column if not exists ethereum_nonce bigint null,
    add column if not exists evm_address bytea null;
