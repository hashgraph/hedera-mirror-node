alter table if exists address_book_entry
    add column if not exists file_id bigint not null;

alter table if exists address_book_service_endpoint
    add column if not exists file_id bigint not null;

alter table if exists live_hash
    add column if not exists entity_id bigint;

