-- Add extensions
create extension if not exists btree_gist;

-- Create roles
create role readonly;
create role readwrite in role readonly;

-- Create user mirror_importer
create user mirror_importer with login password 'mirror_importer_pass' in role readwrite;

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

-- Partition privileges
create extension if not exists pg_cron;
create schema if not exists partman authorization mirror_node;
create extension if not exists pg_partman schema partman;
alter schema partman owner to mirror_node;
grant all on schema partman to mirror_node;
grant usage on schema cron to mirror_node;
grant all on all tables in schema partman to mirror_node;
grant execute on all functions in schema partman to mirror_node;
grant execute on all procedures in schema partman to mirror_node;
grant all on schema public to mirror_node;
grant temporary on database mirror_node to mirror_node;
