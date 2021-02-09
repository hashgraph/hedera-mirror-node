create table if not exists event_file
(
    bytes           bytea                 null,
    consensus_start bigint                not null,
    consensus_end   bigint primary key,
    count           bigint                not null,
    file_hash       character varying(96) not null,
    hash            character varying(96) null,
    load_start      bigint                not null,
    load_end        bigint                not null,
    name            character varying(250),
    node_account_id bigint                not null,
    previous_hash   character varying(96) null,
    version         integer               not null,
    constraint event_file__hash unique (hash)
);

alter table if exists account_balance_file
    add column if not exists bytes bytea;

delete
from account_balance_file
where load_end is null;

alter table if exists record_file
    add column if not exists bytes bytea;

delete
from record_file
where load_end is null;

drop table if exists t_application_status;
