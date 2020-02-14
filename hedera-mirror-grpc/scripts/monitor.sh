#!/bin/bash

PERIOD_SECONDS=${PERIOD_SECONDS:-5}
MAX_QUERY_AGE_SECONDS=${MAX_AGE_SECONDS:-15}
echo "INFO Started. polling period ${PERIOD_SECONDS}s. queries locking topic_message older than ${MAX_QUERY_AGE_SECONDS}s will be killed."

while true
do
  bounce_service=false
  for pid in `psql -h "${DBHOST:-dbhost}" -d "${DBNAME:-mirror_node}" -U "${DBUSER:-mirror_grpc}" -tc "select pl.pid from pg_locks pl join pg_stat_activity pa on pa.pid = pl.pid join pg_class pc on pl.relation = pc.OID where pa.query like 'SELECT%' and pc.relname = 'topic_message' and now() - query_start > '${MAX_QUERY_AGE_SECONDS} seconds';"`
  do
    case $pid in
      ''|*[!0-9]*) echo "ERROR SQL query results did not appear to return PIDs." ;;
      *) res=$(psql -h "${DBHOST:-dbhost}" -d "${DBNAME:-mirror_node}" -U "${DBUSER:-mirror_grpc}" -tc "select pg_terminate_backend($pid);" | head -1)
        rc=$?
        if [ $rc -ne 0 ] || [ "$res" != " t" ] ; then
          echo "ERROR SQL query failed attempting to kill pid $pid."
        else
          echo "INFO Terminated postgres pid $pid."
        fi
        bounce_service=true
        ;;
    esac
  done
  if [ "${bounce_service}" == "true" ] ; then
    echo "INFO Restarting hedera-mirror-grpc."
    systemctl restart hedera-mirror-grpc
  fi
  sleep ${PERIOD_SECONDS}
done
