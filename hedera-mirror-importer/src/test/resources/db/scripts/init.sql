-- Spring takes care of creating the database and owner in tests, but on a real database these commands should still be ran.
-- create user mirror_node with login createrole password 'mirror_node_pass';
-- grant mirror_node to postgres;
-- create database mirror_node with owner mirror_node;

-- Add extensions
create extension if not exists pg_stat_statements;

-- Create roles
create role readonly;
create role readwrite in role readonly;

-- Create users
create user mirror_grpc with login password 'mirror_grpc_pass' in role readonly;
create user mirror_importer with login password 'mirror_importer_pass' in role readwrite;
create user mirror_rosetta with login password 'mirror_rosetta_pass' in role readonly;
create user mirror_web3 with login password 'mirror_web3_pass' in role readonly;

-- Create schema
create schema if not exists public authorization mirror_node;
grant usage on schema public to public;
revoke create on schema public from public;

-- Grant readonly privileges
grant connect on database mirror_node to readonly;
grant select on all tables in schema public to readonly;
grant usage on schema public to readonly;
alter default privileges in schema public grant select on tables to readonly;

-- Grant readwrite privileges
grant insert, update, delete on all tables in schema public to readwrite;
alter default privileges in schema public grant insert, update, delete on tables to readwrite;
