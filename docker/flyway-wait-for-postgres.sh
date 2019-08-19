#!/bin/bash
# wait-for-postgres.sh

set -e

>&2 echo "Waiting for postgres"

until PGPASSWORD=$POSTGRES_PASSWORD psql --host "mirror-node-postgres" --dbname "$POSTGRES_DB" --username "$POSTGRES_USER" -c '\l'; do
  >&2 echo "Postgres is unavailable - sleeping 5s"
  sleep 5
done

>&2 echo "Postgres is up - executing database migration"

rm -f /flyway/sql/*
cp /MirrorNodeCode/sql/* /flyway/sql

sed -i "s/:api_user/$DB_USER/" /flyway/sql/V*.sql
sed -i "s/:api_password/$DB_PASS/" /flyway/sql/V*.sql
sed -i "s/:db_user/$POSTGRES_USER/" /flyway/sql/V*.sql
sed -i "s/:db_name/$POSTGRES_DB/" /flyway/sql/V*.sql

flyway -url=jdbc:postgresql://mirror-node-postgres:5432/postgres -connectRetries=10 -user=$POSTGRES_USER -password=$POSTGRES_PASSWORD -schemas=$POSTGRES_DB migrate
