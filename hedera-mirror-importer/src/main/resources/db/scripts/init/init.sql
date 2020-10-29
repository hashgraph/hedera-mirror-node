-- Change the values below if you are not installing via Docker

\set db_name 'mirror_node'
\set db_user 'mirror_node'
\set db_password 'mirror_node_pass'
\set db_owner 'mirror_node'
\set grpc_user 'mirror_grpc'
\set grpc_password 'mirror_grpc_pass'
\set rosetta_user 'mirror_rosetta'
\set rosetta_password 'mirror_rosetta_pass'

select 'CREATE DATABASE mirror_node'
where not exists (select from pg_database where datname = :'db_name')\gexec

do
$do$
begin
   if not exists (
      select from pg_catalog.pg_roles
      where  rolname = 'mirror_node') then

      create user mirror_node with login createrole PASSWORD 'mirror_node_pass';
   end if;
end
$do$;

create user :grpc_user with login password :'grpc_password';

grant connect on database :db_name to :grpc_user;

create user :rosetta_user with login password :'rosetta_password';

grant connect on database :db_name to :rosetta_user;

\c :db_name

alter default privileges in schema public grant select on tables to :grpc_user;

grant select on all tables in schema public to :grpc_user;

alter default privileges in schema public grant select on tables to :rosetta_user;

grant select on all tables in schema public to :rosetta_user;
