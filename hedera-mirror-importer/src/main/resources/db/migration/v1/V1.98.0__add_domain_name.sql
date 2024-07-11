-- add a column and update the index on address_book_service_endpoint

alter table if exists address_book_service_endpoint drop constraint if exists address_book_service_endpoint_pkey;

alter table if exists address_book_service_endpoint
    alter column ip_address_v4 drop not null,
    alter column port drop default,
    alter column port drop not null,
    add column if not exists domain_name varchar(253) default null;

create index if not exists address_book_service_endpoint__timestamp_node_id
    on address_book_service_endpoint (consensus_timestamp , node_id);
