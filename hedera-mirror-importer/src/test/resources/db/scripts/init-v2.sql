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

-- Owner privileges
grant all on schema public to mirror_node;
grant temporary on database mirror_node to mirror_node;


-- Create cast UDFs for citus create_time_partitions
CREATE FUNCTION nanos_to_timestamptz(nanos bigint) RETURNS timestamptz
    LANGUAGE plpgsql AS
'
DECLARE
value timestamptz;
BEGIN
select to_timestamp(nanos / 1000000000.0)
into value;
return value;
END;
';
CREATE CAST (bigint AS timestamptz) WITH FUNCTION nanos_to_timestamptz(bigint);

CREATE FUNCTION timestamptz_to_nanos(ts timestamptz) RETURNS bigint
    LANGUAGE plpgsql AS
'
DECLARE
value bigint;
BEGIN
select extract(epoch from ts) * 1000000000
into value;
return value;
END;
';
CREATE CAST (timestamptz AS bigint) WITH FUNCTION timestamptz_to_nanos(timestamptz);
