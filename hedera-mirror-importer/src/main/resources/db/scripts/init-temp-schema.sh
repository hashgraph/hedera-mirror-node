#!/bin/bash
set -e
TEMP_SCHEMA="${HEDERA_MIRROR_IMPORTER_DB_TEMPSCHEMA:-temporary}"
SCHEMA_EXISTS="$(psql -d "user=postgres connect_timeout=3" \
                  -XAt \
                  -c "select exists (select schema_name from information_schema.schemata where schema_name = '$TEMP_SCHEMA')")"

if [[ $SCHEMA_EXISTS == 't' ]]
then
  echo "Temp schema $TEMP_SCHEMA already exists";
  exit 0;
fi

echo "Creating temp schema $TEMP_SCHEMA"

psql -d "user=postgres connect_timeout=3" \
  --set ON_ERROR_STOP=1 \
  --set "dbName=${DB_NAME:-mirror_node}" \
  --set "dbSchema=${DB_SCHEMA:-public}" \
  --set "importerUsername=${IMPORTER_USERNAME:-mirror_importer}" \
  --set "ownerUsername=${OWNER_USERNAME:-mirror_node}" \
  --set "tempSchema=$TEMP_SCHEMA"  <<__SQL__
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

-- Grant readwrite privileges
grant insert, update, delete on all tables in schema :tempSchema to readwrite;
grant usage on all sequences in schema :tempSchema to readwrite;
alter default privileges in schema :tempSchema grant insert, update, delete on tables to readwrite;
alter default privileges in schema :tempSchema grant usage on sequences to readwrite;
alter database dbName set search_path = :dbSchema, public, :tempSchema;
__SQL__

echo "Finished creating temp schema $TEMP_SCHEMA"