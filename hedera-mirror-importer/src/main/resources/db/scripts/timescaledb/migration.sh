#!/usr/bin/env bash
set -eo pipefail

SCRIPTS_DIR="$(realpath "${0%/*}")"
EXPORT_DIR="${SCRIPTS_DIR}/export"
MIGRATIONS_DIR="${SCRIPTS_DIR}/../../migration/v2"
source "${SCRIPTS_DIR}/migration.config"

if [[ -z "${OLD_DB_HOST}" ]]; then
    echo "Old host name is not set. Please configure OLD_DB_HOST in migration.config file and rerun the migration"
    exit 1
fi

if [[ -z "${OLD_DB_NAME}" ]]; then
    echo "Old db name is not set. Please configure OLD_DB_NAME in migration.config file and rerun the migration"
    exit 1
fi

if [[ -z "${OLD_DB_PORT}" ]]; then
    echo "Old port is not set. Please configure OLD_DB_PORT in migration.config file and rerun the migration"
    exit 1
fi

if [[ -z "${OLD_DB_USER}" ]]; then
    echo "Old db primary user name is not set. Please configure OLD_DB_USER in migration.config file and rerun the migration"
    exit 1
fi

if [[ -z "${OLD_DB_PASSWORD}" ]]; then
    echo "Old db primary user password is not set. Please configure OLD_DB_PASSWORD in migration.config file and rerun the migration"
    exit 1
fi

if [[ -z "${NEW_DB_HOST}" ]]; then
    echo "New host name is not set. Please configure NEW_DB_HOST in migration.config file and rerun the migration"
    exit 1
fi

if [[ -z "${NEW_DB_NAME}" ]]; then
    echo "New db name is not set. Please configure NEW_DB_NAME in migration.config file and rerun the migration"
    exit 1
fi

if [[ -z "${NEW_DB_PORT}" ]]; then
    echo "New db port is not set. Please configure NEW_DB_PORT in migration.config file and rerun the migration"
    exit 1
fi

if [[ -z $"${NEW_DB_USER}" ]]; then
    echo "New db primary user name is not set. Please configure NEW_DB_USER in migration.config file and rerun the migration"
    exit 1
fi

if [[ -z "${NEW_DB_PASSWORD}" ]]; then
    echo "New db primary user password is not set. Please configure NEW_DB_PASSWORD in migration.config file and rerun the migration"
    exit 1
fi

if [[ -z "${CHUNK_INTERVAL_TIME}" ]]; then
    echo "New db chunk interval time is not set. Please configure CHUNK_INTERVAL_TIME in migration.config file and rerun the migration"
    exit 1
fi

if [[ -z "${CHUNK_INTERVAL_ID}" ]]; then
    echo "New db chunk interval id is not set. Please configure CHUNK_INTERVAL_ID in migration.config file and rerun the migration"
    exit 1
fi

OLD_DB_HAS_DATA=$(PGPASSWORD="${OLD_DB_PASSWORD}" psql -X -t -h "${OLD_DB_HOST}" -d "${OLD_DB_NAME}" -p "${OLD_DB_PORT}" -U "${OLD_DB_USER}" -c "select exists (select from information_schema.tables where table_name = 'flyway_schema_history');" | xargs)
if [[ "${OLD_DB_HAS_DATA}" != "t" ]]; then
  echo "Unable to verify the state of the old database. Either the connection information is wrong or the mirror node data is missing"
  exit 1
fi

NEW_DB_HAS_DATA=$(PGPASSWORD="${NEW_DB_PASSWORD}" psql -X -t -h "${NEW_DB_HOST}" -d "${NEW_DB_NAME}" -p "${NEW_DB_PORT}" -U "${NEW_DB_USER}" -c "select exists (select from information_schema.tables where table_name = 'flyway_schema_history');" | xargs)
if [[ "${NEW_DB_HAS_DATA}" != "f" ]]; then
  echo "Unable to verify the state of the new database. Either the connection information is wrong, the migration might have already been ran, or the importer might've already initialized it."
  exit 1
fi

start_time="$(date -u +%s)"

rm -rf "${EXPORT_DIR}"
mkdir -p "${EXPORT_DIR}"
cd "${EXPORT_DIR}"

echo "Migrating Mirror Node Data from PostgreSQL ${OLD_DB_HOST}:${OLD_DB_PORT} to TimescaleDB ${NEW_DB_HOST}:${NEW_DB_PORT}..."

echo "1. Backing up flyway table schema from PostgreSQL ${OLD_DB_HOST}:${OLD_DB_PORT}..."
PGPASSWORD="${OLD_DB_PASSWORD}" pg_dump -h "${OLD_DB_HOST}" -p "${OLD_DB_PORT}" -U "${OLD_DB_USER}" --table public.flyway_schema_history -f flyway_schema_history.sql mirror_node

echo "2. Restoring flyway_schema_history to TimescaleDB ${NEW_DB_HOST}:${NEW_DB_PORT}..."
PGPASSWORD="${NEW_DB_PASSWORD}" psql -h "${NEW_DB_HOST}" -d "${NEW_DB_NAME}" -p "${NEW_DB_PORT}" -U "${NEW_DB_USER}" <flyway_schema_history.sql

echo "3. Create v2 table schemas in TimescaleDB ${NEW_DB_HOST}:${NEW_DB_PORT}..."
PGPASSWORD="${NEW_DB_PASSWORD}" psql -h "${NEW_DB_HOST}" -d "${NEW_DB_NAME}" -p "${NEW_DB_PORT}" -U "${NEW_DB_USER}" <"${MIGRATIONS_DIR}/V2.0.0__time_scale_init.sql"

echo "4. Creating new hypertables on TimescaleDB ${NEW_DB_HOST}:${NEW_DB_PORT}..."
sed -e 's/${chunkTimeInterval}/'"${CHUNK_INTERVAL_TIME}"'/g' -e 's/${chunkIdInterval}/'"${CHUNK_INTERVAL_ID}"'/g' "${MIGRATIONS_DIR}/V2.0.1__hyper_tables.sql" >"${SCRIPTS_DIR}/createHyperTables.sql"
PGPASSWORD=${NEW_DB_PASSWORD} psql -h "${NEW_DB_HOST}" -d "${NEW_DB_NAME}" -p "${NEW_DB_PORT}" -U "${NEW_DB_USER}" -f "${SCRIPTS_DIR}/createHyperTables.sql"

echo "5. Backing up tables from from PostgreSQL ${OLD_DB_HOST}:${OLD_DB_PORT}"
PGPASSWORD="${OLD_DB_PASSWORD}" psql -h "${OLD_DB_HOST}" -d "${OLD_DB_NAME}" -p "${OLD_DB_PORT}" -U "${OLD_DB_USER}" -f "${SCRIPTS_DIR}/csvBackupTables.sql"

## Optionally use https://github.com/timescale/timescaledb-parallel-copy as it's mulithreaded
echo "6. Restoring database dump to TimescaleDB ${NEW_DB_HOST}:${NEW_DB_PORT}..."
PGPASSWORD="${NEW_DB_PASSWORD}" psql -h "${NEW_DB_HOST}" -d "${NEW_DB_NAME}" -p "${NEW_DB_PORT}" -U "${NEW_DB_USER}" -f "${SCRIPTS_DIR}/csvRestoreTables.sql"

echo "7. Alter schema on TimescaleDB ${NEW_DB_HOST}:${NEW_DB_PORT} to support improved format..."
PGPASSWORD="${NEW_DB_PASSWORD}" psql -h "${NEW_DB_HOST}" -d "${NEW_DB_NAME}" -p "${NEW_DB_PORT}" -U "${NEW_DB_USER}" -f "${SCRIPTS_DIR}/alterSchema.sql"

# leave index creation and policy sets to migration 2.0
end_time="$(date -u +%s)"

elapsed="$((end_time - start_time))"
echo "Migration from PostgreSQL to TimescaleDB took a total of $elapsed seconds"
exit 0
