#!/usr/bin/env bash
set -eo pipefail

SCRIPTS_DIR="$(readlink -f "${0%/*}")"
EXPORT_DIR="${SCRIPTS_DIR}/export"
MIGRATIONS_DIR="${SCRIPTS_DIR}/../../migration/v2"
source "${SCRIPTS_DIR}/migration.config"

if test -f /usr/local/bin/flyway; then
    sudo rm /usr/local/bin/flyway
fi

echo "Installing flyway"
wget -qO- https://repo1.maven.org/maven2/org/flywaydb/flyway-commandline/9.8.1/${FLYWAY_INSTALLATION} | tar xvz && sudo ln -s `pwd`/flyway-9.8.1/flyway /usr/local/bin
echo "flyway installed"
echo "Copying the config for flyway"
cp "${SCRIPTS_DIR}/flyway.conf" "${SCRIPTS_DIR}/flyway-9.8.1/conf/"
echo "Copying the script file for flyway migration"
cp "${SCRIPTS_DIR}/../../migration/v2/V2.0.0__create_tables.sql" "${SCRIPTS_DIR}/flyway-9.8.1/sql/"
cp "${SCRIPTS_DIR}/../../migration/v2/V2.0.1__distribution.sql" "${SCRIPTS_DIR}/flyway-9.8.1/sql/"
cp "${SCRIPTS_DIR}/../../migration/v2/V2.0.2__static_partitioning.sql" "${SCRIPTS_DIR}/flyway-9.8.1/sql/"


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

export PGPASSWORD="${OLD_DB_PASSWORD}"
rm -f "${SCRIPTS_DIR}/restore.sql" "${SCRIPTS_DIR}/backup.sql"
rm -rf "${EXPORT_DIR}"
mkdir -p "${EXPORT_DIR}"
cd "${EXPORT_DIR}"
SECONDS=0

echo "Migrating mirror node data from PostgreSQL ${OLD_DB_HOST}:${OLD_DB_PORT} to CitusDB ${NEW_DB_HOST}:${NEW_DB_PORT}"

echo "1. Generating backup and restore SQL"
TABLES=$(psql -q -t --csv -h "${OLD_DB_HOST}" -p "${OLD_DB_PORT}" -U "${OLD_DB_USER}" -c "select distinct table_name from information_schema.tables where table_schema = 'public' order by table_name asc;")

for table in ${TABLES}; do
  COLUMNS=$(psql -q -t -h "${OLD_DB_HOST}" -p "${OLD_DB_PORT}" -U "${OLD_DB_USER}" -c "select string_agg(column_name, ', ' order by ordinal_position) from information_schema.columns where table_schema = 'public' and table_name = '${table}';")
  COLUMNS="${COLUMNS#"${COLUMNS%%[![:space:]]*}"}" # Trim leading whitespace
  echo "\copy ${table} ($COLUMNS) from program 'gzip -dc ${table}.csv.gz' DELIMITER ',' CSV HEADER null '';" >> "${SCRIPTS_DIR}/restore.sql"
  echo "\copy ${table} to program 'gzip -v6> ${table}.csv.gz' delimiter ',' csv header;" >> "${SCRIPTS_DIR}/backup.sql"
done

echo "2. Backing up tables from source database"
psql -h "${OLD_DB_HOST}" -d "${OLD_DB_NAME}" -p "${OLD_DB_PORT}" -U "${OLD_DB_USER}" -f "${SCRIPTS_DIR}/backup.sql"

echo "3. Running flyway migrate"
flyway migrate

echo "4. Restoring database dump to target database"
psql -h "${NEW_DB_HOST}" -d "${NEW_DB_NAME}" -p "${NEW_DB_PORT}" -U "${NEW_DB_USER}" -f "${SCRIPTS_DIR}/restore.sql"

echo "Migration completed in $SECONDS seconds"
exit 0
