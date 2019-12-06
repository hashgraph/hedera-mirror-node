drop table if exists topic_message;
create table if not exists topic_message (
  consensus_timestamp bigint primary key not null,
  message bytea not null,
  realm_num smallint not null,
  running_hash bytea not null,
  sequence_number bigint not null,
  topic_num int not null
);
