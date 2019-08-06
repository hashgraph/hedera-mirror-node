--\echo Cleaning existing tables
drop table if exists t_events;
--\echo Creating events table
create table t_events
(
    id                      bigint  not null,
    consensus_order         bigint  not null,
    creator_node_id         bigint  not null,
    creator_seq             bigint  not null,
    other_node_id           bigint,
    other_seq               bigint,
    signature               bytea   not null,
    hash                    bytea   not null,
    self_parent_id          bigint,
    other_parent_id         bigint,
    self_parent_hash        bytea,
    other_parent_hash       bytea,
    self_parent_generation  bigint,
    other_parent_generation bigint,
    generation              bigint  not null,
    created_timestamp_ns    bigint  not null,
    consensus_timestamp_ns  bigint  not null,
    latency_ns              bigint  not null,
    txs_bytes_count         integer not null,
    platform_tx_count       integer not null,
    app_tx_count            integer not null,
    constraint pk_events_id primary key (id)
);
create sequence if not exists pk_events_id_seq as bigint owned by t_events.id;
alter table t_events
    alter column id set default nextval('pk_events_id_seq');
alter table t_events
    add constraint fk_events_self_parent_id foreign key (self_parent_id) references t_events (id);
alter table t_events
    add constraint fk_events_other_parent_id foreign key (other_parent_id) references t_events (id);