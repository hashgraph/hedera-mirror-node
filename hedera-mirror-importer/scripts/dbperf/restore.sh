#!/bin/bash
set -e

#while ! pg_isready -d $DB_NAME -h localhost -p $DB_PORT -q -U $DB_USER; do
#    echo >&2 "Postgres $DB_NAME database at localhost:$DB_PORT is unavailable to user $DB_USER - sleeping"
#    sleep 1
#done

echo "Importing Mirror Node Data"
PGPASSWORD=$DB_PASS pg_restore -U $DB_USER -d $DB_NAME -p $DB_PORT -h localhost /tmp/pgdump.gz
echo "Restored Mirror Node Data from backup"
