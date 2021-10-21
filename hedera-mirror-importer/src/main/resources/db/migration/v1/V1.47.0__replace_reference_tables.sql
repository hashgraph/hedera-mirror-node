-- TODO add comment

create type entity_type as enum ('ACCOUNT', 'CONTRACT', 'FILE', 'TOPIC', 'TOKEN', 'SCHEDULE');

-- Alter t_transaction_types to use the new enum
alter table t_transaction_types
    add column entity_type_enum entity_type null;

update t_transaction_types
    set entity_type_enum = 'ACCOUNT'
    where entity_type = 1;

update t_transaction_types
    set entity_type_enum = 'CONTRACT'
    where entity_type = 2;

update t_transaction_types
    set entity_type_enum = 'FILE'
    where entity_type = 3;

update t_transaction_types
    set entity_type_enum = 'TOPIC'
    where entity_type = 4;

update t_transaction_types
    set entity_type_enum = 'TOKEN'
    where entity_type = 5;

update t_transaction_types
    set entity_type_enum = 'SCHEDULE'
    where entity_type = 6;

alter table t_transaction_types
    drop column entity_type;

alter table t_transaction_types
    rename column entity_type_enum TO entity_type;

-- Alter entity to use the new enum
alter table entity
    add column type_enum entity_type null;

update entity
    set type_enum = 'ACCOUNT'
    where type = 1;

update entity
    set type_enum = 'CONTRACT'
    where type = 2;

update entity
    set type_enum = 'FILE'
    where type = 3;

update entity
    set type_enum = 'TOPIC'
    where type = 4;

update entity
    set type_enum = 'TOKEN'
    where type = 5;

update entity
    set type_enum = 'SCHEDULE'
    where type = 6;

alter table entity
    drop column type;

alter table entity
    rename column type_enum TO type;

-- Alter entity_history to use the new enum
alter table entity_history
    add column type_enum entity_type null;

update entity_history
    set type_enum = 'ACCOUNT'
    where type = 1;

update entity_history
    set type_enum = 'CONTRACT'
    where type = 2;

update entity_history
    set type_enum = 'FILE'
    where type = 3;

update entity_history
    set type_enum = 'TOPIC'
    where type = 4;

update entity_history
    set type_enum = 'TOKEN'
    where type = 5;

update entity_history
    set type_enum = 'SCHEDULE'
    where type = 6;

alter table entity_history
    drop column type;

alter table entity_history
    rename column type_enum TO type;

-- Alter contract to use the new enum
alter table contract
    add column type_enum entity_type null;

update contract
    set type_enum = 'ACCOUNT'
    where type = 1;

update contract
    set type_enum = 'CONTRACT'
    where type = 2;

update contract
    set type_enum = 'FILE'
    where type = 3;

update contract
    set type_enum = 'TOPIC'
    where type = 4;

update contract
    set type_enum = 'TOKEN'
    where type = 5;

update contract
    set type_enum = 'SCHEDULE'
    where type = 6;

alter table contract
    drop column type;

alter table contract
    rename column type_enum TO type;

-- Alter contract to use the new enum
alter table contract_history
    add column type_enum entity_type null;

update contract_history
    set type_enum = 'ACCOUNT'
    where type = 1;

update contract_history
    set type_enum = 'CONTRACT'
    where type = 2;

update contract_history
    set type_enum = 'FILE'
    where type = 3;

update contract_history
    set type_enum = 'TOPIC'
    where type = 4;

update contract_history
    set type_enum = 'TOKEN'
    where type = 5;

update contract_history
    set type_enum = 'SCHEDULE'
    where type = 6;

alter table contract_history
    drop column type;

alter table contract_history
    rename column type_enum TO type;
