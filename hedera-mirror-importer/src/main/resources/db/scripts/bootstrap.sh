#!/bin/bash

# Write the script's own PID to bootstrap.pid
PID_FILE="bootstrap.pid"
echo $$ > $PID_FILE

# Enable job control
set -m

####################################
# Variables
####################################

# Define minimum required Bash version
REQUIRED_BASH_MAJOR=4
REQUIRED_BASH_MINOR=3

# Logging and tracking files
LOG_FILE="bootstrap.log"
TRACKING_FILE="bootstrap_tracking.txt"
LOCK_FILE="bootstrap_tracking.lock"

# Required tools
REQUIRED_TOOLS=("psql" "gunzip" "realpath" "flock" "curl")

# Flag file to skip database initialization
FLAG_FILE="SKIP_DB_INIT"

# Assign script arguments
DB_CPU_CORES="$1"
IMPORT_DIR="$2"

# Convert IMPORT_DIR to an absolute path
IMPORT_DIR="$(realpath "$IMPORT_DIR")"

# Calculate available CPU cores
AVAILABLE_CORES=$(($(nproc) - 1))        # Leave one core free for the local system
DB_AVAILABLE_CORES=$((DB_CPU_CORES - 1)) # Leave one core free for the DB instance

# Read manifest.csv into an associative array
declare -A manifest_counts
MANIFEST_FILE="${IMPORT_DIR}/manifest.csv"
DISCREPANCY_FILE="discrepancies.log"

# Get the version from MIRRORNODE_VERSION file
MIRRORNODE_VERSION_FILE="$IMPORT_DIR/MIRRORNODE_VERSION"

####################################
# Functions
####################################

enable_pipefail() {
  set -euo pipefail
}

disable_pipefail() {
  set +euo pipefail
}

export -f enable_pipefail disable_pipefail

# Log messages with UTC timestamps
log() {
  local msg="$1"
  local level="${2:-INFO}"
  local timestamp
  timestamp=$(date -u '+%Y-%m-%d %H:%M:%S')

  echo "[$timestamp] [$level] $msg" >> "$LOG_FILE"
}

# Display help message
show_help() {
  echo "Usage: $0 [OPTIONS] DB_CPU_CORES IMPORT_DIR"
  echo
  echo "Imports data into a PostgreSQL database from compressed CSV files."
  echo
  echo "Options:"
  echo "  -h, --help, -H     Show this help message and exit."
  echo
  echo "Arguments:"
  echo "  DB_CPU_CORES       Number of CPU cores on the DB instance to thread the import jobs."
  echo "  IMPORT_DIR         Path to the directory containing the compressed CSV files."
  echo
  echo "Example:"
  echo "  $0 8 /path/to/db_export"
  echo
}

# Check Bash version
check_bash_version() {
  local current_major=${BASH_VERSINFO[0]}
  local current_minor=${BASH_VERSINFO[1]}

  if (( current_major < REQUIRED_BASH_MAJOR )) || \
    (( current_major == REQUIRED_BASH_MAJOR && current_minor < REQUIRED_BASH_MINOR )); then
    echo "Error: Bash version ${REQUIRED_BASH_MAJOR}.${REQUIRED_BASH_MINOR}+ is required. Current version is ${BASH_VERSION}." >&2
    exit 1
  fi
}

# Kill a process and its descendants
kill_descendants() {
  local pid="$1"
  local children
  children=$(pgrep -P "$pid")
  for child in $children; do
    kill_descendants "$child"
  done
  kill -TERM "$pid" 2>/dev/null
}

# Handle script termination
cleanup() {
  disable_pipefail
  log "Script interrupted. Terminating background jobs..." "ERROR"
  # Ignore further signals during cleanup
  trap '' SIGINT SIGTERM

  # Kill all background jobs and their descendants
  for pid in "${pids[@]}"; do
    kill_descendants "$pid"
  done

  wait 2>/dev/null
  log "All background jobs terminated."

  # Cleanup on termination
  rm -f $PID_FILE $LOCK_FILE

  exit 1
}

# Safely write to the tracking file with a lock
write_tracking_file() {
  local file="$1"
  local status="$2"
  (
    flock -x 200

    # Remove any existing entry for the file
    grep -v "^$file " "$TRACKING_FILE" > "${TRACKING_FILE}.tmp" 2>/dev/null || true
    mv "${TRACKING_FILE}.tmp" "$TRACKING_FILE"

    # Add the new status
    echo "$file $status" >> "$TRACKING_FILE"
  ) 200>"$LOCK_FILE"
}

# Read status from the tracking file
read_tracking_status() {
  local file="$1"
  grep "^$file " "$TRACKING_FILE" 2>/dev/null | awk '{print $2}'
}

# Collect all import tasks (compressed CSV files)
collect_import_tasks() {
  enable_pipefail
  trap 'disable_pipefail' RETURN

  find "$IMPORT_DIR" -type f -name "*.csv.gz"
}

write_discrepancy() {
  local file="$1"
  local expected_count="$2"
  local actual_count="$3"

  # Only write if not already imported successfully
  discrepancy_entry="$filename: expected $expected_count, got $actual_count rows"
  if ! grep -q "^${file} IMPORTED$" "$TRACKING_FILE" 2>/dev/null; then
    echo "$discrepancy_entry" >> "$DISCREPANCY_FILE"
  fi
}

source_bootstrap_env() {
  local BOOTSTRAP_ENV_FILE="bootstrap.env"

  if [[ -f "$BOOTSTRAP_ENV_FILE" ]]; then
    log "Sourcing $BOOTSTRAP_ENV_FILE to set environment variables."
    set -a  # Automatically export all variables
    source "$BOOTSTRAP_ENV_FILE"
    set +a
  else
    log "Error: $BOOTSTRAP_ENV_FILE file not found." "ERROR"
    exit 1
  fi
}

process_manifest() {
  enable_pipefail
  trap 'disable_pipefail' RETURN

  # Declare manifest_counts and manifest_tables as global associative arrays
  declare -g -A manifest_counts
  declare -g -A manifest_tables
  declare -a missing_files=()

  # Check if the manifest file exists
  if [[ ! -f "$MANIFEST_FILE" ]]; then
    log "Error: Manifest file '$MANIFEST_FILE' not found." "ERROR"
    exit 1
  fi

  # Validate file count
  log "Validating file count" "INFO"
  # Count files in manifest (excluding header)
  manifest_file_count=$(tail -n +2 "$MANIFEST_FILE" | wc -l)

  # Count actual .gz files in IMPORT_DIR (recursively)
  actual_file_count=$(find "$IMPORT_DIR" -type f -name "*.gz" | wc -l)

  if [[ "$manifest_file_count" != "$actual_file_count" ]]; then
    log "File count mismatch! Manifest: $manifest_file_count, Directory: $actual_file_count" "ERROR"
    exit 1
  else
    log "File count validation successful" "INFO"
  fi

  # Populate manifest_counts, manifest_tables, and run file validations
  while IFS=',' read -r filename expected_count expected_size expected_crc32; do
    # Skip header line
    if [[ "$filename" == "filename" ]]; then
      continue
    fi

    # Find the file in IMPORT_DIR
    file_path=$(find "$IMPORT_DIR" -type f -name "$filename")

    if [[ -f "$file_path" ]]; then
      # Get CRC32 from GZIP footer and file-size
      actual_crc32=$(tail -c 8 "$file_path" | head -c 4 | tr -d '\000' | xxd -p)
      actual_size=$(stat -c%s "$file_path")

      # Compare file sizes (strip any whitespace from both values)
      if [[ "$actual_size" != "$expected_size" ]]; then
          log "File size mismatch for $filename. Expected: $expected_size bytes, Actual: $actual_size bytes" "ERROR"
          exit 1
      fi

      # Compare CRC32 values
      if [[ "$actual_crc32" != "$expected_crc32" ]]; then
        log "CRC32 mismatch for $filename. Expected: $expected_crc32, Actual: $actual_crc32" "ERROR"
        exit 1
      fi

      log "Successfully validated file-size and CRC32 for $filename"

      # Skip non-data files and entries with 'N/A' expected count
      if [[ "$expected_count" == "N/A" ]]; then
        continue
      fi
      manifest_counts["$filename"]="$expected_count"

      # Extract table name
      if [[ "$filename" == "topic_message_low_vol_topic_ids.csv.gz" ]]; then
        table="topic_message"
      elif [[ "$filename" =~ ^([^/]+)_part_ ]]; then
        table="${BASH_REMATCH[1]}"
      elif [[ "$filename" =~ ^([^/]+)\.csv\.gz$ ]]; then
        table="${BASH_REMATCH[1]}"
      else
        log "Could not determine table name from filename: $filename" "ERROR"
        continue
      fi

      # Store table name in manifest_tables
      manifest_tables["$table"]=1
    else
      missing_files+=("$filename")
    fi
  done <"$MANIFEST_FILE"

  # If there are missing files, report and exit
  if [[ ${#missing_files[@]} -gt 0 ]]; then
    log "The following files are listed in the manifest but are missing from the data directory:" "ERROR"
    for missing_file in "${missing_files[@]}"; do
      log "- $missing_file" "ERROR"
    done
    exit 1
  fi
}

# Initialize the database using init.sh
initialize_database() {
  enable_pipefail
  trap 'disable_pipefail' RETURN

  # Declare the bootstrap environment file as a local variable
  local BOOTSTRAP_ENV_FILE="bootstrap.env"

  # Reconstruct manifest_tables
  declare -A manifest_tables
  eval "$MANIFEST_TABLES_SERIALIZED"

  # Source the bootstrap.env file
  source_bootstrap_env

  # Construct the URL for init.sh
  INIT_SH_URL="https://raw.githubusercontent.com/hashgraph/hedera-mirror-node/refs/heads/main/hedera-mirror-importer/src/main/resources/db/scripts/init.sh"

  # Download init.sh
  log "Downloading init.sh from $INIT_SH_URL"

  if curl -fSLs -o "init.sh" "$INIT_SH_URL"; then
    log "Successfully downloaded init.sh"
  else
    log "Error: Failed to download init.sh" "ERROR"
    exit 1
  fi

  # Make init.sh executable
  chmod +x init.sh

  # Run init.sh to initialize the database
  log "Initializing the database using init.sh"

  if ./init.sh; then
    log "Database initialized successfully"
  else
    log "Error: Database initialization failed" "ERROR"
    exit 1
  fi

  # Update PostgreSQL environment variables to connect to 'mirror_node' database as 'mirror_node' user
  export PGUSER="mirror_node"
  export PGDATABASE="mirror_node"
  export PGPASSWORD="$OWNER_PASSWORD"

  log "Updated PostgreSQL environment variables to connect to 'mirror_node' database as user 'mirror_node'"

  # Set up the schema in the database
  if [[ -f "$IMPORT_DIR/schema.sql" ]]; then
    log "Executing schema.sql from $IMPORT_DIR"
    if psql -v ON_ERROR_STOP=1 -f "$IMPORT_DIR/schema.sql"; then
      log "schema.sql executed successfully"
    else
      log "Error: Failed to execute schema.sql" "ERROR"
      exit 1
    fi
  else
    log "Error: schema.sql not found in $IMPORT_DIR" "ERROR"
    exit 1
  fi

  # Check that each table exists in the database
  # Test database connectivity
  if ! psql -v ON_ERROR_STOP=1 -c '\q' >/dev/null 2>&1; then
    log "Error: Unable to connect to the PostgreSQL database." "ERROR"
    exit 1
  fi
  log "Successfully connected to the PostgreSQL database." "INFO"

  missing_tables=()
  declare -A checked_tables_map=()
  log "Checking table existence in the database"

  for table in "${!manifest_tables[@]}"; do
    log "Verifying existence of table: $table" "INFO"

    # Avoid duplicate checks
    if [[ -n "${checked_tables_map["$table"]:-}" ]]; then
      log "Table $table has already been checked. Skipping." "INFO"
      continue
    fi
    checked_tables_map["$table"]=1

    # Check if the table exists in the database, with a timeout
    if ! timeout 10 psql -v ON_ERROR_STOP=1 -qt -c "SELECT 1 FROM pg_class WHERE relname = '$table' AND relnamespace = 'public'::regnamespace;" | grep -q 1; then
      missing_tables+=("$table")
      log "$table missing from database" "ERROR"
    else
      log "$table exists in the database" "INFO"
    fi
  done

  # If any tables are missing, report and exit
  if [[ ${#missing_tables[@]} -gt 0 ]]; then
    log "====================================================" "ERROR"
    log "The following tables are missing in the database:" "ERROR"
    for table in "${missing_tables[@]}"; do
      log "- $table" "ERROR"
    done
    log "====================================================" "ERROR"
    exit 1
  else
    log "All tables exist in the database." "INFO"
  fi
}

# Import a single file into the database
import_file() {
  enable_pipefail
  trap 'disable_pipefail' RETURN

  local file="$1"
  local table
  local filename
  local expected_count
  local actual_count

  # Declare manifest_counts as an associative array, and reconstruct it in each background job from the serialized data
  declare -A manifest_counts
  eval "$MANIFEST_COUNTS_SERIALIZED"

  # Determine the table name
  if [[ "$(dirname "$file")" == "$IMPORT_DIR" ]]; then
    # Small table
    filename=$(basename "$file")

    # Skip non-table files
    if [[ "$filename" == "MIRRORNODE_VERSION" || "$filename" == "schema.sql" ]]; then
      log "Skipping non-table file: $filename" "INFO"
      return 0
    fi

    # Handle special case for topic_message_low_vol_topic_ids.csv.gz
    if [[ "$filename" == "topic_message_low_vol_topic_ids.csv.gz" ]]; then
      table="topic_message"
      log "Mapped $filename to table $table" "INFO"
    else
      table=$(basename "$file" .csv.gz)
    fi

    # Assign expected_count from manifest_counts
    expected_count="${manifest_counts["$filename"]}"
  else
    # Large table part
    filename="$(basename "$file")"
    table=$(basename "$(dirname "$file")")

    # Assign expected_count from manifest_counts
    expected_count="${manifest_counts["$filename"]}"
  fi

  # Update status to IN_PROGRESS
  write_tracking_file "$file" "IN_PROGRESS"
  log "Importing table $table from $file"

  # Execute the import within a transaction
  if gunzip -c "$file" | PGAPPNAME="$filename" psql -q -v ON_ERROR_STOP=1 --single-transaction -c "COPY $table FROM STDIN WITH CSV HEADER;"; then
    # Verification
    if [[ -z "$expected_count" || "$expected_count" == "N/A" ]]; then
      log "No expected row count for $filename in manifest, skipping verification."
      write_tracking_file "$file" "IMPORTED"
    else
      # Special case for 0.111.0 topic_message_low_vol_topic_ids
      if [[ "$MIRRORNODE_VERSION" == "0.111.0" && "$table" == "topic_message" && "$filename" == "topic_message_low_vol_topic_ids.csv.gz" ]]; then
        local atma_topic_id="1693742"
        if ! actual_count=$(psql -qt -v ON_ERROR_STOP=1 -c "SELECT COUNT(*) FROM \"$table\" WHERE topic_id != $atma_topic_id;" | xargs); then
          log "Error executing count query for $file" "ERROR"
          write_tracking_file "$file" "FAILED_TO_IMPORT"
          return 1
        fi
      # Common handling for small tables (both versions)
      elif [[ "$(dirname "$file")" == "$IMPORT_DIR" ]]; then
        if ! actual_count=$(psql -qt -v ON_ERROR_STOP=1 -c "SELECT COUNT(*) FROM \"$table\";" | xargs); then
          log "Error executing count query for $file" "ERROR"
          write_tracking_file "$file" "FAILED_TO_IMPORT"
          return 1
        fi
      # Large table handling (version specific)
      else
        local basename
        basename=$(basename "$file" .csv.gz)
        if [[ "$MIRRORNODE_VERSION" == "0.111.0" ]]; then
          if [[ "$basename" =~ ^${table}_part_[0-9]+_([0-9]+)_([0-9]+)$ ]]; then
            local start_ts="${BASH_REMATCH[1]}"
            local end_ts="${BASH_REMATCH[2]}"

            if ! actual_count=$(psql -qt -v ON_ERROR_STOP=1 -c "SELECT COUNT(*) FROM \"$table\" WHERE consensus_timestamp BETWEEN $start_ts AND $end_ts;" | xargs); then
              log "Error executing count query for $file" "ERROR"
              write_tracking_file "$file" "FAILED_TO_IMPORT"
              return 1
            fi
            log "Counted rows for table $table within timestamp range $start_ts to $end_ts" "INFO"
          else
            log "Error parsing timestamps from filename $basename" "ERROR"
            write_tracking_file "$file" "FAILED_TO_IMPORT"
          fi
        # Newer versions handling
        else
          if [[ "$basename" =~ ^${table}_part_[0-9]+_([0-9]+)_([0-9]+)(_([0-9]+))?$ ]]; then
            local start_ts="${BASH_REMATCH[1]}"
            local end_ts="${BASH_REMATCH[2]}"
            local topic_id="${BASH_REMATCH[4]}"

            if [[ "$table" == "topic_message" && -n "$topic_id" ]]; then
              # Topic message with topic_id suffix
              if ! actual_count=$(psql -qt -v ON_ERROR_STOP=1 -c "SELECT COUNT(*) FROM \"$table\" WHERE topic_id = $topic_id AND consensus_timestamp BETWEEN $start_ts AND $end_ts;" | xargs); then
                log "Error executing count query for $file" "ERROR"
                write_tracking_file "$file" "FAILED_TO_IMPORT"
                return 1
              fi
            else
              # Other part files
              if ! actual_count=$(psql -qt -v ON_ERROR_STOP=1 -c "SELECT COUNT(*) FROM \"$table\" WHERE consensus_timestamp BETWEEN $start_ts AND $end_ts;" | xargs); then
                log "Error executing count query for $file" "ERROR"
                write_tracking_file "$file" "FAILED_TO_IMPORT"
                return 1
              fi
            fi
          else
            log "Error parsing timestamps from filename $basename" "ERROR"
            write_tracking_file "$file" "FAILED_TO_IMPORT"
          fi
        fi
      fi
    fi

    # Verify the count matches expected
    if [[ "$actual_count" != "$expected_count" ]]; then
      log "Row count mismatch for $file. Expected: $expected_count, Actual: $actual_count" "ERROR"
      write_tracking_file "$file" "FAILED_TO_IMPORT"
      write_discrepancy "$file" "$expected_count" "$actual_count"
      return 1
    fi
  fi

  write_tracking_file "$file" "IMPORTED"
  log "Row count verified, successfully imported $file" "INFO"
}

####################################
# Execution
####################################

# Perform the Bash version check
check_bash_version

# Display help if no arguments are provided
if [[ $# -eq 0 ]]; then
  echo "No arguments provided. Use --help or -h for usage information."
  exit 1
fi

# Parse options
while [[ "$#" -gt 0 ]]; do
  case $1 in
  -h | --help | -H)
    show_help
    exit 0
    ;;
  *)
    break
    ;;
  esac
done

# Check if required arguments are supplied
if [[ -z "$DB_CPU_CORES" || -z "$IMPORT_DIR" ]]; then
  echo "Error: Both DB_CPU_CORES and IMPORT_DIR must be provided."
  echo "Use --help or -h for usage information."
  exit 1
fi

# Check if IMPORT_DIR exists and is a directory
if [[ ! -d "$IMPORT_DIR" ]]; then
  echo "Error: IMPORT_DIR '$IMPORT_DIR' does not exist or is not a directory."
  exit 1
fi

# Check if required tools are installed
missing_tools=()
for tool in "${REQUIRED_TOOLS[@]}"; do
  if ! command -v "$tool" &> /dev/null; then
    missing_tools+=("$tool")
  fi
done

if [[ ${#missing_tools[@]} -gt 0 ]]; then
  echo "Error: The following required tools are not installed:"
  for tool in "${missing_tools[@]}"; do
    echo "  - $tool"
  done
  echo "Please install them to continue."
  exit 1
fi

# Adjust max_jobs based on system limits
if [[ $AVAILABLE_CORES -lt $DB_AVAILABLE_CORES ]]; then
  max_jobs="$AVAILABLE_CORES"
else
  max_jobs="$DB_AVAILABLE_CORES"
fi

# Process the manifest and check for missing files
process_manifest

# Decompress schema.sql and MIRRORNODE_VERSION
for file in "$IMPORT_DIR/schema.sql.gz" "$IMPORT_DIR/MIRRORNODE_VERSION.gz"; do
  if ! gunzip -k -f "$file"; then
    log "Error decompressing $file" "ERROR"
    exit 1
  fi
done

# Serialize manifest_counts & manifest_tables arrays for export into the subshells
MANIFEST_COUNTS_SERIALIZED=$(declare -p manifest_counts)
MANIFEST_TABLES_SERIALIZED=$(declare -p manifest_tables)

# Grab the compatible mirrornode version
if [[ -f "$MIRRORNODE_VERSION_FILE" ]]; then
  MIRRORNODE_VERSION=$(tr -d '[:space:]' < "$MIRRORNODE_VERSION_FILE")
  log "Compatible Mirrornode version: $MIRRORNODE_VERSION"
else
  echo "Error: MIRRORNODE_VERSION file not found in $IMPORT_DIR."
  exit 1
fi

# Trap signals for cleanup
trap 'cleanup' SIGINT SIGTERM EXIT

# Log the start of the import process
log "Starting DB import."

# Initialize the database unless the flag file exists
if [[ ! -f "$FLAG_FILE" ]]; then
  initialize_database
  touch "$FLAG_FILE" # Create a flag to skip subsequent runs from running db init after it succeeded once
else
  # Source the bootstrap.env to set OWNER_PASSWORD
  source_bootstrap_env

  # Set PostgreSQL environment variables
  export PGUSER="mirror_node"
  export PGDATABASE="mirror_node"
  export PGPASSWORD="$OWNER_PASSWORD"

  log "Set PGUSER, PGDATABASE, and PGPASSWORD for PostgreSQL."

  # Validate that the database is already initialized
  if psql -U mirror_node -d mirror_node -c "\q" 2>/dev/null; then
    log "Skipping database initialization as '$FLAG_FILE' exists."
  else
    log "Error: Database is not initialized. Cannot skip database initialization." "ERROR"
    exit 1
  fi
fi

# Get the list of files to import
mapfile -t files < <(collect_import_tasks)

# Initialize the tracking file with all files as NOT_STARTED
(
  flock -x 200
  for file in "${files[@]}"; do
    # Only add if not already in tracking file
    if ! grep -q "^$file " "$TRACKING_FILE" 2>/dev/null; then
      echo "$file NOT_STARTED" >> "$TRACKING_FILE"
    fi
  done
) 200>"$LOCK_FILE"

# Initialize variables for background processes
pids=()
overall_success=1

# Export necessary functions and variables for subshells
export -f import_file log kill_descendants write_tracking_file read_tracking_status process_manifest source_bootstrap_env
export IMPORT_DIR LOG_FILE TRACKING_FILE LOCK_FILE MANIFEST_COUNTS_SERIALIZED DISCREPANCY_FILE MIRRORNODE_VERSION

# Loop through files and manage parallel execution
for file in "${files[@]}"; do
  # Check if the file has already been imported
  status=$(read_tracking_status "$file")
  if [[ "$status" == "IMPORTED" ]]; then
    log "Skipping already imported file $file"
    continue
  fi

  # Wait if max_jobs are already running
  while [[ ${#pids[@]} -ge $max_jobs ]]; do
    # Wait for any job to finish
    if ! wait -n; then
      overall_success=0
    fi

    # Remove completed PIDs from the array
    new_pids=()
    for pid in "${pids[@]}"; do
      if kill -0 "$pid" 2>/dev/null; then
        new_pids+=("$pid")
      fi
    done
    pids=("${new_pids[@]}")
  done

  # Start import in background
  import_file "$file" &
  pids+=($!)
done

# Wait for all remaining jobs to finish
for pid in "${pids[@]}"; do
  if ! wait "$pid"; then
    overall_success=0
  fi
done

# Summarize discrepancies
if [[ -s "$DISCREPANCY_FILE" ]]; then
  overall_success=0
  echo "===================================================="
  echo "Discrepancies detected during import:"
  echo "The following files failed the row count verification:"
  echo
  while read -r line; do
    echo "- $line"
  done < "$DISCREPANCY_FILE"
  echo "===================================================="
else
  log "No discrepancies detected during import."
  echo "No discrepancies detected during import."
fi

# Log the final status of the import process
if [[ $overall_success -eq 1 ]]; then
  log "DB import completed successfully. The database is fully identical to the data files."
  echo "===================================================="
  echo "DB import completed successfully."
  echo "The database is fully identical to the data files."
  echo "===================================================="
else
  log "The database import process encountered errors and is incomplete. Mirrornode requires a fully synchronized database." "ERROR"
  echo "===================================================="
  echo "The database import process encountered errors and is incomplete."
  echo "Mirrornode requires a fully synchronized database."
  echo "Please review the discrepancies above."
  echo "===================================================="
fi

# Cleanup pid file
rm -f $PID_FILE $LOCK_FILE
