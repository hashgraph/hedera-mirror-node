-- init the timescale db mirror node db
-- Change the values below if you are not installing via Docker

\set db_host 'localhost'
\set db_port 6432
\set db_name 'mirror_node'
\set db_super_user 'postgres'
\set db_owner 'mirror_node'
\set owner_password 'mirror_node_pass'
\set importer_user 'mirror_importer'
\set importer_password 'mirror_importer_pass'
\set grpc_user 'mirror_grpc'
\set grpc_password 'mirror_grpc_pass'
\set rest_user 'mirror_api'
\set rest_password 'mirror_api_pass'
\set schema_name 'mirrornode'

-- create owner user
create user :db_owner with login createrole password :'owner_password';

-- create primary user and db
create database :db_name with owner :db_owner;
\c :db_name :db_owner

-- create users
create user :importer_user with login password :'importer_password';
create role viewer;
create user :grpc_user with login password :'grpc_password' in role viewer;
create user :rest_user with login password :'rest_password' in role viewer;

-- grant connect access to api users
grant connect on database :db_name to viewer;

-- schema
create schema if not exists :schema_name authorization :db_owner;
grant usage on schema :schema_name to public;

-- alter search path for given schema
alter user :db_owner set search_path = :schema_name, public;
alter user :importer_user set search_path = :schema_name, public;
alter user :grpc_user set search_path = :schema_name, public;
alter user :rest_user set search_path = :schema_name, public;

-- grant select privileges on past and future tables and sequences to users
grant all privileges on all tables in schema :schema_name to :db_owner;
grant all privileges on all sequences in schema :schema_name to :db_owner;
grant select on all tables in schema :schema_name to :importer_user;
grant select on all tables in schema :schema_name to viewer;
grant select on all sequences in schema :schema_name to :importer_user;
grant select on all sequences in schema :schema_name to viewer;
alter default privileges in schema :schema_name grant select on tables to :importer_user;
alter default privileges in schema :schema_name grant select on tables to viewer;
alter default privileges in schema :schema_name grant select on sequences to :importer_user;
alter default privileges in schema :schema_name grant select on sequences to viewer;

-- add extensions, ensuring they're available to new schema
-- drop extension if exists timescaledb;
\c :db_name :db_super_user
drop extension if exists timescaledb;
-- must reconnect otherwise fails with "Start a new session and execute CREATE EXTENSION as the first command. Make sure to pass the "-X" flag to psql."
\c :db_name :db_super_user
create extension if not exists timescaledb schema :schema_name cascade;
create extension if not exists pg_stat_statements schema :schema_name;
