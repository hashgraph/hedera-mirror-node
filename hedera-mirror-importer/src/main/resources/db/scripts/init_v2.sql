-- Change the values below if you are not installing via Docker

\set db_name 'mirror_node'
\set db_schema 'mirror_node'
\set db_user 'mirror_node'
\set db_password 'mirror_node_pass'
\set db_owner 'mirror_node'
\set grpc_user 'mirror_grpc'
\set grpc_password 'mirror_grpc_pass'
\set rest_user 'mirror_api'
\set rest_password 'mirror_api_pass'
\set rosetta_user 'mirror_rosetta'
\set rosetta_password 'mirror_rosetta_pass'

-- create primary user and db
create user if not exists :db_user with login createrole password :'db_password';
create database :db_name with owner :db_owner;

-- create api users
create user if not exists :grpc_user with login password :grpc_password;
create user if not exists :rest_user with login password :rest_password;

-- grant connect access to api users
grant connect on database :db_name to :grpc_user;
grant connect on database :db_name to :rest_user;
grant connect on database :db_name to :rosetta_user;

\c :db_name

-- schema
create schema if not exists :schema_name;
grant usage on schema :schema_name to public;

-- alter search path for given schema
alter user :db_user set search_path = :schema_name, public;
alter user :grpc_user set search_path = :schema_name, public;
alter user :rest_user set search_path = :schema_name, public;
alter user :rosetta_user set search_path = :schema_name, public;

-- alter search path for given schema
alter default privileges in schema :db_schema grant select on tables to :grpc_user;
alter default privileges in schema :db_schema grant select on tables to :rest_user;
alter default privileges in schema :db_schema grant select on tables to :rosetta_user;

-- grant select privileges on past and future tables to api users
grant select on all tables in schema :db_schema to :grpc_user;
grant select on all tables in schema :db_schema to :rest_user;
grant select on all tables in schema :db_schema to :rosetta_user;
