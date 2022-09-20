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
grant select on all sequences in schema public to readonly;
grant usage on schema public to readonly;
alter default privileges in schema public grant select on tables to readonly;
alter default privileges in schema public grant select on sequences to readonly;

-- Grant readwrite privileges
grant insert, update, delete on all tables in schema public to readwrite;
grant usage on all sequences in schema public to readwrite;
alter default privileges in schema public grant insert, update, delete on tables to readwrite;
alter default privileges in schema public grant usage on sequences to readwrite;
