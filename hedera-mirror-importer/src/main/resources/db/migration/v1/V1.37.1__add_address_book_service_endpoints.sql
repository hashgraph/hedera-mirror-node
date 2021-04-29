-------------------
-- Support proto updates in 0.13.0-rc.1 with respect to address book end points
-------------------

-- Update address book entry with description and stake columns
alter table if exists address_book_entry
    add column if not exists description varchar(100) null,
    add column if not exists stake       bigint       null;


-- address_book_service_endpoint
create table if not exists address_book_service_endpoint
(
    consensus_timestamp bigint      not null,
    ip_address_v4       varchar(15) not null,
    node_id             bigint      null,
    port                integer     not null
);
comment on table address_book_service_endpoint is 'Network address book node service endpoints';

-- add address_book_service_endpoint index
alter table address_book_service_endpoint
    add primary key (consensus_timestamp, node_id, ip_address_v4, port);

-- migrate endpoints from address_book_entry to address_book_service_endpoint
insert into address_book_service_endpoint (consensus_timestamp, ip_address_v4, port, node_id)
select consensus_timestamp,
       ip,
       port,
       node_id
from address_book_entry
where node_id is not null
  and ip is not null
  and ip != ''
order by consensus_timestamp asc;

-- Update address_book_entry dropping ip and port columns
alter table if exists address_book_entry
    drop column if exists ip,
    drop column if exists port;

-- ensure node_account_id is no longer empty. Early address books have node_account_id offset from node_id by 3
-- initial addressBook at time 1 has node_id = 0, this can be skipped as it has been verified to have no duplicates
update address_book_entry
set node_account_id = node_id + 3
where node_account_id is null
  and consensus_timestamp > 1;

-- initial addressBook at time 1 has all rows node_id = 0, and has been verified to have no duplicates
-- correct 0 and null cases of node_account_id and node_id by splitting memo and extracting num
update address_book_entry
set node_account_id = split_part(memo, '.', 3)::int,
    node_id         = split_part(memo, '.', 3)::int - 3
where consensus_timestamp = 1
  and node_id = 0;

-- update node_account_id and node_id to have mandatory population
alter table if exists address_book_entry
    alter column node_account_id set not null,
    alter column node_id set not null;

-- collapse duplicate node_id rows in address_book_entry
-- join table on self and remove duplicates with a lower id leaving a single instance of a memo-consensus_timestamp combo
delete
from address_book_entry a
    using address_book_entry b
where a.id < b.id
  and a.memo = b.memo
  and a.consensus_timestamp = b.consensus_timestamp;

-- remove id and update primary key to be consensus_timestamp, node_id
alter table if exists address_book_entry
    drop column if exists id;
alter table if exists address_book_entry
    add primary key (consensus_timestamp, node_id);

-- update address_book node_count
with entry_map as (
    select consensus_timestamp, count(consensus_timestamp) from address_book_entry group by consensus_timestamp
)
update address_book
set node_count = count
from entry_map
where start_consensus_timestamp = entry_map.consensus_timestamp;
