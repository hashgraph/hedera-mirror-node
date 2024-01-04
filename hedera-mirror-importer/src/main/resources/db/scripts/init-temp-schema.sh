#!/bin/bash
set -e

DB_TEMP_SCHEMA="${DB_TEMP_SCHEMA:-temporary}"
DB_NAME="${DB_NAME:-mirror_node}"

SCHEMA_EXISTS="$(psql -d "user=postgres connect_timeout=3 dbname=${DB_NAME}" \
                  -XAt \
                  -c "select exists (select schema_name from information_schema.schemata where schema_name = '${DB_TEMP_SCHEMA}')")"

if [[ $SCHEMA_EXISTS == 't' ]]
then
  echo "Temp schema ${DB_TEMP_SCHEMA} already exists";
  exit 0;
fi

echo "Creating temp schema ${DB_TEMP_SCHEMA}"

psql -d "user=postgres connect_timeout=3" \
  --set ON_ERROR_STOP=1 \
  --set "dbName=${DB_NAME}" \
  --set "dbSchema=${DB_SCHEMA:-public}" \
  --set "importerUsername=${IMPORTER_USERNAME:-mirror_importer}" \
  --set "ownerUsername=${OWNER_USERNAME:-mirror_node}" \
  --set "tempSchema=${DB_TEMP_SCHEMA}"  <<__SQL__

\connect :dbName

create role temporary_admin in role readwrite;

-- Grant temp schema privileges
grant temporary_admin to :ownerUsername;
grant temporary_admin to :importerUsername;

-- Create temp table schema
create schema if not exists :tempSchema authorization temporary_admin;
grant usage on schema :tempSchema to public;
revoke create on schema :tempSchema from public;

-- Grant readonly privileges
grant select on all tables in schema :tempSchema to readonly;
grant select on all sequences in schema :tempSchema to readonly;
grant usage on schema :tempSchema to readonly;
alter default privileges in schema :tempSchema grant select on tables to readonly;
alter default privileges in schema :tempSchema grant select on sequences to readonly;

-- Alter search path
alter database :dbName set search_path = :dbSchema, public, :tempSchema;
__SQL__

echo "Finished creating temp schema ${DB_TEMP_SCHEMA}"