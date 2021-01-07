--- add columns to support new record file format
alter table if exists record_file add column if not exists hash varchar(96);
alter table if exists record_file add column if not exists version int not null default 2;
alter table if exists record_file alter column version drop default;

