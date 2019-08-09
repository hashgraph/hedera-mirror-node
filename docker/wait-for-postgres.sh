#!/bin/bash
# wait-for-postgres.sh

set -e

>&2 echo "Waiting for postgres"

until PGPASSWORD=$POSTGRES_PASSWORD psql --host "$DB_HOST" --dbname "$POSTGRES_DB" --username "$POSTGRES_USER" -c '\l'; do
  >&2 echo "Postgres is unavailable - sleeping 5s"
  sleep 5
done

>&2 echo "Postgres is up - executing command"
