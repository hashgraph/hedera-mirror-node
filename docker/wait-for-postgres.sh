#!/bin/bash
# wait-for-postgres.sh

set -e

host="$1"
# shift
# cmd="$@"

echo "Starting postgres"
echo "PGDATA=$PGDATA"

su - postgres -c "PGDATA=$PGDATA /usr/local/bin/pg_ctl -w start"

echo "Waiting for postgres"
until PGPASSWORD=$POSTGRES_PASSWORD psql -h "$host" -U "$POSTGRES_USER" -c '\l'; do
  >&2 echo "Postgres is unavailable - sleeping"
  sleep 1
done

>&2 echo "Postgres is up - executing command"

tail -f wait-for-postgres.sh
