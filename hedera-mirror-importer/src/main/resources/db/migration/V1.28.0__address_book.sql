-------------------
-- Support db storage of network address books
-------------------

-- add address book table
create table if not exists address_book
(
    consensus_timestamp         nanos_timestamp primary key,
    start_consensus_timestamp   nanos_timestamp null,
    end_consensus_timestamp     nanos_timestamp null,
    file_id                     entity_id       not null,
    node_count                  smallint        null,
    file_data                   bytea           not null
);

create index if not exists address_book__fileId
    on address_book (consensus_timestamp, file_id);

-- add node address table
create table if not exists address_book_entry
(
    id                      serial          primary key,
    consensus_timestamp     nanos_timestamp not null,
    ip                      varchar(128)    null,
    port                    integer         null,
    memo                    varchar(128)    not null,
    public_key              varchar(1024)   null,
    node_id                 bigint          null,
    node_account_id         entity_id       null,
    node_cert_hash          bytea           null
);

create index if not exists address_book_entry__id_timestamp
    on address_book_entry (id, consensus_timestamp);

-- add foreign key
alter table address_book_entry
    add constraint fk__address_book foreign key (consensus_timestamp) references address_book (consensus_timestamp);

-- Update file_data with fileID
alter table if exists file_data
    add column if not exists entity_id entity_id null,
    add column if not exists transaction_type smallint null;

-- retrieve file_data entity_id and transaction type from transaction table
update file_data
set entity_id = t.entity_id, transaction_type = t.type
from transaction t
where consensus_timestamp = t.consensus_ns;

alter table if exists file_data
    alter column entity_id set not null,
    alter column transaction_type set not null;
