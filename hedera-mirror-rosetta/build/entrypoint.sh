#!/bin/bash
set -eo pipefail

function config_data_retention() {
  echo "Configuring data retention"
  export HEDERA_MIRROR_IMPORTER_RETENTION_BATCHPERIOD="${DATA_RETENTION_BATCHPERIOD:-1d}"
  export HEDERA_MIRROR_IMPORTER_RETENTION_ENABLED="${DATA_RETENTION_ENABLED:-false}"
  export HEDERA_MIRROR_IMPORTER_RETENTION_FREQUENCY="${DATA_RETENTION_FREQUENCY:-7d}"
  export HEDERA_MIRROR_IMPORTER_RETENTION_PERIOD="${DATA_RETENTION_PERIOD:-90d}"
}

function config_entity_balance_tracking() {
  echo "Configuring entity balance tracking"
  export HEDERA_MIRROR_IMPORTER_MIGRATION_INITIALIZEENTITYBALANCEMIGRATION_ENABLED="${INITIALIZEENTITYBALANCEMIGRATION_ENABLED:-false}"
  export HEDERA_MIRROR_IMPORTER_PARSER_RECORD_ENTITY_PERSIST_TRACKBALANCE="${ENTITY_PERSIST_TRACKBALANCE:-false}"
}

function run_offline_mode() {
  echo "Running in offline mode"
  exec supervisord --configuration /app/supervisord-offline.conf
}

function run_online_mode() {
  echo "Running in online mode"
  config_data_retention
  config_entity_balance_tracking
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

  PG_CLUSTER_CONF=/etc/postgresql/${PG_VERSION}/${PG_CLUSTER_NAME}
  if [[ -f "${PGDATA}/PG_VERSION" ]]; then
    # relink the config dir just in case it's a newly created container with existing mapped /data dir
    ln -sTf ${PGDATA} ${PG_CLUSTER_CONF}
    echo "Database is already initialized"
    return
  fi

  echo "Creating cluster '${PG_CLUSTER_NAME}' with data dir '${PGDATA}'"
  mkdir -p "${PGDATA}" && chown -R postgres:postgres "${PGDATA}"
  su postgres -c "pg_createcluster -d ${PGDATA} --start-conf auto ${PG_VERSION} ${PG_CLUSTER_NAME}"
  # mv conf to $PGDATA and link it
  cp -pr ${PG_CLUSTER_CONF}/* ${PGDATA}
  rm -fr ${PG_CLUSTER_CONF}
  ln -s ${PGDATA} ${PG_CLUSTER_CONF}
  su postgres -c 'cp /app/pg_hba.conf ${PGDATA} && cp /app/postgresql.conf ${PGDATA}/conf.d'

  /etc/init.d/postgresql start
  su postgres -c 'PATH=/usr/lib/postgresql/${PG_VERSION}/bin:${PATH} /app/scripts/init.sh'
  /etc/init.d/postgresql stop

  echo "Initialized database"
}

function restore() {
  if [[ -z "${RESTORE}" ]]; then
    echo "Skipping database restore"
    return
  fi

  DATA_DIR="data_dump"
  TMPDIR=$(mktemp -d)
  export PGPASSWORD="${HEDERA_MIRROR_IMPORTER_DB_PASSWORD:-mirror_node_pass}"

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
