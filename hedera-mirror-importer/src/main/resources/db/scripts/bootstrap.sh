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

####################################
# Functions
####################################

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
  find "$IMPORT_DIR" -type f -name "*.csv.gz"
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

  # Check for MIRRORNODE_VERSION in $IMPORT_DIR
  if [[ -f "$IMPORT_DIR/MIRRORNODE_VERSION" ]]; then
    MIRRORNODE_VERSION=$(cat "$IMPORT_DIR/MIRRORNODE_VERSION")
    log "Found MIRRORNODE_VERSION: $MIRRORNODE_VERSION"
  else
    log "Error: MIRRORNODE_VERSION file not found in $IMPORT_DIR" "ERROR"
    exit 1
  fi

  # Construct the URL for init.sh
  INIT_SH_URL="https://raw.githubusercontent.com/hashgraph/hedera-mirror-node/refs/tags/v$MIRRORNODE_VERSION/hedera-mirror-importer/src/main/resources/db/scripts/init.sh"

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
    if psql -f "$IMPORT_DIR/schema.sql"; then
      log "schema.sql executed successfully"
    else
      log "Error: Failed to execute schema.sql" "ERROR"
      exit 1
    fi
  else
    log "Error: schema.sql not found in $IMPORT_DIR" "ERROR"
    exit 1
  fi
}

# Import a single file into the database
import_file() {
  local file="$1"
  local table

  # Determine the table name
  if [[ "$(dirname "$file")" == "$IMPORT_DIR" ]]; then
    table=$(basename "$file" .csv.gz)
  else
    table=$(basename "$(dirname "$file")")
  fi

  # Update status to IN_PROGRESS
  write_tracking_file "$file" "IN_PROGRESS"
  log "Importing table $table from $file"

  # Execute the import within a transaction
  if gunzip -c "$file" | psql -q -v ON_ERROR_STOP=1 --single-transaction -c "COPY $table FROM STDIN WITH CSV HEADER;"; then
    log "Successfully imported $file into $table"
    # Update the status to IMPORTED
    write_tracking_file "$file" "IMPORTED"
  else
    log "Failed to import $file into $table" "ERROR"
    # Update the status to FAILED_TO_IMPORT
    write_tracking_file "$file" "FAILED_TO_IMPORT"
    return 1
  fi
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
overall_success=0

# Export necessary functions and variables for subshells
export -f import_file log kill_descendants write_tracking_file read_tracking_status
export IMPORT_DIR LOG_FILE TRACKING_FILE LOCK_FILE

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
      overall_success=1
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
    overall_success=1
  fi
done

# Log the final status of the import process
if [[ $overall_success -eq 0 ]]; then
  log "DB import completed successfully."
else
  log "DB import completed with errors" "ERROR"
  exit 1
fi

# After successful completion of all import tasks
rm -f bootstrap.pid