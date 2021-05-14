-- Change the values below if you are not installing via Docker

\set dbHost '127.0.0.1'
\set dbPort '5432'
\set dbName 'mirror_node'
\set dbSchema 'public'
\set grpcPassword 'mirror_grpc_pass'
\set grpcUsername 'mirror_grpc'
\set importerPassword 'mirror_importer_pass'
\set importerUsername 'mirror_importer'
\set ownerPassword 'mirror_node_pass'
\set ownerUsername 'mirror_node'
\set rosettaPassword 'mirror_rosetta_pass'
\set rosettaUsername 'mirror_rosetta'

create user :ownerUsername with createrole login password :'ownerPassword';
grant :ownerUsername to postgres;
create database :dbName with owner :ownerUsername;
create extension if not exists pg_stat_statements;

-- Create roles
create role readonly;
create role readwrite in role readonly;

-- Create users
create user :grpcUsername with login password :'grpcPassword' in role readonly;
create user :importerUsername with login password :'importerPassword' in role readwrite;
create user :rosettaUsername with login password :'rosettaPassword' in role readonly;

-- Create schema
\connect postgresql://:ownerUsername::ownerPassword@:dbHost::dbPort/:dbName
create schema if not exists :dbSchema authorization :ownerUsername;
revoke all privileges on schema :dbSchema from public;
grant usage on schema :dbSchema to public;
grant create on schema :dbSchema to :ownerUsername;

-- Grant readonly privileges
grant connect on database :dbName to readonly;
grant select on all tables in schema :dbSchema to readonly;
grant select on all sequences in schema :dbSchema to readonly;
grant usage on schema :dbSchema to readonly;
alter default privileges in schema :dbSchema grant select on tables to readonly;
alter default privileges in schema :dbSchema grant select on sequences to readonly;

-- Grant readwrite privileges
grant insert, update on all tables in schema :dbSchema to readwrite;
grant usage on all sequences in schema :dbSchema to readwrite;
alter default privileges in schema :dbSchema grant insert, update on tables to readwrite;
alter default privileges in schema :dbSchema grant usage on sequences to readwrite;
