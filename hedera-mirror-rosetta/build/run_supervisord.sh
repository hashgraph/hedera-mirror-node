#!/bin/bash
set -eo pipefail

function run_offline_mode() {
  echo "Running in offline mode"
  supervisord --configuration /app/supervisord-offline.conf
}

function run_online_mode() {
  echo "Running in online mode"
  supervisord --configuration /app/supervisord.conf
}

function cleanup() {
  /etc/init.d/postgresql stop || true
  cp /app/postgresql.conf "${PGCONF}/conf.d"
  rm -rf "${TMPDIR}"
  cd /app
  echo "Cleanup complete"
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

  if (psql -h localhost -d mirror_node -U mirror_node -c 'select count(*) from flyway_schema_history'); then
    echo "Skipping restore since database already contains data"
    cleanup
    return
  fi

  echo "Downloading and restoring from database backup: ${RESTORE}"
  cd "${TMPDIR}"
  curl --fail -L --retry 3 "${RESTORE}" | tar -xvf -

  if [[ ! -d "${DATA_DIR}" ]]; then
    echo "Database dump does not contain the required '${DATA_DIR}' directory"
    cleanup
    exit 1
  fi

  pg_restore -h localhost -U mirror_node --exit-on-error --format=directory --no-owner --no-acl -j 6 -d mirror_node "${DATA_DIR}"

  cleanup
}

function main() {
  if [[ -n "${NETWORK}" ]]; then
    export HEDERA_MIRROR_IMPORTER_NETWORK="${NETWORK}"
    export HEDERA_MIRROR_ROSETTA_NETWORK="${NETWORK}"
  fi


  case "${MODE}" in
    "offline")
      run_offline_mode
    ;;
    *)
      restore
      run_online_mode
    ;;
  esac
}

main
