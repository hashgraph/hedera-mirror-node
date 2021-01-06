#!/usr/bin/env bash
echo "BASH_VERSION: $BASH_VERSION"
set -e

. scripts/timescaledb/migration.config

if [[ -z $OLD_DB_HOST ]]; then
    echo "Old host name is not set. Please configure OLD_DB_HOST in migration.config file and rerun './scripts/timescaledb/migration.sh'"
    exit 1
fi

if [[ -z $OLD_DB_NAME ]]; then
    echo "Old db name is not set. Please configure OLD_DB_NAME in migration.config file and rerun './scripts/timescaledb/migration.sh'"
    exit 1
fi

if [[ -z $OLD_DB_PORT ]]; then
    echo "Old port is not set. Please configure OLD_DB_PORT in migration.config file and rerun './scripts/timescaledb/migration.sh'"
    exit 1
fi

if [[ -z $OLD_DB_USER ]]; then
    echo "Old db primary user name is not set. Please configure OLD_DB_USER in migration.config file and rerun './scripts/timescaledb/migration.sh'"
    exit 1
fi

if [[ -z $OLD_PASSWORD ]]; then
    echo "Old db primary user password is not set. Please configure OLD_PASSWORD in migration.config file and rerun './scripts/timescaledb/migration.sh'"
    exit 1
fi

if [[ -z $NEW_DB_HOST ]]; then
    echo "New host name is not set. Please configure NEW_DB_HOST in migration.config file and rerun './scripts/timescaledb/migration.sh'"
    exit 1
fi

if [[ -z $NEW_DB_NAME ]]; then
    echo "New db name is not set. Please configure NEW_DB_NAME in migration.config file and rerun './scripts/timescaledb/migration.sh'"
    exit 1
fi

if [[ -z $NEW_DB_PORT ]]; then
    echo "New db port is not set. Please configure NEW_DB_PORT in migration.config file and rerun './scripts/timescaledb/migration.sh'"
    exit 1
fi

if [[ -z $NEW_DB_USER ]]; then
    echo "New db primary user name is not set. Please configure NEW_DB_USER in migration.config file and rerun './scripts/timescaledb/migration.sh'"
    exit 1
fi

if [[ -z $NEW_PASSWORD ]]; then
    echo "New db primary user password is not set. Please configure NEW_PASSWORD in migration.config file and rerun './scripts/timescaledb/migration.sh'"
    exit 1
fi

if [[ -z $CHUNK_INTERVAL_TIME ]]; then
    echo "New db chunk interval time is not set. Please configure CHUNK_INTERVAL_TIME in migration.config file and rerun './scripts/timescaledb/migration.sh'"
    exit 1
fi

if [[ -z $CHUNK_INTERVAL_ID ]]; then
    echo "New db chunk interval id is not set. Please configure CHUNK_INTERVAL_ID in migration.config file and rerun './scripts/timescaledb/migration.sh'"
    exit 1
fi

start_time="$(date -u +%s)"
# assumes 1. Valid populated old mirror node postgres db with appropriate user 2. New empty TimescaleDB db host with appropriate user
echo "Migrating Mirror Node Data from Postgres($OLD_DB_HOST:$OLD_DB_PORT) to TimescaleDB($NEW_DB_HOST:$NEW_DB_PORT)..."

echo "1. Backing up flyway table schema from Postgres($OLD_DB_HOST:$OLD_DB_PORT)..."
PGPASSWORD=${OLD_PASSWORD} pg_dump -h $OLD_DB_HOST -p $OLD_DB_PORT -U $OLD_DB_USER --table public.flyway_schema_history -f mirror_node_${start_time}.bak mirror_node

echo "2. Restoring flyway_schema_history to TimescaleDB($NEW_DB_HOST:$NEW_DB_PORT)..."
PGPASSWORD=${NEW_PASSWORD} psql -h $NEW_DB_HOST -d $NEW_DB_NAME -p $NEW_DB_PORT -U $NEW_DB_USER <mirror_node_${start_time}.bak

echo "3. Create v2 table schemas in TimescaleDB($NEW_DB_HOST:$NEW_DB_PORT)..."
PGPASSWORD=${NEW_PASSWORD} psql -h $NEW_DB_HOST -d $NEW_DB_NAME -p $NEW_DB_PORT -U $NEW_DB_USER <migration/v2/V2.0.0__time_scale_init.sql

echo "4. Creating new hypertables on TimescaleDB($NEW_DB_HOST:$NEW_DB_PORT)..."
sed -e 's/${chunkTimeInterval}/'$CHUNK_INTERVAL_TIME'/g' -e 's/${chunkIdInterval}/'$CHUNK_INTERVAL_ID'/g' migration/v2/V2.0.1__hyper_tables.sql >scripts/timescaledb/createHyperTables.sql
PGPASSWORD=${NEW_PASSWORD} psql -h $NEW_DB_HOST -d $NEW_DB_NAME -p $NEW_DB_PORT -U $NEW_DB_USER -f scripts/timescaledb/createHyperTables.sql

echo "5. Backing up tables from from Postgres($OLD_DB_HOST:$OLD_DB_PORT) to separate CSV's..."
PGPASSWORD=${OLD_PASSWORD} psql -h $OLD_DB_HOST -d $OLD_DB_NAME -p $OLD_DB_PORT -U $OLD_DB_USER -f scripts/timescaledb/csvBackupTables.sql

## Optionally use https://github.com/timescale/timescaledb-parallel-copy as it's mulithreaded
echo "6. Restoring CSV backups to TimescaleDB($NEW_DB_HOST:$NEW_DB_PORT)..."
PGPASSWORD=${NEW_PASSWORD} psql -h $NEW_DB_HOST -d $NEW_DB_NAME -p $NEW_DB_PORT -U $NEW_DB_USER -f scripts/timescaledb/csvRestoreTables.sql

echo "7. Alter schema on TimescaleDB($NEW_DB_HOST:$NEW_DB_PORT) to support improved format..."
PGPASSWORD=${NEW_PASSWORD} psql -h $NEW_DB_HOST -d $NEW_DB_NAME -p $NEW_DB_PORT -U $NEW_DB_USER -f scripts/timescaledb/alterSchema.sql

# leave index creation and policy sets to migration 2.0
end_time="$(date -u +%s)"

elapsed="$(($end_time - $start_time))"
echo "Migration from postgres to timescale took a total of $elapsed seconds"
