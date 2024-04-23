#!/usr/bin/env bash
set -emo pipefail

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

targetTableExists() {
  local table="${1}"
  local query="select exists (select from information_schema.tables where table_schema = '${TARGET_DB_SCHEMA}' and table_name = '${table}');"
  local exists=$(queryTarget "${query}")
  echo "${exists}"
}

getColumns() {
  local table="${1}"
  local query="select string_agg(concat('mt.', column_name), ', ' order by ordinal_position) from information_schema.columns where table_schema = '${SOURCE_DB_SCHEMA}' and table_name = '${table}';"
  local columns
  columns=$(querySource "${query}")
  columns="${columns#"${columns%%[![:space:]]*}"}" # Trim leading whitespace
  echo "${columns}"
}

insertCursors() {
  local hasTable="f"
  hasTable=$(targetTableExists "async_migration_status")
  if [[ "${hasTable}" = "t" ]]; then
    return
  else
    log "Creating async_migration_status table"
  fi

  local tableDDL="
  create table if not exists async_migration_status (
  the_table                varchar(100),
  cursor_id                int,
  cursor_range             int8range,
  last_processed_timestamp bigint,
  last_processed_entity    bigint);"
  queryTarget "${tableDDL}"

  local conditions
  getCursorConditions conditions
  local cursorId=1
  for value in "${conditions[@]}"; do
    for table in $(echo "${ASYNC_TABLES[@]}" | tr "," "\n"); do
      local insertSql="insert into async_migration_status (the_table, cursor_id, cursor_range) values (${table}, ${cursorId}, ${value});"
      log "Inserting cursor sql: ${insertSql}"
      queryTarget "${insertSql}"
    done
    cursorId=$((cursorId + 1))
  done
}

getCursorConditions() {
  local query="
  with record_file_stats as (
    select date_trunc('month', to_timestamp(consensus_end / 1000000000)) start_timestamp, sum(count) as record_count from record_file group by 1 order by 1 desc
  ),
  total_records as (
    select sum(record_count) as total_record_count
    from record_file_stats
  )
  select
  (100 * (1.0 * rfs.record_count) / tr.total_record_count) as pct_of_total,
  (extract(epoch from rfs.start_timestamp) * 1000000000)::bigint as lower_bound,
  (extract(epoch from (rfs.start_timestamp + INTERVAL '1 month')) * 1000000000)::bigint as upper_bound
  from record_file_stats rfs, total_records tr
  order by 3 desc
  "

  local upperBound=$MAX_TIMESTAMP
  local pctPerCursor=$((100 / CONCURRENT_CURSORS_PER_TABLE))
  local totalPct=0
  local -n cursors=$1 # Init array to hold cursor definitions

  local stats=$(PGPASSWORD="${SOURCE_DB_PASSWORD}" psql -q --csv -t -h "${SOURCE_DB_HOST}" -d "${SOURCE_DB_NAME}" -p "${SOURCE_DB_PORT}" -U "${SOURCE_DB_USER}" -c "${query}")
  while IFS="," read -r pct_of_total lower_bound upper_bound; do
    local newTotal=$(echo "(${totalPct} + ${pct_of_total})" | bc)
    if (($(echo "${newTotal} < ${pctPerCursor}" | bc))); then
      totalPct=$newTotal
      upperBound=$((upperBound > upper_bound ? upperBound : upper_bound))
    else
      difference=$(echo "${newTotal} - ${pctPerCursor}" | bc)
      groupRange=$((upper_bound - lower_bound))
      cursorLowerBound=$(echo "${upper_bound} - ((100 - ${difference}) / 100) * ${groupRange}" | bc)
      cursors+=("'(${cursorLowerBound}, ${upperBound}]'")

      upperBound=$cursorLowerBound
      totalPct=$difference
      if (($(echo "(${#cursors[@]} + 1) == ${CONCURRENT_CURSORS_PER_TABLE}" | bc))); then
        cursors+=("'(,${cursorLowerBound}]'")
        break
      fi
    fi
  done <<<"${stats}"
}

migrateTableBinary() {
  local table="${1}"
  local maxTimestamp=${MAX_TIMESTAMP:-$2}

  trap "handlePipeError 'for table ${table} (BINARY)'" SIGPIPE

  local tableExists="f"
  tableExists=$(targetTableExists "${table}")
  if [[ "${tableExists}" = "f" ]]; then
    log "Skipping '${table}' table since it doesn't exist on the target"
    return
  fi

  local has_data=$(queryTarget "select exists (select * from ${table}  limit 1)")
  if [[ "${has_data}" = "t" ]]; then
    log "Skipping '${table}' table since it contains existing data"
    return
  fi

  local columns
  columns=$(getColumns "${table}")

  if [[ "${columns}" =~ $TS_COLUMN_REGEX ]]; then
    where="where consensus_timestamp \<= ${maxTimestamp}"
  elif [[ "${columns}" =~ \<consensus_timestamp_start\> ]]; then
    where="where consensus_timestamp \<= ${maxTimestamp} and type=27"
  fi

  log "Starting to copy '${table}' table with sql: \\copy ${table}(${columns}) FROM PROGRAM 'echo copy \(select ${columns} from ${table} ${where}\) to STDOUT with BINARY|./querySource.sh' WITH BINARY;"

  queryTarget "\\copy ${table}(${columns//mt./}) FROM PROGRAM 'echo copy \(select ${columns} from ${table} mt ${where}\) to STDOUT with BINARY|./querySource.sh' WITH BINARY;"

  log "Copied '${table}' table in ${SECONDS}s"
}

handlePipeError() {
  log "SIGPIPE received for pid $$ on pipe $!{EPIPE} msg: ${1}"
  exit 1
}

handleExit() {
  local error_code=$?
  local error_line="${BASH_LINENO[*]}"
  local error_command=$BASH_COMMAND
  log "pid: $! on $$ Received non zero exit code: (${error_code}) ${1} at ${error_line}: ${error_command}"
  exit 1
}

executeBookmark() {
  local count="${1}"
  local bookMarkFile="${outputFile}-bookmark"
  local bookMarkFileTmp="${bookMarkFile}.tmp"
  mkfifo "${bookMarkFile}"
  echo "\o ${bookMarkFile}" >"${source}"

  cat "${bookMarkFile}" >"${bookMarkFileTmp}" &
  echo "fetch 1 from crsr;" >"${source}"
  log "Waiting for bookmark for batch ${count} on ${table} for cursorId ${cursorId}"
  echo "\o /dev/null" >"${source}"
  wait "$!" # batch call is issued first. Wait here on bookmark will ensure batch copy and bookmark has already completed

  rm "${bookMarkFile}"
  local row
  row=$(<"${bookMarkFileTmp}")
  rm "${bookMarkFileTmp}"

  local entityIdColumn

  if [[ "${table}" = "topic_message" ]]; then
    entityIdColumn="topic_id"
  elif [[ "${table}" = "transaction" ]]; then
    entityIdColumn="payer_account_id"
  elif [[ "${table}" = "crypto_transfer" ]]; then
    entityIdColumn="entity_id"
  else
    logError "Unknown entity id column for ${table}"
    exit 1
  fi

  if [[ -z "${timestampIndex}" ]]; then
    IFS=', ' read -r -a columnArray <<<"$columns"
    timestampIndex=0
    for column in "${columnArray[@]}"; do
      if [[ "${column}" =~ $TS_COLUMN_REGEX ]]; then
        break;
      fi
      timestampIndex=$((timestampIndex + 1))
    done
  fi

  if [[ -z "${entityIdIndex}" ]]; then
    IFS=', ' read -r -a columnArray <<<"$columns"
    entityIdIndex=0
    for column in "${columnArray[@]}"; do
      if [[ "${column}" =~ $entityIdColumn ]]; then
        break;
      fi
      entityIdIndex=$((entityIdIndex + 1))
    done
  fi

  if [[ -z "${row}" ]]; then
    log "Finished copying '${table}' for crsr ${cursorId} in $SECONDS seconds"
    echo "\\q" >"${source}"
    queryTarget "update async_migration_status set last_processed_timestamp=lower(cursor_range) where the_table='${table}' and cursor_id=${cursorId};"
    exit 0
  fi

  IFS=',' read -r -a rowArray <<<"${row}"
  local sql="\\copy ${table}(${columns//mt./}) from program 'echo ${row}' with csv delimiter ',';
  update async_migration_status set last_processed_timestamp=${rowArray[$timestampIndex]}, last_processed_entity=${rowArray[$entityIdIndex]} where the_table='${table}' and cursor_id=${cursorId};"
  queryTarget "${sql}"

  log "Updated bookmark for batch ${count} with timestamp: ${rowArray[$timestampIndex]} entity_id: ${rowArray[$entityIdIndex]} for ${table} on cursor ${cursorId}"
}

executeCursor() {
  local crsrDef="${1}"
  local cursorId="${2}"
  local tmpDir="${3}"
  local source="${tmpDir}/source${cursorId}"
  trap "handlePipeError 'for crsr ${cursorId} with def ${crsrDef}'" SIGPIPE
  trap "handleExit" ERR

  mkfifo "${source}"

  # Initialize psql session for source
  log "Starting to copy '${table}' for crsr ${cursorId} on pid $!"
  readFifo "${source}" | sourcePipe &

  local startSql="BEGIN; ${crsrDef}"
  if [[ "${table}" != "topic_message" ]]; then
    startSql="set cursor_tuple_fraction = 1; ${startSql}"
  fi
  local batchSql="FETCH ${CURSOR_COPY_BATCH_SIZE} FROM crsr;"
  local count=0
  while true; do
    SECONDS=0
    count=$((count + 1))

    # Setup output pipe
    local outputFile="${tmpDir}/output-${cursorId}-$(uuidgen)"
    mkfifo "${outputFile}"
    echo "\o ${outputFile}" >"${source}"

    # Listen to data on output pipe
    queryTarget "\copy ${table}(${columns//mt./}) from program 'cat ${outputFile}' with csv delimiter ',';" &

    # Retrieve the next batch
    if [[ "${count}" -eq 1 ]]; then
      echo "${startSql} ${batchSql}" >"${source}"
    else
      echo "${batchSql}" >"${source}"
    fi

    log "Requested batch ${count} on table ${table} for cursor ${cursorId}"

    # Terminate the output pipe
    echo "\o /dev/null" >"${source}"

    executeBookmark "${count}"
    rm "${outputFile}"
    log "Finished batch ${count} in $SECONDS seconds on ${table} for cursor ${cursorId} $((count * CURSOR_COPY_BATCH_SIZE)) total records processed"
  done
}

migrateTableCursor() {
  local table="${1}"
  local tmpDir="/tmp/migrate-${table}-$$"

  mkdir -m 700 "${tmpDir}" || die "Couldn't make safe tmp ${tmpDir}"
  pushd "${tmpDir}" || die "Couldn't change directory to tmp ${tmpDir}"
  local maxTimestamp=${MAX_TIMESTAMP:-$2}

  local columns
  columns=$(getColumns "${table}")
  if [[ "${columns}" =~ $TS_COLUMN_REGEX ]]; then
    orderby="order by mt.consensus_timestamp desc"
  fi

  local cursorQuery="SELECT cursor_id, lower(cursor_range), least(last_processed_timestamp - 1, upper(cursor_range)), last_processed_entity FROM async_migration_status WHERE the_table = '${table}' ORDER BY cursor_id asc;"
  #TODO make existing function work
  local tableCursors=$(PGPASSWORD="${TARGET_DB_PASSWORD}" psql -q --csv -t -h "${TARGET_DB_HOST}" -d "${TARGET_DB_NAME}" -p "${TARGET_DB_PORT}" -U "${TARGET_DB_USER}" -c "${cursorQuery}")
  log "The table cursors are ${tableCursors}"
  while IFS="," read -r cursor_id cursor_lower_bound cursor_upper_bound last_entity; do
    orderby=""
    where=""
    join=""
    if [[ "${columns}" =~ $TS_COLUMN_REGEX ]]; then
      if [[ -z "${cursor_lower_bound}" ]]; then
        where="where mt.consensus_timestamp <= ${cursor_upper_bound}"
      else
        where="where mt.consensus_timestamp <= ${cursor_upper_bound} and mt.consensus_timestamp > ${cursor_lower_bound}"
      fi
    fi
    if [[ "${table}" = "topic_message" ]]; then
      join="join transaction jt on mt.topic_id = jt.entity_id and mt.consensus_timestamp = jt.consensus_timestamp"
      where="${where//mt./jt.} and jt.type=27"
      orderby="${orderby//mt./jt.}"
    elif [[ "${table}" = "crypto_transfer" ]]; then
      orderby="${orderby}, mt.entity_id desc"
    fi

    local cursorDef="DECLARE crsr NO SCROLL CURSOR FOR select ${columns} from ${table} mt ${join} ${where} ${orderby} FOR READ ONLY;"
    executeCursor "${cursorDef}" "${cursor_id}" "${tmpDir}" &
    log "Created cursor with definition: ${cursorDef} pid $!"
  done <<<"${tableCursors}"

  log "waiting for ${table} jobs to finish"
  wait

  log "Copied '${table}' table in ${SECONDS}s"
  jobs
}

# Export the functions so they can be invoked via parallel xargs
export -f log querySource queryTarget migrateTableBinary migrateTableCursor executeBookmark executeCursor handlePipeError handleExit sourcePipe readFifo targetTableExists getColumns

SECONDS=0
SCRIPTS_DIR="./"
FLYWAY_DIR="/tmp/flyway"
MIGRATIONS_DIR="${SCRIPTS_DIR}/../../migration/v2"
export PATH="${FLYWAY_DIR}/:$PATH"
source "${SCRIPTS_DIR}/migration.config"

export CONCURRENCY=${CONCURRENCY:-5}
export CONCURRENT_CURSORS_PER_TABLE=${CONCURRENT_CURSORS_PER_TABLE:-5}
export CURSOR_COPY_BATCH_SIZE=${CURSOR_COPY_BATCH_SIZE:-1000000}
export CREATE_INDEXES_BEFORE_MIGRATION=${CREATE_INDEXES_BEFORE_MIGRATION:-true}
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
#mkdir -p "${FLYWAY_DIR}" || die "Couldn't create directory ${FLYWAY_DIR}"
#cd "${FLYWAY_DIR}" || die "Couldn't change directory to ${FLYWAY_DIR}"
#
#log "Installing Flyway"
#wget -qO- ${FLYWAY_URL} | tar -xz && mv flyway-* flyway
#log "Flyway installed"
#
#log "Copying Flyway configuration"
#
#if [[ "${CREATE_INDEXES_BEFORE_MIGRATION}" = "true" ]]; then
#  cp "${MIGRATIONS_DIR}/V2.0."* "${FLYWAY_DIR}"/sql/
#else
#  cp "${MIGRATIONS_DIR}/V2.0."[0-2]* "${FLYWAY_DIR}"/sql/
#fi
#cat >"${FLYWAY_DIR}/conf/flyway.conf" <<EOF
#flyway.password=${TARGET_DB_PASSWORD}
#flyway.placeholders.hashShardCount=6
#flyway.placeholders.maxEntityId=5000000
#flyway.placeholders.maxEntityIdRatio=2.0
#flyway.placeholders.partitionStartDate='2019-09-01'
#flyway.placeholders.partitionTimeInterval='1 month'
#flyway.placeholders.schema=${TARGET_DB_SCHEMA}
#flyway.placeholders.shardCount=16
#flyway.url=jdbc:postgresql://${TARGET_DB_HOST}:${TARGET_DB_PORT}/${TARGET_DB_NAME}
#flyway.user=${TARGET_DB_USER}
#EOF
#
#log "Running Flyway migrate"
#flyway migrate

TABLES_QUERY="
select pc.relname from pg_class pc
join pg_database pd on pc.relowner = pd.datdba
join pg_catalog.pg_namespace ns on ns.oid = pc.relnamespace
left join pg_catalog.pg_statio_user_tables psu on psu.relname = pc.relname
where ns.nspname='${SOURCE_DB_SCHEMA}' and pd.datname = '${SOURCE_DB_NAME}' and pc.relkind in ('p', 'r') and pc.relname not similar to '%[0-9]' and pc.relname not in (${EXCLUDED_TABLES})
and pc.relname not in (${ASYNC_TABLES})
order by pg_total_relation_size(psu.relid) asc NULLS LAST;"

TABLES=$(querySource "${TABLES_QUERY}" | tr " " "\n")
COUNT=$(echo "${TABLES}" | wc -l)

#TODO how to get rid of this and make the querysource function available
cat >querySource.sh <<EOF
#!/usr/bin/env bash
PGPASSWORD="${SOURCE_DB_PASSWORD}" psql -q -t -h "${SOURCE_DB_HOST}" -d "${SOURCE_DB_NAME}" -p "${SOURCE_DB_PORT}" -U "${SOURCE_DB_USER}"
EOF
chmod +x querySource.sh

log "Migrating ${COUNT} tables from ${SOURCE_DB_HOST}:${SOURCE_DB_PORT} to ${TARGET_DB_HOST}:${TARGET_DB_PORT}. Tables: ${TABLES}"
echo "${TABLES}" | xargs -n 1 -P "${CONCURRENCY}" -I {} bash -c 'migrateTableBinary "$@"' _ {}
rm querySource.sh

log "Synchronous migration completed in $SECONDS seconds. Starting async migration for tables: ${ASYNC_TABLES}"
insertCursors
SECONDS=0
ASYNC_TABLES=$(echo "${ASYNC_TABLES//[\']/}" | tr "," "\n")
echo "${ASYNC_TABLES}" | xargs -n 1 -P "${CONCURRENCY}" -I {} bash -c 'migrateTableCursor "$@"' _ {}
log "Async migration completed in $SECONDS seconds"

exit 0

