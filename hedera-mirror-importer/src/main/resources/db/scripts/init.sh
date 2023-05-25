#!/bin/bash
set -e

PGCONF="${PGCONF:-/var/lib/postgresql/data}"
PGHBA="${PGCONF}/pg_hba.conf"
DB_SPECIFIC_EXTENSION_SQL=
DB_SPECIFIC_SQL="alter user :ownerUsername with createrole;"

# v2 schema no longer creates the REST API user, while v1 schema still does
if [[ "${SCHEMA_V2}" == "true" ]]; then
  DB_SPECIFIC_EXTENSION_SQL="create extension if not exists btree_gist;
                             create extension citus;
                             grant all privileges on database :dbName to :ownerUsername;
                             create extension if not exists pg_cron;
                             create schema if not exists partman authorization :ownerUsername;
                             create extension if not exists pg_partman schema partman;
                             alter schema partman owner to :ownerUsername;
                             grant create on database :dbName to :ownerUsername;
                             grant all on schema partman to :ownerUsername;
                             grant usage on schema cron to :ownerUsername;
                             grant all on all tables in schema partman to :ownerUsername;
                             grant execute on all functions in schema partman to :ownerUsername;
                             grant execute on all procedures in schema partman to :ownerUsername;
                             grant all on schema public to :ownerUsername;
                             grant temporary on database :dbName to :ownerUsername;"
  DB_SPECIFIC_SQL="create user :restUsername with login password :'restPassword' in role readonly;"
fi

cp "${PGHBA}" "${PGHBA}.bak"
echo "local all all trust" > "${PGHBA}"
pg_ctl reload

psql -d "user=postgres connect_timeout=3" \
  --set ON_ERROR_STOP=1 \
  --set "dbName=${DB_NAME:-mirror_node}" \
  --set "dbSchema=${DB_SCHEMA:-public}" \
  --set "graphqlPassword=${GRAPHQL_PASSWORD:-mirror_graphql_pass}" \
  --set "graphqlUsername=${GRAPHQL_USERNAME:-mirror_graphql}" \
  --set "grpcPassword=${GRPC_PASSWORD:-mirror_grpc_pass}" \
  --set "grpcUsername=${GRPC_USERNAME:-mirror_grpc}" \
  --set "importerPassword=${IMPORTER_PASSWORD:-mirror_importer_pass}" \
  --set "importerUsername=${IMPORTER_USERNAME:-mirror_importer}" \
  --set "ownerPassword=${OWNER_PASSWORD:-mirror_node_pass}" \
  --set "ownerUsername=${OWNER_USERNAME:-mirror_node}" \
  --set "restPassword=${REST_PASSWORD:-mirror_api_pass}" \
  --set "restUsername=${REST_USERNAME:-mirror_api}" \
  --set "rosettaPassword=${ROSETTA_PASSWORD:-mirror_rosetta_pass}" \
  --set "rosettaUsername=${ROSETTA_USERNAME:-mirror_rosetta}" \
  --set "web3Password=${WEB3_PASSWORD:-mirror_web3_pass}" \
  --set "web3Username=${WEB3_USERNAME:-mirror_web3}" <<__SQL__

-- Create database & owner
create user :ownerUsername with login password :'ownerPassword';
create database :dbName with owner :ownerUsername;

-- Add extensions
create extension if not exists pg_stat_statements;

-- Create roles
create role readonly;
create role readwrite in role readonly;

-- Create users
create user :graphqlUsername with login password :'graphqlPassword' in role readonly;
create user :grpcUsername with login password :'grpcPassword' in role readonly;
create user :importerUsername with login password :'importerPassword' in role readwrite;
create user :rosettaUsername with login password :'rosettaPassword' in role readonly;
create user :web3Username with login password :'web3Password' in role readonly;
${DB_SPECIFIC_SQL}

\connect :dbName
alter schema public owner to :ownerUsername;

-- Create schema
\connect :dbName :ownerUsername
create schema if not exists :dbSchema authorization :ownerUsername;
grant usage on schema :dbSchema to public;
revoke create on schema :dbSchema from public;

-- Grant readonly privileges
grant connect on database :dbName to readonly;
grant select on all tables in schema :dbSchema to readonly;
grant select on all sequences in schema :dbSchema to readonly;
grant usage on schema :dbSchema to readonly;
alter default privileges in schema :dbSchema grant select on tables to readonly;
alter default privileges in schema :dbSchema grant select on sequences to readonly;

-- Grant readwrite privileges
grant insert, update, delete on all tables in schema :dbSchema to readwrite;
grant usage on all sequences in schema :dbSchema to readwrite;
alter default privileges in schema :dbSchema grant insert, update, delete on tables to readwrite;
alter default privileges in schema :dbSchema grant usage on sequences to readwrite;

-- Partition privileges
\connect :dbName postgres
${DB_SPECIFIC_EXTENSION_SQL}

-- Alter search path
\connect postgres postgres
alter database :dbName set search_path = :dbSchema, public;
__SQL__

mv "${PGHBA}.bak" "${PGHBA}"
pg_ctl reload
