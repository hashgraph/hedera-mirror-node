-- init the timescale db mirror node db
-- Change the values below if you are not installing via Docker

\set db_name 'mirror_node'
\set importer_user 'mirror_node'
\set importer_password 'mirror_node_pass'
\set importer_user 'mirror_node'
\set grpc_user 'mirror_grpc'
\set grpc_password 'mirror_grpc_pass'
\set rest_user 'mirror_api'
\set rest_password 'mirror_api_pass'
\set schema_name 'mirror_node'

-- create primary user and db
create database :db_name;
\c :db_name
create extension if not exists timescaledb cascade;

-- create users
create user :importer_user with login createrole password :'importer_password';
create role viewer;
create user :grpc_user with login password :'grpc_password' in role viewer;
create user :rest_user with login password :'rest_password' in role viewer;

-- grant connect access to api users
grant connect on database :db_name to :grpc_user;
grant connect on database :db_name to :rest_user;

-- schema
create schema if not exists :schema_name;
grant usage on schema :schema_name to public;
grant all privileges on all tables in schema :schema_name to :importer_user;

-- alter search path for given schema
alter user :importer_user set search_path = :schema_name, public;
alter user :grpc_user set search_path = :schema_name, public;
alter user :rest_user set search_path = :schema_name, public;

-- grant select privileges on past and future tables to api users
grant select on all tables in schema :schema_name to :importer_user;
grant select on all tables in schema :schema_name to viewer;
alter default privileges for role :importer_user in schema :schema_name grant select on tables to viewer;

-- add extensions
create extension if not exists timescaledb cascade;
create extension pg_stat_statements;
