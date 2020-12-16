#!/usr/bin/env bash
echo "BASH_VERSION: $BASH_VERSION"
set -e

. migration.config
echo "$OLD_DB_HOST" "$OLD_DB_NAME" "$OLD_DB_PORT" "$OLD_DB_USER" "$NEW_DB_HOST" "$NEW_DB_NAME" "$NEW_DB_PORT" "$NEW_DB_USER"

if [[ -z $OLD_DB_HOST ]]; then
    echo "Current host name is not set. Please configure OLD_DB_HOST in migration.config file and rerun './timeScaleDbMigration.sh'"
    exit 1
fi

if [[ -z $OLD_DB_NAME ]]; then
    echo "Current db name is not set. Please configure OLD_DB_NAME in migration.config file and rerun './timeScaleDbMigration.sh'"
    exit 1
fi

if [[ -z $OLD_DB_PORT ]]; then
    echo "Current port is not set. Please configure OLD_DB_PORT in migration.config file and rerun './timeScaleDbMigration.sh'"
    exit 1
fi

if [[ -z $OLD_DB_USER ]]; then
    echo "Current db primary user name is not set. Please configure OLD_DB_USER in migration.config file and rerun './timeScaleDbMigration.sh'"
    exit 1
fi

if [[ -z $OLD_PASSWORD ]]; then
    echo "Old db primary user password is not set. Please configure OLD_PASSWORD in migration.config file and rerun './timeScaleDbMigration.sh'"
    exit 1
fi

if [[ -z $NEW_DB_HOST ]]; then
    echo "New host name is not set. Please configure NEW_DB_HOST in migration.config file and rerun './timeScaleDbMigration.sh'"
    exit 1
fi

if [[ -z $NEW_DB_NAME ]]; then
    echo "New db name is not set. Please configure NEW_DB_NAME in migration.config file and rerun './timeScaleDbMigration.sh'"
    exit 1
fi

if [[ -z $NEW_DB_PORT ]]; then
    echo "New db port is not set. Please configure NEW_DB_PORT in migration.config file and rerun './timeScaleDbMigration.sh'"
    exit 1
fi

if [[ -z $NEW_DB_USER ]]; then
    echo "New db primary user name is not set. Please configure NEW_DB_USER in migration.config file and rerun './timeScaleDbMigration.sh'"
    exit 1
fi

if [[ -z $NEW_PASSWORD ]]; then
    echo "New db primary user password is not set. Please configure NEW_PASSWORD in migration.config file and rerun './timeScaleDbMigration.sh'"
    exit 1
fi

start_time="$(date -u +%s)"
# assumes 1. Valid populated current mirror node postgres db with appropriate user 2. New empty TimeScaleDb db host with appropriate user
echo "Migrating Mirror Node Data from Postgres($OLD_DB_HOST:$OLD_DB_PORT) to TimeScaleDb($NEW_DB_HOST:$NEW_DB_PORT)..."

echo "1. Backing up table schema from Postgres($OLD_DB_HOST:$OLD_DB_PORT)..."
PGPASSWORD=${OLD_PASSWORD} pg_dump -h $OLD_DB_HOST -p $OLD_DB_PORT -U $OLD_DB_USER --section=pre-data -f mirror_node_${start_time}.bak mirror_node
#
echo "2. Restoring table schemas to TimeScaleDb($NEW_DB_HOST:$NEW_DB_PORT)..."
psql -h $NEW_DB_HOST -d $NEW_DB_NAME -p $NEW_DB_PORT -U $NEW_DB_USER "password=${NEW_PASSWORD}" <mirror_node_${start_time}.bak

## Optionally we could skip step 1 and 2 and just create a whole new schema with a new init.sql -> timeScaleDBInit.sql
echo "3. Creating new hyper tables on TimeScaleDb($NEW_DB_HOST:$NEW_DB_PORT)..."
psql -h $NEW_DB_HOST -d $NEW_DB_NAME -p $NEW_DB_PORT -U $NEW_DB_USER -f createHyperTables.sql "password=${NEW_PASSWORD}"

echo "4. Backing up tables from from Postgres($OLD_DB_HOST:$OLD_DB_PORT) to separate CSV's..."
psql -h $OLD_DB_HOST -d $OLD_DB_NAME -p $OLD_DB_PORT -U $OLD_DB_USER -f csvBackupTables.sql "password=${OLD_PASSWORD}"

## Optionally use https://github.com/timescale/timescaledb-parallel-copy as it's mulithreaded
echo "5. Restoring CSV backups to TimeScaleDb($NEW_DB_HOST:$NEW_DB_PORT)..."
psql -h $NEW_DB_HOST -d $NEW_DB_NAME -p $NEW_DB_PORT -U $NEW_DB_USER -f csvRestoreTables.sql "password=${NEW_PASSWORD}"

# leave index creation and policy sets to migration 2.0
end_time="$(date -u +%s)"

elapsed="$(($end_time - $start_time))"
echo "Migration from postgres to timescale took a total of $elapsed seconds"
