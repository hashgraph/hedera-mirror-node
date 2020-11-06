-- Change the values below if you are not installing via Docker

\set db_name 'mirror_node'
\set db_user 'mirror_node'
\set db_password 'mirror_node_pass'
\set db_owner 'mirror_node'
\set grpc_user 'mirror_grpc'
\set grpc_password 'mirror_grpc_pass'
\set rosetta_user 'mirror_rosetta'
\set rosetta_password 'mirror_rosetta_pass'

do $$
begin
  create user mirror_node with createrole password 'mirror_node_pass';
  exception when duplicate_object then
  raise notice 'not creating user mirror_node -- it already exists';
end
$$;

select 'create database mirror_node'
where not exists (select from pg_database where datname = :'db_name')\gexec

do $$
begin
  create user mirror_grpc with login password 'mirror_grpc_pass';
  exception when duplicate_object then
  raise notice 'not creating user mirror_grpc -- it already exists';
end
$$;

grant connect on database :db_name to :grpc_user;

do $$
begin
  create user mirror_rosetta with login password 'mirror_rosetta_pass';
  exception when duplicate_object then
  raise notice 'not creating user mirror_rosetta -- it already exists';
end
$$;

grant connect on database :db_name to :rosetta_user;

\c :db_name

alter default privileges in schema public grant select on tables to :grpc_user;

grant select on all tables in schema public to :grpc_user;

alter default privileges in schema public grant select on tables to :rosetta_user;

grant select on all tables in schema public to :rosetta_user;
