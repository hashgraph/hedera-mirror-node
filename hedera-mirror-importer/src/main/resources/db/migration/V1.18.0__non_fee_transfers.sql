create table non_fee_transfers
(
    consensus_timestamp nanos_timestamp  not null,
    realm_num           entity_realm_num not null,
    entity_num          entity_num       not null,
    amount              hbar_tinybars    not null
);
comment on table non_fee_transfers is
    'non-fee related successful transfers for CryptoTransfer, CryptoCreate, ContractCreate, and ContractCall';

create index if not exists idx__non_fee_transfers__cts on non_fee_transfers (consensus_timestamp);
