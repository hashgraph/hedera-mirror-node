#!/usr/bin/env bash
set -eo pipefail

# Print log statements to stdout
log() {
    echo "$(date --iso-8601=seconds) ${1}"
}

# Query the source database and return the response
querySource() {
    local query="${1}"
    local result=$(PGPASSWORD="${SOURCE_DB_PASSWORD}" psql -q --csv -t -h "${SOURCE_DB_HOST}" -d "${SOURCE_DB_NAME}" -p "${SOURCE_DB_PORT}" -U "${SOURCE_DB_USER}" -c "${query}" | xargs)
    echo "${result}"
}

# Query the target database and return the response
queryTarget() {
    local query="${1}"
    local result=$(PGPASSWORD="${TARGET_DB_PASSWORD}" psql -q --csv -t -h "${TARGET_DB_HOST}" -d "${TARGET_DB_NAME}" -p "${TARGET_DB_PORT}" -U "${TARGET_DB_USER}" -c "${query}" | xargs)
    echo "${result}"
}

# Copy data from the source database to the target database if the target is empty.
copyTable() {
    SECONDS=0
    local table="${1}"
    local filename="${table}.csv.gz"
    local has_data=$(queryTarget "select exists (select * from ${table} limit 1)")

    if [[ "${has_data}" = "t" ]]; then
        log "Skipping '${table}' table since it contains existing data"
        return
    fi

    local query="
    select string_agg(column_name, ', ' order by ordinal_position)
    from information_schema.columns
    where table_schema = '${SOURCE_DB_SCHEMA}' and table_name = '${table}';"

    log "Starting to copy '${table}' table"
    local columns=$(querySource "${query}")
    columns="${columns#"${columns%%[![:space:]]*}"}" # Trim leading whitespace

    if [[ -n "${MAX_TIMESTAMP}" && "${columns}" =~ consensus_timestamp ]]; then
        where="where consensus_timestamp <= ${MAX_TIMESTAMP}"
    fi

    querySource "\copy (select ${columns} from ${table} $where) to program 'gzip -v6> ${filename}' delimiter ',' csv header;"
    queryTarget "\copy ${table} ($columns) from program 'gzip -dc ${filename}' DELIMITER ',' csv header;"
    rm -f "${filename}"
    log "Copied '${table}' table in ${SECONDS}s"
}

# Export the functions so they can be invoked via parallel xargs
export -f copyTable log querySource queryTarget

SECONDS=0
SCRIPTS_DIR="$(readlink -f "${0%/*}")"
EXPORT_DIR="${SCRIPTS_DIR}/export"
FLYWAY_DIR="${EXPORT_DIR}/flyway"
MIGRATIONS_DIR="${SCRIPTS_DIR}/../../migration/v2"
export PATH="${FLYWAY_DIR}/:$PATH"
source "${SCRIPTS_DIR}/migration.config"

export CONCURRENCY=${CONCURRENCY:-5}
export MAX_TIMESTAMP="${MAX_TIMESTAMP}"
export SOURCE_DB_HOST="${SOURCE_DB_HOST:-127.0.0.1}"
export SOURCE_DB_NAME="${SOURCE_DB_NAME:-mirror_node}"
export SOURCE_DB_PASSWORD="${SOURCE_DB_PASSWORD:-mirror_node_pass}"
export SOURCE_DB_PORT="${SOURCE_DB_PORT:-5432}"
export SOURCE_DB_SCHEMA="${SOURCE_DB_SCHEMA:-public}"
export SOURCE_DB_USER="${SOURCE_DB_USER:-mirror_node}"

export TARGET_DB_HOST="${TARGET_DB_HOST:-127.0.0.1}"
export TARGET_DB_NAME="${TARGET_DB_NAME:-mirror_node}"
export TARGET_DB_PASSWORD="${TARGET_DB_PASSWORD:-mirror_node_pass}"
export TARGET_DB_PORT="${TARGET_DB_PORT:-5432}"
export TARGET_DB_SCHEMA="${TARGET_DB_SCHEMA:-public}"
export TARGET_DB_USER="${TARGET_DB_USER:-mirror_node}"

SOURCE_QUERY="
select exists
(
  select from information_schema.tables
  where table_name = 'flyway_schema_history' and table_schema = '${SOURCE_DB_SCHEMA}'
);"

SOURCE_DB_HAS_DATA=$(querySource "${SOURCE_QUERY}")
if [[ "${SOURCE_DB_HAS_DATA}" != "t" ]]; then
  echo "Unable to verify the state of the source database. Either the connection information is wrong or the data is missing."
  exit 1
fi

TARGET_DB_HEALTHY=$(queryTarget "select exists (select from citus_check_cluster_node_health() where result = true);")
if [[ "${TARGET_DB_HEALTHY}" != "t" ]]; then
  echo "Unable to verify the state of the target database."
  exit 1
fi

rm -rf "${EXPORT_DIR}"
mkdir -p "${EXPORT_DIR}"
cd "${EXPORT_DIR}"

log "Installing Flyway"
wget -qO- ${FLYWAY_URL} | tar -xz && mv flyway-* flyway
log "Flyway installed"

log "Copying Flyway configuration"
cp "${MIGRATIONS_DIR}/V2.0."[0-2]* ${FLYWAY_DIR}/sql/
cat > "${FLYWAY_DIR}/conf/flyway.conf" <<EOF
flyway.password=${TARGET_DB_PASSWORD}
flyway.placeholders.idPartitionPostfix=p1970_01_01_000000
flyway.placeholders.partitionIdCount=1
flyway.placeholders.partitionIdInterval='.001 seconds'
flyway.placeholders.partitionStartDate='3 years'
flyway.placeholders.partitionTimeInterval='1 month'
flyway.placeholders.schema=${TARGET_DB_SCHEMA}
flyway.placeholders.shardCount=16
flyway.url=jdbc:postgresql://${TARGET_DB_HOST}:${TARGET_DB_PORT}/${TARGET_DB_NAME}
flyway.user=${TARGET_DB_USER}
EOF

log "Running Flyway migrate"
flyway migrate

TABLES_QUERY="
select relname from pg_catalog.pg_statio_user_tables
where schemaname = '${SOURCE_DB_SCHEMA}' and relname not similar to '%[0-9]' and relname not in (${EXCLUDED_TABLES})
order by pg_total_relation_size(relid) asc;"

TABLES=$(querySource "${TABLES_QUERY}" | tr " " "\n")
COUNT=$(echo "${TABLES}" | wc -l)
log "Migrating ${COUNT} tables from ${SOURCE_DB_HOST}:${SOURCE_DB_PORT} to ${TARGET_DB_HOST}:${TARGET_DB_PORT}"
echo "${TABLES}" | xargs -n 1 -P "${CONCURRENCY}" -I {} bash -c 'copyTable "$@"' _ {}

log "Migration completed in $SECONDS seconds"
exit 0
