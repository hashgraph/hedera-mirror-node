-- set nonce to null for all contracts in the entity table
update entity
set ethereum_nonce = null
where type='CONTRACT';
