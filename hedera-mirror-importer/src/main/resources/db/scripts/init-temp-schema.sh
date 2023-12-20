#!/bin/bash
set -e

psql -d "user=postgres connect_timeout=3" \
  --set ON_ERROR_STOP=1 \
  --set "dbName=${DB_NAME:-mirror_node}" \
  --set "dbSchema=${DB_SCHEMA:-public}" \
  --set "importerUsername=${IMPORTER_USERNAME:-mirror_importer}" \
  --set "ownerUsername=${OWNER_USERNAME:-mirror_node}" \
  --set "tempSchema=${HEDERA_MIRROR_IMPORTER_DB_TEMPSCHEMA:-temporary}"  <<__SQL__
\connect :dbName
create or replace function create_temp_schema(tempSchema varchar, dbSchema varchar, dbName varchar,
importerUsername varchar, ownerUsername varchar)
returns void as \$\$
declare
  schema_exists boolean := false;
begin
  select exists (select schema_name from information_schema.schemata where schema_name = tempSchema) into schema_exists;
  if schema_exists then
    raise notice 'The schema % already exists. Skipping creation.', tempSchema;
    return;
  end if;
  raise notice 'Creating schema %', tempSchema;
  execute format('create role %I_admin in role readwrite', tempSchema);

  -- Grant temp schema privileges
  execute format('grant %I_admin to %I', tempSchema, ownerUsername);
  execute format('grant %I_admin to %I', tempSchema, importerUsername);

  -- Create temp table schema
  execute format('create schema if not exists %I authorization %I_admin', tempSchema, tempSchema);
  execute format('grant usage on schema %I to public', tempSchema);
  execute format('revoke create on schema %I from public', tempSchema);

  -- Grant readonly privileges
  execute format('grant select on all tables in schema %I to readonly', tempSchema);
  execute format('grant select on all sequences in schema %I to readonly', tempSchema);
  execute format('grant usage on schema %I to readonly', tempSchema);
  execute format('alter default privileges in schema %I grant select on tables to readonly', tempSchema);
  execute format('alter default privileges in schema %I grant select on sequences to readonly', tempSchema);

  -- Grant readwrite privileges
  execute format('grant insert, update, delete on all tables in schema %I to readwrite', tempSchema);
  execute format('grant usage on all sequences in schema %I to readwrite', tempSchema);
  execute format('alter default privileges in schema %I grant insert, update, delete on tables to readwrite', tempSchema);
  execute format('alter default privileges in schema %I grant usage on sequences to readwrite', tempSchema);
  execute format('alter database %I set search_path = %I, public, %I', dbName, dbSchema, tempSchema);

  raise notice 'finished Creating schema %', tempSchema;
end;
\$\$ language plpgsql;

select create_temp_schema(:'tempSchema', :'dbSchema', :'dbName', :'importerUsername', :'ownerUsername');
drop function create_temp_schema;
__SQL__