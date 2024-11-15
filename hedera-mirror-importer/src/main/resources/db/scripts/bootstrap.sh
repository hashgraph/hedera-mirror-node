#!/bin/bash

# Write the script's own PID to bootstrap.pid
echo $$ > bootstrap.pid

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
AVAILABLE_CORES=$(( $(nproc) - 1 ))          # Leave one core free for the local system
DB_AVAILABLE_CORES=$((DB_CPU_CORES - 1))     # Leave one core free for the DB instance

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

  echo "$timestamp - $level - $msg" >> "$LOG_FILE"
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
    echo "Error: Bash version ${REQUIRED_BASH_MAJOR}.${REQUIRED_BASH_MINOR}+ is required. Current version is ${BASH_VERSION}."
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
  find "$IMPORT_DIR" -type f -name "*.csv.gz"
  disable_pipefail
}

# Initialize the database using init.sh
initialize_database() {
  # Declare the bootstrap environment file as a local variable
  local BOOTSTRAP_ENV_FILE="bootstrap.env"

  # Source the bootstrap.env file
  if [[ -f "$BOOTSTRAP_ENV_FILE" ]]; then
    log "Sourcing $BOOTSTRAP_ENV_FILE to set environment variables."
    set -a  # Automatically export all variables
    source "$BOOTSTRAP_ENV_FILE"
    set +a
  else
    log "Error: $BOOTSTRAP_ENV_FILE file not found." "ERROR"
    exit 1
  fi

  # Construct the URL for init.sh
  INIT_SH_URL="https://raw.githubusercontent.com/hashgraph/hedera-mirror-node/refs/heads/main/hedera-mirror-importer/src/main/resources/db/scripts/init.sh"

  # Download init.sh
  log "Downloading init.sh from $INIT_SH_URL"

  enable_pipefail
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
  disable_pipefail

  # Update PostgreSQL environment variables to connect to 'mirror_node' database as 'mirror_node' user
  export PGUSER="mirror_node"
  export PGDATABASE="mirror_node"
  export PGPASSWORD="$OWNER_PASSWORD"

  log "Updated PostgreSQL environment variables to connect to 'mirror_node' database as user 'mirror_node'"

  # Set up the schema in the database
  enable_pipefail
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
  disable_pipefail

  # Verify that tables in manifest exist in the database
  log "Verifying all tables were created correctly during the schema import"
  declare -A manifest_tables

  # Read table names from the manifest
  while IFS=',' read -r filename _; do
    # Skip the header line
    if [[ "$filename" == "filename" ]]; then
      continue
    fi
    # Extract table name and store it in the associative array
    if [[ "$filename" =~ ^([^/]+)(/|$) ]]; then
      table="${BASH_REMATCH[1]}"
      manifest_tables["$table"]=1
    fi
  done < "$MANIFEST_FILE"

  # Check that each table exists in the database
  enable_pipefail
  missing_tables=()
  log "Checking table existence in the database"
  for table in "${!manifest_tables[@]}"; do
    if ! psql -v ON_ERROR_STOP=1 -qt -c "SELECT to_regclass('public.$table');" | grep -q "$table"; then
      missing_tables+=("$table")
      log "Table missing from database: $table" "ERROR"
    else
      log "Table exists in the database: $table"
    fi
  done
  disable_pipefail

  # Handle missing tables
  if [[ ${#missing_tables[@]} -gt 0 ]]; then
    log "The following tables are missing in the database:" "ERROR"
    for table in "${missing_tables[@]}"; do
      log "- $table" "ERROR"
    done
    exit 1
  else
    log "All tables in the manifest exist in the database."
  fi
}

# Import a single file into the database
import_file() {
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
    table=$(basename "$file" .csv.gz)
    filename=$(basename "$file")
  else
    # Large table part
    table=$(basename "$(dirname "$file")")
    filename="$(basename "$file")"
  fi

  # Update status to IN_PROGRESS
  write_tracking_file "$file" "IN_PROGRESS"
  log "Importing table $table from $file"

  enable_pipefail

  # Execute the import within a transaction
  if gunzip -c "$file" | psql -q -v ON_ERROR_STOP=1 --single-transaction -c "COPY $table FROM STDIN WITH CSV HEADER;"; then
    # Verification
    # Get the expected row count from the manifest
    expected_count="${manifest_counts["$filename"]}"

    if [[ -z "$expected_count" || "$expected_count" == "N/A" ]]; then
      log "No expected row count for $filename in manifest, skipping verification."
    else
      # Perform row count verification
      if [[ "$(dirname "$file")" == "$IMPORT_DIR" ]]; then
        # Small table: count total rows
        actual_count=$(psql -qt -c "SELECT COUNT(*) FROM $table;" | xargs)
      else
        # Large table: extract timestamps from filename and count rows in range
        local basename=$(basename "$file" .csv.gz)
        if [[ "$MIRRORNODE_VERSION" == "0.111.0" ]]; then
          # Old method (0.111.0 only)
          if [[ "$basename" =~ ^${table}_part_[0-9]+_([0-9]+)_([0-9]+)$ ]]; then
            local start_ts="${BASH_REMATCH[1]}"
            local end_ts="${BASH_REMATCH[2]}"
            if [[ "$table" == "topic_message" ]]; then
              # Old method: no topic_id condition
              actual_count=$(psql -qt -c "SELECT COUNT(*) FROM $table WHERE consensus_timestamp BETWEEN $start_ts AND $end_ts;" | xargs)
            else
              # Other large tables
              actual_count=$(psql -qt -c "SELECT COUNT(*) FROM $table WHERE consensus_timestamp BETWEEN $start_ts AND $end_ts;" | xargs)
            fi
          elif [[ "$basename" =~ ^${table}_low_vol_topic_ids\.csv\.gz$ ]]; then
            if [[ "$table" == "topic_message" ]]; then
              local atma_topic_id="1693742"
              # Old method: topic_message low-volume single file handling
              actual_count=$(psql -qt -c "SELECT COUNT(*) FROM $table WHERE topic_id <> $atma_topic_id;" | xargs)
            fi
          else
            log "Error parsing timestamps from filename $basename" "ERROR"
            write_tracking_file "$file" "FAILED_TO_IMPORT"
            return 1
          fi
        else
          # New method (0.113.2 or higher)
          if [[ "$basename" =~ ^${table}_part_[0-9]+_([0-9]+)_([0-9]+)(_([0-9]+))?$ ]]; then
            local start_ts="${BASH_REMATCH[1]}"
            local end_ts="${BASH_REMATCH[2]}"
            local topic_id_suffix="${BASH_REMATCH[4]}"

            if [[ "$table" == "topic_message" ]]; then
              if [[ -n "$topic_id_suffix" ]]; then
                # With topic_id suffix
                local topic_id="$topic_id_suffix"
                actual_count=$(psql -qt -c "SELECT COUNT(*) FROM $table WHERE topic_id = $topic_id AND consensus_timestamp BETWEEN $start_ts AND $end_ts;" | xargs)
              else
                # Without topic_id suffix (low volume topic IDs)
                actual_count=$(psql -qt -c "SELECT COUNT(*) FROM $table WHERE consensus_timestamp BETWEEN $start_ts AND $end_ts;" | xargs)
              fi
            else
              # Other large tables
              actual_count=$(psql -qt -c "SELECT COUNT(*) FROM $table WHERE consensus_timestamp BETWEEN $start_ts AND $end_ts;" | xargs)
            fi
          else
            log "Error parsing timestamps from filename $basename" "ERROR"
            write_tracking_file "$file" "FAILED_TO_IMPORT"
            return 1
          fi
        fi
      fi

      # Compare counts
      if [[ "$expected_count" == "$actual_count" ]]; then
        log "Row count verification passed for $filename: expected $expected_count, got $actual_count"
      else
        log "Row count verification failed for $filename: expected $expected_count, got $actual_count" "ERROR"
        # Record discrepancy without duplicates
        discrepancy_entry="$filename: expected $expected_count, got $actual_count"
        if ! grep -Fxq "$discrepancy_entry" "$DISCREPANCY_FILE" 2>/dev/null; then
          echo "$discrepancy_entry" >> "$DISCREPANCY_FILE"
        fi
      fi
    fi

    # Update the status to IMPORTED
    write_tracking_file "$file" "IMPORTED"
    log "Successfully imported $file into $table"
  else
    log "Failed to import $file into $table" "ERROR"
    # Update the status to FAILED_TO_IMPORT
    write_tracking_file "$file" "FAILED_TO_IMPORT"
    return 1
  fi

  disable_pipefail
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
    -h|--help|-H)
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

if [[ ! -f "$MANIFEST_FILE" ]]; then
  echo "Error: Manifest file '$MANIFEST_FILE' not found."
  exit 1
fi

# Populate the manifest array
enable_pipefail
while IFS=',' read -r filename expected_count; do
  # Skip header line
  if [[ "$filename" == "filename" ]]; then
    continue
  fi
  manifest_counts["$filename"]="$expected_count"
done < "$MANIFEST_FILE"
disable_pipefail

# Serialize manifest_counts array for export into the subshells
MANIFEST_COUNTS_SERIALIZED=$(declare -p manifest_counts)

# Grab the compatible mirrornode version
if [[ -f "$MIRRORNODE_VERSION_FILE" ]]; then
  MIRRORNODE_VERSION=$(cat "$MIRRORNODE_VERSION_FILE" | tr -d '[:space:]')
  log "Compatible Mirrornode version: $MIRRORNODE_VERSION"
else
  echo "Error: MIRRORNODE_VERSION file not found in $IMPORT_DIR."
  exit 1
fi

# Trap signals for cleanup
trap 'cleanup' SIGINT SIGTERM

# Log the start of the import process
log "Starting DB import."

# Initialize the database unless the flag file exists
if [[ ! -f "$FLAG_FILE" ]]; then
  initialize_database
  touch "$FLAG_FILE" # Create a flag to skip subsequent runs from running db init after it succeeded once
else
  # Source the bootstrap.env to set OWNER_PASSWORD
  BOOTSTRAP_ENV_FILE="bootstrap.env"
  if [[ -f "$BOOTSTRAP_ENV_FILE" ]]; then
    log "Sourcing $BOOTSTRAP_ENV_FILE to set environment variables."
    set -a
    source "$BOOTSTRAP_ENV_FILE"
    set +a
  else
    log "Error: $BOOTSTRAP_ENV_FILE file not found." "ERROR"
    exit 1
  fi

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
export -f import_file log kill_descendants write_tracking_file read_tracking_status
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
  overall_success=0  # Set overall_success to 0 if discrepancies exist
  log "Discrepancies detected during import:" "ERROR"

  while read -r line; do
    log "$line" "ERROR"
  done < "$DISCREPANCY_FILE"

  echo "===================================================="
  echo "Discrepancies detected during import:"
  echo "The following files failed the row count verification:"
  echo
  while read -r line; do
    echo "$line"
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
  rm -f bootstrap.pid

  exit 1
fi

# Cleanup pid file
rm -f bootstrap.pid