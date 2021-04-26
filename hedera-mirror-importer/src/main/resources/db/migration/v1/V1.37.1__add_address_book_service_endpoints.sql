-------------------
-- Support proto updates in 0.13.0-rc.1 with respect to address book end points
-------------------

-- Update address book entry with  description and stake columns
alter table if exists address_book_entry
    add column if not exists description varchar(100) null,
    add column if not exists stake       bigint       null;


-- address_book_service_endpoint
create table if not exists address_book_service_endpoint
(
    consensus_timestamp bigint      not null,
    ip_address_v4       varchar(20) not null,
    port                integer     not null,
    node_account_id     bigint      null
);
comment on table address_book_service_endpoint is 'Network address book node service endpoints';

-- add indexes
alter table address_book_service_endpoint
    add primary key (consensus_timestamp, node_account_id, ip_address_v4);
create index if not exists address_book_service_endpoint__timestamp
    on address_book_service_endpoint (consensus_timestamp);
create index if not exists address_book_service_endpoint__timestamp_account_id
    on address_book_service_endpoint (consensus_timestamp, node_account_id);


-- migrate endpoints from address_book_entry to address_book_service_endpoint
insert into address_book_service_endpoint (consensus_timestamp, ip_address_v4, port, node_account_id)
select consensus_timestamp,
       ip,
       port,
       node_account_id
from address_book_entry
where node_account_id is not null
  and ip is not null
  and ip != ''
order by consensus_timestamp asc;

-- Update address book entry dropping ip and port columns
alter table if exists address_book_entry
    drop column if exists ip,
    drop column if exists port;
