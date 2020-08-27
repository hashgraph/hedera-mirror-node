#!/usr/bin/env bash
echo "BASH_VERSION: $BASH_VERSION"
set -e

# get and assign variables $OLD_DB_HOST, $OLD_DB_NAME, $OLD_DB_PORT, $OLD_DB_USER, $NEW_DB_HOST, $NEW_DB_NAME, $NEW_DB_PORT, $NEW_DB_USER
while getopts “:h:d:p:u:w:x:y:z:-:” OPTION; do
    case $OPTION in
    h)
        OLD_DB_HOST=$OPTARG
        echo "Setting OLD_DB_HOST to ${OLD_DB_HOST}"
        ;;
    d)
        OLD_DB_NAME=$OPTARG
        echo "Setting OLD_DB_NAME to ${OLD_DB_NAME}"
        ;;
    p)
        OLD_DB_PORT=$OPTARG
        echo "Setting OLD_DB_PORT to ${OLD_DB_PORT}"
        ;;
    u)
        OLD_DB_USER=$OPTARG
        echo "Setting OLD_DB_USER to ${OLD_DB_USER}"
        ;;
    w)
        NEW_DB_HOST=$OPTARG
        echo "Setting NEW_DB_HOST to ${NEW_DB_HOST}"
        ;;
    x)
        NEW_DB_NAME=$OPTARG
        echo "Setting NEW_DB_NAME to ${NEW_DB_NAME}"
        ;;
    y)
        NEW_DB_PORT=$OPTARG
        echo "Setting NEW_DB_PORT to ${NEW_DB_PORT}"
        ;;
    z)
        NEW_DB_USER=$OPTARG
        echo "Setting NEW_DB_USER to ${NEW_DB_USER}"
        ;;
    ?)
        echo "Provided ${OPTION}"
        echo "Please call script as follows './timeScaleDbMigration.sh -oh <old_host> -od <old_db> -op <old_port> -ou <old_user> -nh <new_host> -nd <new_db> -np <new_port> -nu <new_user>'"
        exit
        ;;
    esac
done

if [ "$#" -lt 8 ]; then
    echo "A Db connection item is missing. Please call script as follows './devDeployPrep.sh -s <suffix> -i <ip>'"
    exit 1
fi

if [[ -z $OLD_DB_HOST ]]; then
    echo "Current host name is missing. Please add -oh <old_host> to your arguments'"
    exit 1
fi

start_time="$(date -u +%s)"
# assumes 1. Valid populated current mirror node postgres db with appropriate user 2. New empty TimeScaleDb db host with appropriate user
echo "Migrating Mirror Node Data from Postgres($OLD_DB_HOST:$OLD_DB_PORT) to TimeScaleDb($NEW_DB_HOST:$NEW_DB_PORT)"

echo "1. Migrate schema to TimeScaleDb. Postgres($OLD_DB_HOST:$OLD_DB_PORT) will prompt for password..."
pg_dump -h $OLD_DB_HOST -p $OLD_DB_PORT -U $OLD_DB_USER --section=pre-data -f mirror_node_${start_time}.bak mirror_node
#
echo "2. Restore table schemas to new host. TimeScaleDb($NEW_DB_HOST:$NEW_DB_PORT) will prompt for password..."
psql -h $NEW_DB_HOST -d $NEW_DB_NAME -p $NEW_DB_PORT -U $NEW_DB_USER <mirror_node_${start_time}.bak

## Optionally we could skip step 1 and 2 and just reate a whole new schema with a new init.sql -> timeScaleDBInit.sql
echo "3. Create new hyper tables"
psql -h $NEW_DB_HOST -d $NEW_DB_NAME -p $NEW_DB_PORT -U $NEW_DB_USER -f createHyperTables.sql

echo "4. Copy tables into separate CSV's"
psql -h $OLD_DB_HOST -d $OLD_DB_NAME -p $OLD_DB_PORT -U $OLD_DB_USER -f csvBackupTables.sql

## Optionally use https://github.com/timescale/timescaledb-parallel-copy as it's mulithreaded
echo "5. Pg_restore to separate host"
psql -h $NEW_DB_HOST -d $NEW_DB_NAME -p $NEW_DB_PORT -U $NEW_DB_USER -f csvRestoreTables.sql

## rename entities table
#psql -h $NEW_DB_HOST -d $NEW_DB_NAME -p $NEW_DB_PORT -U $NEW_DB_USER -c "alter table t_entities rename to entity"

# leave index creation to migration 2.0
end_time="$(date -u +%s)"

elapsed="$(($end_time - $start_time))"
echo "Migration from postgres to timescale took a total of $elapsed seconds"
