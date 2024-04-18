#!/usr/bin/env bash
set -o pipefail

# Print log statements to stdout
log() {
  echo "$(date --iso-8601=seconds) ${1}"
}

# Open pipe to issue commands to the source database from a session
sourcePipe() {
  PGPASSWORD="${SOURCE_DB_PASSWORD}" psql -q --csv -t -h "${SOURCE_DB_HOST}" -d "${SOURCE_DB_NAME}" -p "${SOURCE_DB_PORT}" -U "${SOURCE_DB_USER}"
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

die() {
  echo "$@"
  exit 1
}

readFifo() {
  while (true); do
    # Wait for the next line of input
    cat "${1}"
  done
}

migrateTableBinary() {
  local table="${1}"
  local tmpDir="/tmp/migrate-${table}-$$"
  mkdir -m 700 "${tmpDir}" || die "Couldn't make safe tmp ${tmpDir}"
  pushd "${tmpDir}" || die "Couldn't pushd ${tmpDir}"

  trap "handlePipeError 'for table ${table} (BINARY)'" SIGPIPE
#TODO how to get rid of this and make the querysource function available
  cat >querySource.sh <<EOF
#!/usr/bin/env bash
set > $tmpDir/env
PGPASSWORD="${SOURCE_DB_PASSWORD}" psql -q --csv -t -h "${SOURCE_DB_HOST}" -d "${SOURCE_DB_NAME}" -p "${SOURCE_DB_PORT}" -U "${SOURCE_DB_USER}" "$@"
EOF
  chmod +x querySource.sh queryTarget.sh
  local has_data=$(queryTarget "select exists (select * from ${table} limit 1)")
  local maxTimestamp=${MAX_TIMESTAMP:-$2}
  local query="
      select string_agg(column_name, ', ' order by ordinal_position)
      from information_schema.columns
      where table_schema = '${SOURCE_DB_SCHEMA}' and table_name = '${table}';"

  if [[ "${has_data}" = "t" ]]; then
    log "Skipping '${table}' table since it contains existing data"
    return
  fi

  local columns=$(querySource "${query}")
  columns="${columns#"${columns%%[![:space:]]*}"}" # Trim leading whitespace

  if [[ -n "${MAX_TIMESTAMP}" && "${columns}" =~ consensus_timestamp ]]; then
    where="where consensus_timestamp <= ${MAX_TIMESTAMP}"
  fi

  log "Starting to copy '${table}' table"

  queryTarget "\\copy ${table}(${columns}) FROM PROGRAM 'echo copy \(select ${columns} from ${table} ${where}\) to STDOUT with BINARY; \\q|./querySource.sh' WITH BINARY;"

  log "Copied '${table}' table in ${SECONDS}s"
}

handlePipeError() {
  echo "SIGPIPE received for pid $$ on pipe $!{EPIPE} msg: ${1}"
  exit 1
}

executeCursor() {
  local crsrDef="${1}"
  local crsrId="${2}"
  local tmpDir="${3}"
  local source="${tmpDir}/source${crsrId}"
  trap "handlePipeError 'for crsr ${crsrId} with def ${crsrDef}'" SIGPIPE

  mkfifo "${source}"

  # Initialize psql session for source
  log "Starting to copy '${table}' for crsr ${crsrId} on pid $!"
  readFifo "${source}" | sourcePipe &

  #Initialize the cursor
  echo "set cursor_tuple_fraction = 1;" >"${source}"
  echo "BEGIN;" >"${source}"
  echo "${crsrDef}" >"${source}"

  local count=0
  #TODO exit on bookmark call empty
  while [[ count -le 1000 ]]; do
    ((count++))

    #Allow an additional process to start to prepare connection and initiate batch
    ACTIVE_COPIES=$(ls "${tmpDir}" | grep "output-${crsrId}" | wc | awk '{print $1}')
    while [[ "${ACTIVE_COPIES}" -ge 2 ]]; do
      sleep .5
      ACTIVE_COPIES=$(ls "${tmpDir}" | grep "output-${crsrId}" | wc | awk '{print $1}')
    done

    # Setup output pipe
    local outputFile="${tmpDir}/output-${crsrId}-$(uuidgen)"
    mkfifo "${outputFile}"
    echo "\o ${outputFile}" >"${source}"

    # Listen to data on output pipe
    queryTarget "\copy ${table}(${columns}) FROM program 'cat ${outputFile}' WITH CSV DELIMITER ',';" &&
    rm "${outputFile}" && \
    log "Finished batch. Progress for ${table} cursor ${crsrId}: $((count * CURSOR_COPY_BATCH_SIZE)) records processed" &

    # Retrieve the next batch
    echo "FETCH ${CURSOR_COPY_BATCH_SIZE} FROM crsr;" >"${source}"

    log "Requested batch ${count} for table ${table} cursor ${crsrId}"

    # Terminate the output pipe
    echo "\o /dev/null" >"${source}"

    #TODO perform bookmark fetch and update bookmark migration table
  done

  echo "\q" >"${source}"
  wait
  log "Finished copying '${table}' for crsr ${crsrId}"
}

migrateTableCursor() {
  local table="${1}"
  local tmpDir="/tmp/migrate-${table}-$$"

  mkdir -m 700 "${tmpDir}" || die "Couldn't make safe tmp ${tmpDir}"
  pushd "${tmpDir}" || die "Couldn't change directory to tmp ${tmpDir}"

  local has_data=$(echo "select exists (select * from ${table} limit 1)" | queryTarget)
  local maxTimestamp=${MAX_TIMESTAMP:-$2}
  local query="
      select string_agg(column_name, ', ' order by ordinal_position)
      from information_schema.columns
      where table_schema = '${SOURCE_DB_SCHEMA}' and table_name = '${table}';"

  if [[ "${has_data}" = "t" ]]; then
    log "Skipping '${table}' table since it contains existing data"
    return
  fi

  local columns=$(querySource "${query}")
  columns="${columns#"${columns%%[![:space:]]*}"}" # Trim leading whitespace

  local minConsensusTimestamp=0
  local migrationRangeNs=$((2**64-1))
  if [[ "${columns}" =~ consensus_timestamp ]]; then
    minConsensusTimestamp=$(querySource "select min(consensus_timestamp) from ${table};")
    migrationRangeNs=$((MAX_TIMESTAMP - minConsensusTimestamp))
    orderby="order by consensus_timestamp desc"
  fi

  local crsrRangeNs=$((migrationRangeNs / CONCURRENT_CURSORS_PER_TABLE))
  log "Cursor will process from ${MAX_TIMESTAMP} to ${minConsensusTimestamp} with each cursor processing ${crsrRangeNs} ns of timestamps for total range of ${migrationRangeNs} ns pid $$"
  for i in $(seq 1 "${CONCURRENT_CURSORS_PER_TABLE}"); do
    local factor=$((i - 1))
    local upperBound="$((MAX_TIMESTAMP - (factor * crsrRangeNs)))"
    local lowerBound="$((upperBound - crsrRangeNs))"
    if [[ "${columns}" =~ consensus_timestamp ]]; then
      if [[ "${i}" -eq "${CONCURRENT_CURSORS_PER_TABLE}" ]]; then
        where="where consensus_timestamp <= ${upperBound}"
      else
        where="where consensus_timestamp <= ${upperBound} and consensus_timestamp > ${lowerBound}"
      fi
    fi

    local cursorDef="DECLARE crsr CURSOR FOR select ${columns} from ${table} ${where} ${orderby};"
    executeCursor "${cursorDef}" "${i}" "${tmpDir}" &
    log "Created cursor with definition: ${cursorDef} pid $!"
  done

  log "waiting for ${table} jobs to finish"
  wait

  log "Copied '${table}' table in ${SECONDS}s"
  jobs
}
# Export the functions so they can be invoked via parallel xargs
export -f log querySource queryTarget migrateTableBinary migrateTableCursor executeCursor handlePipeError sourcePipe

SECONDS=0
SCRIPTS_DIR="./"
EXPORT_DIR="${SCRIPTS_DIR}/export"
FLYWAY_DIR="${EXPORT_DIR}/flyway"
MIGRATIONS_DIR="${SCRIPTS_DIR}/../../migration/v2"
export PATH="${FLYWAY_DIR}/:$PATH"
source "${SCRIPTS_DIR}/migration.config"

export CONCURRENCY=${CONCURRENCY:-5}
export CONCURRENT_CURSORS_PER_TABLE=${CONCURRENT_CURSORS_PER_TABLE:-5}
export CURSOR_COPY_BATCH_SIZE=${CURSOR_COPY_BATCH_SIZE:-1000000}
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

#TODO remove?
rm -rf "${EXPORT_DIR}"
mkdir -p "${EXPORT_DIR}"
cd "${EXPORT_DIR}" || die "Couldn't change directory to ${EXPORT_DIR}"

log "Installing Flyway"
wget -qO- ${FLYWAY_URL} | tar -xz && mv flyway-* flyway
log "Flyway installed"

log "Copying Flyway configuration"
#cp "${MIGRATIONS_DIR}/V2.0."[0-2]* ${FLYWAY_DIR}/sql/
#cat > "${FLYWAY_DIR}/conf/flyway.conf" <<EOF
#flyway.password=${TARGET_DB_PASSWORD}
#flyway.placeholders.hashShardCount=6
#flyway.placeholders.idPartitionSize=1000000
#flyway.placeholders.maxEntityId=5000000
#flyway.placeholders.maxEntityIdRatio=2.0
#flyway.placeholders.partitionStartDate='2019-09-01'
#flyway.placeholders.partitionTimeInterval='1 month'
#flyway.placeholders.schema=${TARGET_DB_SCHEMA}
#flyway.placeholders.shardCount=16
#flyway.url=jdbc:postgresql://${TARGET_DB_HOST}:${TARGET_DB_PORT}/${TARGET_DB_NAME}
#flyway.user=${TARGET_DB_USER}
#EOF

log "Running Flyway migrate"
#flyway migrate

TABLES_QUERY="
select relname from pg_catalog.pg_statio_user_tables
where schemaname = '${SOURCE_DB_SCHEMA}' and relname not similar to '%[0-9]' and relname not in (${EXCLUDED_TABLES})
and relname not in (${ASYNC_TABLES})
order by pg_total_relation_size(relid) asc;"

TABLES=$(querySource "${TABLES_QUERY}" | tr " " "\n")
COUNT=$(echo "${TABLES}" | wc -l)

log "Migrating ${COUNT} async tables from ${SOURCE_DB_HOST}:${SOURCE_DB_PORT} to ${TARGET_DB_HOST}:${TARGET_DB_PORT}. Tables: ${TABLES}"
#echo "${TABLES}" | xargs -n 1 -P "${CONCURRENCY}" -I {} bash -c 'migrateTableBinary "$@"' _ {}
#echo "${ASYNC_TABLES}" | xargs -n 1 -P "${CONCURRENCY}" -I {} bash -c 'migrateTableCursor "$@"' _ {}
time migrateTableCursor transaction

log "Migration completed in $SECONDS seconds"

#TODO handle not getting future partitions
#queryTarget "CREATE TABLE IF NOT EXISTS async_migration_status (parent TEXT, partition TEXT primary key, start_partition bigint, end_partition bigint, processed_range int8range, migration_start timestamptz, migration_stop timestamptz);"
#queryTarget "insert into async_migration_status(parent, partition, start_partition, end_partition) select parent, name, from_timestamp, to_timestamp from mirror_node_time_partitions where parent not in (${EXCLUDED_TABLES}) and parent in (${ASYNC_TABLES}) ON CONFLICT(partition) DO NOTHING;"
### to do need to select upper and lower processed range
#ASYNC_TABLE_PARTITIONS=$(queryTarget "select parent, partition, start_partition, end_partition from async_migration_status where processed_range IS NULL or processed_range != int8range(start_partition, end_partition, '[)') order by start_partition DESC, partition;" | tr " " "\n")
#echo "${ASYNC_TABLE_PARTITIONS}"
#echo "The aysnc tables are ${ASYNC_TABLE_PARTITIONS}"
#echo "${ASYNC_TABLE_PARTITIONS}"| awk -F , '{print $1,$2,$3,$4,$5,$6};' |xargs -P "${CONCURRENCY}" -n1 -I {} bash -c 'migrateAsyncTable $1 $2 $3 $4 $5 $6' _ {}

exit 0

#TODO
#populate transaction hash
# turn off on vacuum?