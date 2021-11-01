-- add the public key index for contract table
create index if not exists contract__public_key
    on contract (public_key) where public_key is not null;
