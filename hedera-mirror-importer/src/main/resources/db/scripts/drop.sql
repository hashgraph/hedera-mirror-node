-- drop db items allowing for restart of mirror node parsing operations against a new network
-- Change the values below appropriately if not using defaults

-- drop tables, views, indexes, data types, functions, stored procedures and operators associated with db
drop schema if exists public cascade;

-- recreate schema used by init script
create schema public;
grant usage on schema public to public;

-- drop api user created in v1 flyway migration
drop user if exists mirror_api;

-- re-grant grpc stream user access
alter default privileges in schema public grant select on tables to mirror_grpc;
grant select on all tables in schema public to mirror_grpc;
