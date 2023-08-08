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
grant create on database mirror_node to mirror_node;
grant all on schema public to mirror_node;
grant temporary on database mirror_node to mirror_node;


-- Create cast UDFs for citus create_time_partitions
CREATE FUNCTION nanos_to_timestamptz(nanos bigint) RETURNS timestamptz
    LANGUAGE plpgsql AS
'
DECLARE
value timestamptz;
BEGIN
select to_timestamp(nanos * 1.0 / 1000000000)
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
