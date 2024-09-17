#!/bin/bash

# Enable job control
set -m

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

# Parse options
if [[ $# -eq 0 ]]; then
  echo "No arguments provided. Use --help or -h for usage information."
  exit 1
fi

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
if [[ -z "$1" || -z "$2" ]]; then
  echo "Error: Both DB_CPU_CORES and IMPORT_DIR must be provided."
  echo "Use --help or -h for usage information."
  exit 1
fi

DB_CPU_CORES="$1"
IMPORT_DIR="$2"

# Convert IMPORT_DIR to an absolute path
IMPORT_DIR="$(realpath "$IMPORT_DIR")"

# Check if IMPORT_DIR exists and is a directory
if [[ ! -d "$IMPORT_DIR" ]]; then
  echo "Error: IMPORT_DIR '$IMPORT_DIR' does not exist or is not a directory."
  exit 1
fi

AVAILABLE_CORES=$(( $(nproc) - 1 ))  # Leave one core free for the local system
DB_AVAILABLE_CORES=$((DB_CPU_CORES - 1))  # Leave one core free for the DB instance

if [[ $AVAILABLE_CORES -lt $DB_AVAILABLE_CORES ]]; then
  DB_AVAILABLE_CORES=$AVAILABLE_CORES
fi

max_jobs="$DB_AVAILABLE_CORES"

# Set PostgreSQL environment variables
export PGUSER=${PGUSER:-"DB_OWNER"}
export PGPASSWORD=${PGPASSWORD:-"DB_PASSWORD"}
export PGHOST=${PGHOST:-"DB_ADDRESS"}
export PGDATABASE=${PGDATABASE:-"DB_NAME"}

LOG_FILE="import.log"
TRACKING_FILE="import_tracking.txt"
LOCK_FILE="import_tracking.lock"

# Check if required tools are installed
REQUIRED_TOOLS=("psql" "gunzip" "realpath" "flock")
for tool in "${REQUIRED_TOOLS[@]}"; do
  if ! command -v "$tool" &> /dev/null; then
    echo "Error: $tool is not installed. Please install it to continue."
    exit 1
  fi
done

# Log using UTC times
log() {
  local msg="$1"
  local level="${2:-INFO}"
  local timestamp
  timestamp=$(date -u '+%Y-%m-%d %H:%M:%S')

  echo "$timestamp - $level - $msg" | tee -a "$LOG_FILE"
}

# Function to kill a process and its descendants
kill_descendants() {
  local pid="$1"
  local children
  children=$(pgrep -P "$pid")
  for child in $children; do
    kill_descendants "$child"
  done
  kill -TERM "$pid" 2>/dev/null
}

# Function to handle script termination
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

# Trap signals
trap 'cleanup' SIGINT SIGTERM

# Function to safely write to tracking file with lock
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

# Function to read status from tracking file
read_tracking_status() {
  local file="$1"
  grep "^$file " "$TRACKING_FILE" 2>/dev/null | awk '{print $2}'
}

# Function to collect all import tasks
collect_import_tasks() {
  find "$IMPORT_DIR" -type f -name "*.csv.gz"
}

# Main script execution
log "Starting DB import."

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

# Initialize variables
pids=()
overall_success=0

# Export necessary functions and variables
export -f import_file log kill_descendants write_tracking_file read_tracking_status
export IMPORT_DIR LOG_FILE TRACKING_FILE LOCK_FILE PGUSER PGPASSWORD PGHOST PGDATABASE

# Function to import a single file
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

  if {
    echo "BEGIN;"
    echo "\\copy $table FROM STDIN WITH CSV HEADER;"
    gunzip -c "$file"
    echo "\."
    echo "COMMIT;"
  } | psql -q -v ON_ERROR_STOP=1; then
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

if [[ $overall_success -eq 0 ]]; then
  log "DB import completed successfully."
else
  log "DB import completed with errors" "ERROR"
  exit 1
fi