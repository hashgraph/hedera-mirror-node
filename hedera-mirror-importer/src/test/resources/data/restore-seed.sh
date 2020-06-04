#!/bin/sh
set -e

while ! pg_isready -d mirror_node -p 5432 -q -U mirror_node; do
    echo >&2 "Postgres $DB_NAME database at localhost:$DB_PORT is unavailable to user $DB_USER - sleeping"
    sleep 1
done

echo "Setting user and role for Mirror Node $DB_NAME database at localhost:$DB_PORT under user $DB_USER"
PGPASSWORD=password psql -v ON_ERROR_STOP=1 --username postgres <<-EOSQL
    \set db_name '$DB_NAME'
    \set db_owner 'mirror_node'
    \set db_user '$DB_USER'
    \set db_password '$$DB_PASS'

    create user :db_user with login createrole password :'db_password';
    create database :db_name with owner :db_user;
    GRANT :db_owner TO :db_user;
EOSQL

echo "Importing Mirror Node Data"
echo "Connecting to Postgres $DB_NAME database at localhost:$DB_PORT under user $DB_USER"
PGPASSWORD=$DB_PASS pg_restore -U $DB_USER -d $DB_NAME -p $DB_PORT /tmp/pgdump.gz
echo "Restored Mirror Node Data from backup"
