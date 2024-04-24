#!/usr/bin/env bash
set -emo pipefail

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
  local result=$(PGPASSWORD="${TARGET_DB_PASSWORD}" psql -q --csv -t -h "${TARGET_DB_HOST}" -d "${TARGET_DB_NAME}" -p "${TARGET_DB_PORT}" -U "${TARGET_DB_USER}" --single-transaction -c "${query}" | xargs)
  echo "${result}"
}

die() {
  echo "$@"
  exit 1
}

handlePipeError() {
  log "ERROR: pid: $! on $$ SIGPIPE received on pipe $!{EPIPE} msg: ${1}"
  exit 1
}

handleExit() {
  local error_code=$?
  local error_line="${BASH_LINENO[*]}"
  local error_command=$BASH_COMMAND
  log "ERROR: pid: $! on $$ Received non zero exit code: (${error_code}) ${1} at ${error_line}: ${error_command}"
  exit 1
}

targetTableExists() {
  local table="${1}"
  local query="select exists (select from information_schema.tables where table_schema = '${TARGET_DB_SCHEMA}' and table_name = '${table}');"
  local exists=$(queryTarget "${query}")
  echo "${exists}"
}

getColumns() {
  local table="${1}"
  local query="select string_agg(concat('', column_name), ', ' order by ordinal_position) from information_schema.columns where table_schema = '${SOURCE_DB_SCHEMA}' and table_name = '${table}';"
  local columns
  columns=$(querySource "${query}")
  columns="${columns#"${columns%%[![:space:]]*}"}" # Trim leading whitespace
  echo "${columns}"
}

insertCursors() {
  local hasTable="f"
  local sqlTemp="/tmp/cursors-$$.csv"
  local tableDDL="
    create table if not exists async_migration_status (
    the_table                varchar(100),
    cursor_id                int,
    cursor_range             int8range,
    processed_timestamp timestamptz);"

  hasTable=$(targetTableExists "async_migration_status")
  if [[ "${hasTable}" = "t" ]]; then
    log "Skipping async_migration_status table creation since it already exists"
    return
  else
    log "Creating async_migration_status table"
    queryTarget "${tableDDL}"
  fi

  local conditions
  getCursorConditions conditions

  cursorId=1
  local insertSql="\\copy async_migration_status(the_table, cursor_id, cursor_range) from program 'cat ${sqlTemp}' with csv delimiter ',';"
  local values=()
  for value in "${conditions[@]}"; do
    for table in $(echo "${ASYNC_TABLES[@]}" | tr "," "\n"); do
      values+=("${table},${cursorId},${value}")
    done
    cursorId=$((cursorId + 1))
  done
  IFS=$'\n'; echo "${values[*]}" > "${sqlTemp}"
  queryTarget "${insertSql}"
}

getCursorConditions() {
  local query="
  with record_file_stats as (
    select date_trunc('month', to_timestamp(consensus_end / 1000000000)) start_timestamp,
           sum(count) as record_count from record_file
    where consensus_end <= ${MAX_TIMESTAMP}
    group by 1 order by 1 desc
  ),
  total_records as (
    select sum(record_count) as total_record_count
    from record_file_stats
  )
  select
  100 * ((1.0 * rfs.record_count) / tr.total_record_count) as pct_of_total,
  (extract(epoch from rfs.start_timestamp) * 1000000000)::bigint as lower_bound,
  LEAST(${MAX_TIMESTAMP}, (extract(epoch from (rfs.start_timestamp + INTERVAL '1 month')) * 1000000000)::bigint) as upper_bound
  from record_file_stats rfs, total_records tr
  order by 3 desc
  "

  local upperBound=$MAX_TIMESTAMP
  local pctPerCursor=$(echo "scale=10; 100 / ${ASYNC_TABLE_SPLITS}" | bc)
  local totalPct=0
  local -n cursors=$1 # Init array to hold cursor definitions
  local stats=$(PGPASSWORD="${SOURCE_DB_PASSWORD}" psql -q --csv -t -h "${SOURCE_DB_HOST}" -d "${SOURCE_DB_NAME}" -p "${SOURCE_DB_PORT}" -U "${SOURCE_DB_USER}" -c "${query}")

  while IFS="," read -r pct_of_total lower_bound upper_bound; do
    upperBound=$((upper_bound > upperBound ? upper_bound : upperBound))
    local groupRange=$((upperBound - lower_bound))
    local rangeToTake=$(echo "scale=10; (${pctPerCursor} / 100) * ${groupRange}" | bc)
    totalPct=$(echo "(${totalPct} + ${pct_of_total})" | bc)
    while (($(echo "${totalPct} >= ${pctPerCursor}" | bc) )); do
      totalPct=$(echo "${totalPct} - ${pctPerCursor}" | bc)
      cursorLowerBound=$(echo "scale=0; (${upperBound} - ${rangeToTake}) / 1" | bc)
      cursors+=("\"(${cursorLowerBound},${upperBound}]\"")
      upperBound=$cursorLowerBound
    done
  done <<< "${stats}"

  if (( $(echo "${totalPct} > 0" | bc) )); then
    cursors+=("\"(,${upperBound}]\"")
  fi
}

migrateTableBinary() {
  local table="${1}"
  local columns="${2}"

  local has_data=$(queryTarget "select exists (select * from ${table}  limit 1)")
  if [[ "${has_data}" = "t" ]]; then
    log "Skipping '${table}' table since it contains existing data"
    return
  fi

  local where
  if [[ "${table}" != "topic_message" && "${columns}" =~ $TS_COLUMN_REGEX ]]; then
    where="where consensus_timestamp \<= ${MAX_TIMESTAMP}"
  # handle address book.
  elif [[ "${columns}" =~ \<consensus_timestamp_start\> ]]; then
    where="where consensus_timestamp_end \<= ${MAX_TIMESTAMP}"
  fi

  local sql="\\copy ${table}(${columns}) FROM PROGRAM 'echo copy \(select ${columns} from ${table} ${where}\) to STDOUT with BINARY|./querySource.sh' WITH BINARY;"

  log "Starting to copy '${table}' table with sql: ${sql}"

  queryTarget "${sql}"

  log "Copied '${table}' table in ${SECONDS}s"
}

migrateTableAsyncBinary() {
  local table="${1}"
  local columns="${2}"
  local tmpDir="${3}"

  if ! [[ "${columns}" =~ $TS_COLUMN_REGEX ]]; then
    log "ERROR: Table ${table} does not have a timestamp column and can not be migrated asynchronously."
    exit 1
  fi

  local cursorQuery="SELECT cursor_id,
                            lower(cursor_range),
                            upper(cursor_range),
                            lower_inc(cursor_range),
                            upper_inc(cursor_range)
                     FROM async_migration_status
                     WHERE the_table = '${table}' and
                           processed_timestamp IS NULL
                     ORDER BY upper(cursor_range) desc;"

  local tableCursors=$(PGPASSWORD="${TARGET_DB_PASSWORD}" psql -q --csv -t -h "${TARGET_DB_HOST}" -d "${TARGET_DB_NAME}" -p "${TARGET_DB_PORT}" -U "${TARGET_DB_USER}" -c "${cursorQuery}")

  while IFS="," read -r cursor_lower_bound cursor_upper_bound lower_inc upper_inc; do
    ACTIVE_COPIES=$(find . -maxdepth 1 -name "process-${table}*" -printf '.' | wc -m)
    while [[ "${ACTIVE_COPIES}" -ge ${CONCURRENT_COPIES_PER_TABLE} ]]; do
      sleep .5
      ACTIVE_COPIES=$(find . -maxdepth 1 -name "process-${table}*" -printf '.' | wc -m)
    done
    local lowerOperator
    local upperOperator

    if [[ "${lower_inc}" == "t" ]]; then
      lowerOperator=">="
    else
      lowerOperator=">"
    fi

    if [[ "${upper_inc}" == "t" ]]; then
      upperOperator="<="
    else
      upperOperator="<"
    fi
    local where
    if [[ -z "${cursor_lower_bound}" ]]; then
      where="where consensus_timestamp \<= ${cursor_upper_bound}"
    else
      where="where consensus_timestamp \\${upperOperator} ${cursor_upper_bound} and consensus_timestamp \\${lowerOperator} ${cursor_lower_bound}"
    fi

    local processMarker="${tmpDir}/process-${table}-$(uuidgen)"
    touch "${processMarker}"

    local copyDef="\\copy ${table}(${columns}) from program 'echo copy \(select ${columns} from ${table} ${where}\) to stdout with binary|./querySource.sh' with binary;"
    local bookmarkSql="update async_migration_status set processed_timestamp=now() where the_table='${table}' and cursor_id=${cursor_id};"
    queryTarget "${copyDef} ${bookmarkSql}" && rm "${processMarker}" &

    log "Starting to copy table ${table} with sql: ${copyDef} pid $!"
  done <<<"${tableCursors}"

  wait
  log "Copied '${table}' table in ${SECONDS}s"
  jobs
  popd || die "Couldn't change directory back to the original directory"
  rm -r "${tmpDir}"
}

migrateTable() {
  local table="${1}"
  local tmpDir="/tmp/migrate-${table}-$$"
  mkdir -m 700 "${tmpDir}" || die "Couldn't make safe tmp ${tmpDir}"
  pushd "${tmpDir}" || die "Couldn't change directory to tmp ${tmpDir}"

  trap "handlePipeError 'for table ${table} (BINARY)'" SIGPIPE
  trap "handleExit 'for table ${table} (BINARY)'" ERR

  local tableExists="f"
  tableExists=$(targetTableExists "${table}")
  if [[ "${tableExists}" = "f" ]]; then
    log "Skipping '${table}' table since it doesn't exist on the target"
    return
  fi

  cat >querySource.sh <<EOF
#!/usr/bin/env bash
PGPASSWORD="${SOURCE_DB_PASSWORD}" psql -q -t -h "${SOURCE_DB_HOST}" -d "${SOURCE_DB_NAME}" -p "${SOURCE_DB_PORT}" -U "${SOURCE_DB_USER}"
EOF
  chmod +x querySource.sh

  local columns
  columns=$(getColumns "${table}")

  if [[ "${ASYNC_TABLES}" =~ $table ]]; then
    migrateTableAsyncBinary "${table}" "${columns}" "${tmpDir}" &
  else
    migrateTableBinary "${table}" "${columns}" "${tmpDir}"
  fi
}

# Export the functions so they can be invoked via parallel xargs
export -f log querySource queryTarget migrateTable migrateTableBinary migrateTableAsyncBinary handlePipeError handleExit targetTableExists getColumns

SECONDS=0
SCRIPTS_DIR="$(pwd)"
FLYWAY_DIR="/tmp/flyway"
MIGRATIONS_DIR="${SCRIPTS_DIR}/../../migration/v2"
export PATH="${FLYWAY_DIR}/:$PATH"
source "${SCRIPTS_DIR}/migration.config"

ASYNC_TABLE_SPLITS=${ASYNC_TABLE_SPLITS:-1000}
export CONCURRENCY=${CONCURRENCY:-5}
export CONCURRENT_COPIES_PER_TABLE=${CONCURRENT_COPIES_PER_TABLE:-5}
export MAX_TIMESTAMP="${MAX_TIMESTAMP:-$(querySource "select max(consensus_end) from record_file;")}"
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
export TS_COLUMN_REGEX="${TS_COLUMN_REGEX:-\<consensus_timestamp\>}"

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

rm -rf "${FLYWAY_DIR}"
mkdir -p "${FLYWAY_DIR}" || die "Couldn't create directory ${FLYWAY_DIR}"
pushd "${FLYWAY_DIR}" || die "Couldn't change directory to ${FLYWAY_DIR}"

log "Installing Flyway"
wget -qO- "${FLYWAY_URL}" | tar -xz && mv flyway-*/* .
log "Flyway installed"

log "Copying Flyway configuration"

CREATE_INDEXES_BEFORE_MIGRATION=${CREATE_INDEXES_BEFORE_MIGRATION:-true}
if [[ "${CREATE_INDEXES_BEFORE_MIGRATION}" = "true" ]]; then
  cp "${MIGRATIONS_DIR}/V2.0."* "${FLYWAY_DIR}"/sql/
else
  cp "${MIGRATIONS_DIR}/V2.0."[0-2]* "${FLYWAY_DIR}"/sql/
fi

cat >"${FLYWAY_DIR}/conf/flyway.conf" <<EOF
flyway.password=${TARGET_DB_PASSWORD}
flyway.placeholders.hashShardCount=6
flyway.placeholders.maxEntityId=5000000
flyway.placeholders.maxEntityIdRatio=2.0
flyway.placeholders.partitionStartDate='2019-09-01'
flyway.placeholders.partitionTimeInterval='1 month'
flyway.placeholders.schema=${TARGET_DB_SCHEMA}
flyway.placeholders.shardCount=16
flyway.url=jdbc:postgresql://${TARGET_DB_HOST}:${TARGET_DB_PORT}/${TARGET_DB_NAME}
flyway.user=${TARGET_DB_USER}
EOF

log "Running Flyway migrate"
flyway migrate

TABLES_QUERY="
select pc.relname from pg_class pc
join pg_database pd on pc.relowner = pd.datdba
join pg_catalog.pg_namespace ns on ns.oid = pc.relnamespace
left join pg_catalog.pg_statio_user_tables psu on psu.relname = pc.relname
where ns.nspname='${SOURCE_DB_SCHEMA}' and pd.datname = '${SOURCE_DB_NAME}' and pc.relkind in ('p', 'r') and pc.relname not similar to '%[0-9]' and pc.relname not in (${EXCLUDED_TABLES})
and pc.relname not in (${ASYNC_TABLES})
order by pg_total_relation_size(psu.relid) asc NULLS LAST;"

TABLES=$(querySource "${TABLES_QUERY}" | tr " " "\n")
ASYNC_TABLES=$(echo "${ASYNC_TABLES//[\']/}" | tr "," "\n")

declare tablesArray
IFS=" " readarray -t tablesArray <<< "${TABLES}"
tablesArray+=("topic_message")
tablesArray+=("${ASYNC_TABLES[@]}")

COUNT="${#tablesArray[@]}"
insertCursors

log "Migrating ${COUNT} tables from ${SOURCE_DB_HOST}:${SOURCE_DB_PORT} to ${TARGET_DB_HOST}:${TARGET_DB_PORT}. Tables: ${TABLES[*]}"

echo "${tablesArray[*]}" | tr " " "\n" |ASYNC_TABLES=$ASYNC_TABLES xargs -n 1 -P "${CONCURRENCY}" -I {} bash -c 'migrateTable "$@"' _ {}

log "migration completed in $SECONDS seconds."

popd || die "Couldn't change directory back to ${SCRIPTS_DIR}"
exit 0
