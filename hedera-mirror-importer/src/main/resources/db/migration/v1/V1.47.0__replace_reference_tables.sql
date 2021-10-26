-- Remove t_entity_types and alter tables referencing it to use a entity_type value instead.

-- Create the enum entity_type to replace the foreign key with
create type entity_type as enum ('ACCOUNT', 'CONTRACT', 'FILE', 'TOPIC', 'TOKEN', 'SCHEDULE');

drop function if exists updateEntityTypeFromInt(integer);

create function updateEntityTypeFromInt(integer)
    returns entity_type as $$
    begin
        case $1
            when 1 then return 'ACCOUNT';
            when 2 then return 'CONTRACT';
            when 3 then return 'FILE';
            when 4 then return 'TOPIC';
            when 5 then return 'TOKEN';
            when 6 then return 'SCHEDULE';
            else return null;
        end case;
    end; $$ LANGUAGE plpgsql;


-- Alter t_transaction_types to use the new enum entity_type
alter table t_transaction_types
    add column entity_type_enum entity_type null;

update t_transaction_types
    set entity_type_enum = updateEntityTypeFromInt(entity_type);

alter table t_transaction_types
    drop column entity_type;

alter table t_transaction_types
    rename column entity_type_enum TO entity_type;

-- Alter entity to use the new enum entity_type
alter table entity
    add column type_enum entity_type null;

update entity
    set type_enum = updateEntityTypeFromInt(type);

alter table entity
    drop column type;

alter table entity
    rename column type_enum TO type;

alter table entity
    alter column type set not null;

-- Alter entity_history to use the new enum entity_type
alter table entity_history
    add column type_enum entity_type null;

update entity_history
    set type_enum = updateEntityTypeFromInt(type);

alter table entity_history
    drop column type;

alter table entity_history
    rename column type_enum TO type;

alter table entity_history
    alter column type set not null;


-- Alter contract to use the new enum entity_type
alter table contract
    add column type_enum entity_type null;

update contract
    set type_enum = updateEntityTypeFromInt(type);

alter table contract
    drop column type;

alter table contract
    rename column type_enum TO type;

alter table contract
    alter column type set not null;

-- Alter contract to use the new enum entity_type
alter table contract_history
    add column type_enum entity_type null;

update contract_history
    set type_enum = updateEntityTypeFromInt(type);

alter table contract_history
    drop column type;

alter table contract_history
    rename column type_enum TO type;

alter table contract
    alter column type set not null;


-- Drop t_entity_types
drop table t_entity_types;
