-- Add extensions
create extension if not exists btree_gist;

-- Create roles
create role readonly;
create role readwrite in role readonly;

-- Create user mirror_importer
create user mirror_node with login password 'mirror_node_pass' in role readwrite;

-- Create schema
create schema if not exists public authorization mirror_node;

-- Partition privileges
grant connect on database mirror_node to readwrite;
grant all privileges on database mirror_node to mirror_node;
create extension if not exists pg_cron;
create schema if not exists partman authorization mirror_node;
create extension if not exists pg_partman schema partman;
alter schema partman owner to mirror_node;
grant create on database mirror_node to mirror_node;
grant all on schema partman to mirror_node;
grant usage on schema cron to mirror_node;
grant all on all tables in schema partman to mirror_node;
grant execute on all functions in schema partman to mirror_node;
grant execute on all procedures in schema partman to mirror_node;
grant all on schema public to mirror_node;
grant temporary on database mirror_node to mirror_node;
