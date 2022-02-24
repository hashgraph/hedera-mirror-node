#!/bin/bash
set -eo pipefail

function run_offline_mode() {
  echo "Running in offline mode"
  exec supervisord --configuration /app/supervisord-offline.conf
}

function run_online_mode() {
  echo "Running in online mode"
  exec supervisord --configuration /app/supervisord.conf
}

function cleanup() {
  /etc/init.d/postgresql stop || true
  cp /app/postgresql.conf "${PGCONF}/conf.d"
  rm -rf "${TMPDIR}"
  cd /app
  echo "Cleanup complete"
}

function init_db() {
  echo "Initializing database"
  if [[ -f "${PGCONF}/PG_VERSION" ]]; then
    echo "Database is already initialzed"
    return
  fi

  mkdir -p "${PGCONF}/conf.d" && cp /app/pg_hba.conf "${PGCONF}" && cp /app/postgresql.conf "${PGCONF}/conf.d"
  chown -R postgres.postgres "${PGCONF}"
  /etc/init.d/postgresql start && /app/scripts/init.sh && /etc/init.d/postgresql stop

  echo "Initialized database"
}

function restore() {
  if [[ -z "${RESTORE}" ]]; then
    echo "Skipping database restore"
    return
  fi

  DATA_DIR="data_dump"
  TMPDIR=$(mktemp -d)
  export PGPASSWORD="${HEDERA_MIRROR_IMPORTER_DB_OWNERPASSWORD:-mirror_node_pass}"

  cp /app/postgresql-restore.conf "${PGCONF}/conf.d/postgresql.conf"
  /etc/init.d/postgresql start

  if (psql -h localhost -d mirror_node -U mirror_node -c 'select count(*) from flyway_schema_history' > /dev/null); then
    echo "Skipping restore since database already contains data"
    cleanup
    return
  fi

  echo "Downloading database backup: ${RESTORE}"
  cd "${TMPDIR}"
  curl --fail -L --retry 3 "${RESTORE}" | tar -xvf -

  if [[ ! -d "${DATA_DIR}" ]]; then
    echo "Database dump does not contain the required '${DATA_DIR}' directory"
    cleanup
    exit 1
  fi

  echo "Restoring from database backup"
  pg_restore -h localhost -U mirror_node --exit-on-error --format=directory --no-owner --no-acl -j 6 -d mirror_node "${DATA_DIR}"

  echo "Restoration complete"
  cleanup
}

function main() {
  if [[ -n "${NETWORK}" ]]; then
    export HEDERA_MIRROR_IMPORTER_NETWORK="${NETWORK}"
    export HEDERA_MIRROR_ROSETTA_NETWORK="${NETWORK}"
  fi

  if [[ -n "${OWNER_PASSWORD}" ]]; then
    export HEDERA_MIRROR_IMPORTER_DB_PASSWORD="${OWNER_PASSWORD}"
  fi

  if [[ -n "${ROSETTA_PASSWORD}" ]]; then
    export HEDERA_MIRROR_ROSETTA_DB_PASSWORD="${ROSETTA_PASSWORD}"
  fi

  case "${MODE}" in
    "offline")
      run_offline_mode
    ;;
    *)
      init_db
      restore
      run_online_mode
    ;;
  esac
}

main
