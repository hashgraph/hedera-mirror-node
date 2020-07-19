-------------------
-- Support db storage of netowrk address books
-------------------

-- add address book table
create table if not exists address_book
(
    consensus_timestamp         nanos_timestamp primary key not null,
    start_consensus_timestamp   nanos_timestamp null,
    end_consensus_timestamp     nanos_timestamp null,
    file_id                     entity_id       not null,
    node_count                  smallint        null,
    file_data                   bytea           not null,
    is_complete                 boolean         not null default false,
    operation_type              smallint        not null
);

create index if not exists address_book__fileId_isComplete_operationType
    on address_book (file_id, is_complete, operation_type);

-- add node address table
create table if not exists node_address
(
    id                      serial          primary key,
    consensus_timestamp     nanos_timestamp not null,
    ip                      varchar(128)    null,
    port                    integer         null,
    memo                    bytea           not null,
    public_key              bytea           null,
    node_id                 bigint          null,
    node_account_id         entity_id       null,
    node_cert_hash          bytea           null
);

create index if not exists node_address__id_timestamp
    on node_address (id, consensus_timestamp);

-- fill in historical files
-- if table is empty
-- store bootsrap content
-- select on file_data table. If contents parse to address boo file ID then store in address_book, get consensustimestamp and data
-- might need to break out to db and application migration
-- application will have to read address_book table and fill in items of complete etc and also add node_address items
