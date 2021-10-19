-- TODO add comment

create type entity_type as enum ('ACCOUNT', 'CONTRACT', 'FILE', 'TOPIC', 'TOKEN', 'SCHEDULE');

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
