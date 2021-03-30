-- init the timescale db mirror node db
-- Change the values below if you are not installing via Docker

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
create user :db_owner with login password :'owner_password';

-- create primary user and db
create database :db_name with owner :db_owner;

-- create roles
create role readonly;
create role readwrite in role readonly;

-- create users
create user :grpc_user with login password :'grpc_password' in role readonly;
create user :rest_user with login password :'rest_password' in role readonly;
create user :importer_user with login password :'importer_password' in role readwrite;

-- drop timescaledb extension for future install to ensure availability in custom schema
drop extension if exists timescaledb cascade;

-- connect with db owner to create schema and set schema user permissions
\c :db_name :db_owner
create schema if not exists :schema_name authorization :db_owner;
grant usage on schema :schema_name to public;

-- revoke default public permissions on schema
revoke create on schema :schema_name from public;

-- grant connect and schema access to readonly role
grant connect on database :db_name to readonly;
grant usage on schema :schema_name to readonly;

-- grant select privileges on tables to readonly
grant select on all tables in schema :schema_name to readonly;
alter default privileges in schema :schema_name grant select on tables to readonly;

-- grant select privileges on sequences to readonly
grant select on all sequences in schema :schema_name to readonly;
alter default privileges in schema :schema_name grant select on sequences to readonly;

-- grant write privileges on sequences to readwrite
grant insert, update on all tables in schema :schema_name to readwrite;
alter default privileges in schema :schema_name grant insert, update on tables to readwrite;
grant usage on all sequences in schema :schema_name to readwrite;
alter default privileges in schema :schema_name grant usage on sequences to readwrite;

-- alter search path for given schema as super user
\c :db_name :db_super_user
alter user :db_owner set search_path = :schema_name, public;
alter user :importer_user set search_path = :schema_name, public;
alter user :grpc_user set search_path = :schema_name, public;
alter user :rest_user set search_path = :schema_name, public;

-- add extensions, ensuring they're available to new schema
create extension if not exists timescaledb cascade schema :schema_name;
create extension if not exists pg_stat_statements cascade schema :schema_name;
alter database :db_name set timescaledb.telemetry_level = off;
