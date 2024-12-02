#!/usr/bin/env bash

set -euo pipefail

source ./utils.sh

BACKUP_TO_RESTORE=
CLUSTER=
CLUSTER_CONFIG=

# Adjust coordinator pods storage
# - Swap coord-0's PV with the backup's source PV if different. It's needed since the coordinator backup is always
#   restored to coord-0's PV
# - Wipe out all other coordinator pods (they will be replicas when restored) storage so they'll get re-created; can't
#   delete them since any such volume might belong to the primary in the past and thus be the source of an older backup
function adjustCoordStorage() {
  coordPod0="${CLUSTER}-coord-0"
  coordPod0Pvc="${CLUSTER}-coord-data-${coordPod0}"
  coordPod0Pv=$(kubectl get pvc/"${coordPod0Pvc}" -o json | jq -r '.spec.volumeName')
  sgBackupCoord=$(kubectl get sgbackups -o json | \
    jq -r --arg backup "${BACKUP_TO_RESTORE}" --arg sgCluster "${CLUSTER}-coord" \
    '.items[] | select(.metadata.ownerReferences[0].name == $backup and .spec.sgCluster == $sgCluster) | .metadata.name')
  backupSourcePv=$(getSnapshotHandle "${sgBackupCoord}" | cut -d'@' -f1)
  if [[ "${backupSourcePv}" != "${coordPod0Pv}" ]]; then
    log "Coordinator backup's source PV is different from the current ${coordPod0}'s PV, swap them"
    backupSourcePvc=$(kubectl get pvc -l 'app=StackGresCluster,citus-group=0' -o json | \
      jq --arg volumeName "${backupSourcePv}" -r '.items[] | select(.spec.volumeName == $volumeName) | .metadata.name')
    swapPv "${coordPod0Pvc}" "${backupSourcePvc}"
  fi

  for pvc in $(kubectl get pvc -l 'app=StackGresCluster,citus-group=0' --output=custom-columns=':.metadata.name' --no-headers); do
    if [[ "${pvc}" =~ ^.*-coord-0$ ]]; then
      continue
    fi

    log "Wiping out ${pvc} data"
    cat <<EOF | kubectl apply -f -
apiVersion: batch/v1
kind: Job
metadata:
  name: coord-data-wiper
spec:
  backoffLimit: 0
  template:
    spec:
      containers:
      - name: debian
        image: debian:bookworm-slim
        command:
        - "/bin/bash"
        - "-ecx"
        - rm -fr /pgdata/*
        volumeMounts:
        - mountPath: /pgdata
          name: pgdata
      nodeSelector:
        citus-role: coordinator
        csi-type: zfs
      restartPolicy: Never
      tolerations:
      - effect: NoSchedule
        key: zfs
        operator: Equal
        value: "true"
      volumes:
      - name: pgdata
        persistentVolumeClaim:
          claimName: ${pvc}
EOF
  kubectl wait --for=condition=complete job/coord-data-wiper --timeout=-1s
  kubectl delete job/coord-data-wiper
  done
}

function checkCoordinatorReplica() {
  EXPECTED_STATE="running,streaming"
  TIMEOUT_SECONDS=300

  log "Waiting for coordinator replica to become streaming"
  primary=$(kubectl get pods -l 'app=StackGresCluster,citus-group=0,role=master' -o name)

  deadline=$((SECONDS + TIMEOUT_SECONDS))
  while [[ "$SECONDS" -lt "${deadline}" ]]; do
    state=$(kubectl exec "${primary}" -c patroni -- patronictl list --group 0 --format json | \
      jq -r '[.[].State] | sort | join(",")')
    if [[ "${state}" == "${EXPECTED_STATE}" ]]; then
      log "Coordinator replica has entered streaming state"
      return
    fi

    log "Coordinator group state is ${state}. Waiting for it to become ${EXPECTED_STATE}"
    sleep 5
  done

  log "Coordinator replica does not enter streaming state in ${TIMEOUT_SECONDS} seconds. Exiting"
  exit 1
}

function createSGShardedClusterConfigWithRestore() {
  sourceConfig=$(kubectl get sgshardedclusters "${CLUSTER}" -o json | \
    jq 'del(.metadata.creationTimestamp,.metadata.generation,.metadata.resourceVersion,.metadata.uid,.spec.configurations.backups,.status)')
  initialDataConfig=$(cat <<EOF
{
  "restore": {
    "fromBackup": {
      "name": "${BACKUP_TO_RESTORE}"
    },
    "downloadDiskConcurrency": 1
  }
}
EOF)
  CLUSTER_CONFIG=$(echo "${sourceConfig}" | \
    jq --argjson initialDataConfig "${initialDataConfig}" '.spec.initialData=$initialDataConfig')
  log "Created SGShardedCluster configuration to restore backup ${BACKUP_TO_RESTORE}"
}

function deleteCluster() {
  kubectl delete sgshardedclusters "${CLUSTER}"
  log "Deleted SGShardedCluster \"${CLUSTER}\", waiting for all pods to terminate"
  kubectl wait --for=delete pods -l 'app=StackGresCluster' --timeout=-1s
  log "All pods terminated"
}

function ensureNoBackupRunning() {
  count=$(kubectl get sgshardedbackups -o yaml | yq '[.items[] | select(.status.process.status == "Running")] | length')
  if [[ "$count" -gt 0 ]]; then
    log "There is a backup running. Exiting"
    exit 1
  fi
}

function findShardedCluster() {
  clusters=($(kubectl get sgshardedclusters --output=custom-columns=':.metadata.name' --no-headers))
  if [[ ${#clusters[@]} -eq 0 ]]; then
    log "No SGShardedClusters found. Exiting"
    exit 1
  elif [[ ${#clusters[@]} -gt 1 ]]; then
    log "More than one SGShardedClusters found. Exiting"
    exit 1
  fi

  CLUSTER="${clusters[0]}"
  log "Will restore backup to SGShardedCluster \"${CLUSTER}\""
  doContinue
}

function fixClusterAuth() {
  local sgPasswords=$(kubectl get secret "${CLUSTER}" -o json |
    ksd |
    jq -r '.stringData')
  local superuserUsername=$(echo "${sgPasswords}" | jq -r '.["superuser-username"]')
  local superuserPassword=$(echo "${sgPasswords}" | jq -r '.["superuser-password"]')
  local replicationUsername=$(echo "${sgPasswords}" | jq -r '.["replication-username"]')
  local replicationPassword=$(echo "${sgPasswords}" | jq -r '.["replication-password"]')
  local authenticatorUsername=$(echo "${sgPasswords}" | jq -r '.["authenticator-username"]')
  local authenticatorPassword=$(echo "${sgPasswords}" | jq -r '.["authenticator-password"]')

  # Mirror Node Passwords
  local mirrorNodePasswords=$(kubectl get secret "${HELM_RELEASE_NAME}-passwords" -o json |
    ksd |
    jq -r '.stringData')
  local graphqlUsername=$(echo "${mirrorNodePasswords}" | jq -r '.HEDERA_MIRROR_GRAPHQL_DB_USERNAME')
  local graphqlPassword=$(echo "${mirrorNodePasswords}" | jq -r '.HEDERA_MIRROR_GRAPHQL_DB_PASSWORD')
  local grpcUsername=$(echo "${mirrorNodePasswords}" | jq -r '.HEDERA_MIRROR_GRPC_DB_USERNAME')
  local grpcPassword=$(echo "${mirrorNodePasswords}" | jq -r '.HEDERA_MIRROR_GRPC_DB_PASSWORD')
  local importerUsername=$(echo "${mirrorNodePasswords}" | jq -r '.HEDERA_MIRROR_IMPORTER_DB_USERNAME')
  local importerPassword=$(echo "${mirrorNodePasswords}" | jq -r '.HEDERA_MIRROR_IMPORTER_DB_PASSWORD')
  local ownerUsername=$(echo "${mirrorNodePasswords}" | jq -r '.HEDERA_MIRROR_IMPORTER_DB_OWNER')
  local ownerPassword=$(echo "${mirrorNodePasswords}" | jq -r '.HEDERA_MIRROR_IMPORTER_DB_OWNERPASSWORD')
  local restUsername=$(echo "${mirrorNodePasswords}" | jq -r '.HEDERA_MIRROR_REST_DB_USERNAME')
  local restPassword=$(echo "${mirrorNodePasswords}" | jq -r '.HEDERA_MIRROR_REST_DB_PASSWORD')
  local restJavaUsername=$(echo "${mirrorNodePasswords}" | jq -r '.HEDERA_MIRROR_RESTJAVA_DB_USERNAME')
  local restJavaPassword=$(echo "${mirrorNodePasswords}" | jq -r '.HEDERA_MIRROR_RESTJAVA_DB_PASSWORD')
  local rosettaUsername=$(echo "${mirrorNodePasswords}" | jq -r '.HEDERA_MIRROR_ROSETTA_DB_USERNAME')
  local rosettaPassword=$(echo "${mirrorNodePasswords}" | jq -r '.HEDERA_MIRROR_ROSETTA_DB_PASSWORD')
  local web3Username=$(echo "${mirrorNodePasswords}" | jq -r '.HEDERA_MIRROR_WEB3_DB_USERNAME')
  local web3Password=$(echo "${mirrorNodePasswords}" | jq -r '.HEDERA_MIRROR_WEB3_DB_PASSWORD')
  local dbName=$(echo "${mirrorNodePasswords}" | jq -r '.HEDERA_MIRROR_IMPORTER_DB_NAME')
  local sql=$(cat <<EOF
alter user ${superuserUsername} with password '${superuserPassword}';
alter user ${graphqlUsername} with password '${graphqlPassword}';
alter user ${grpcUsername} with password '${grpcPassword}';
alter user ${importerUsername} with password '${importerPassword}';
alter user ${ownerUsername} with password '${ownerPassword}';
alter user ${restUsername} with password '${restPassword}';
alter user ${restJavaUsername} with password '${restJavaPassword}';
alter user ${rosettaUsername} with password '${rosettaPassword}';
alter user ${web3Username} with password '${web3Password}';
alter user ${replicationUsername} with password '${replicationPassword}';
alter user ${authenticatorUsername} with password '${authenticatorPassword}';

\c ${dbName}
insert into pg_dist_authinfo(nodeid, rolename, authinfo)
  values (0, '${superuserUsername}', 'password=${superuserPassword}'),
         (0, '${graphqlUsername}', 'password=${graphqlPassword}'),
         (0, '${grpcUsername}', 'password=${grpcPassword}'),
         (0, '${importerUsername}', 'password=${importerPassword}'),
         (0, '${ownerUsername}', 'password=${ownerPassword}'),
         (0, '${restUsername}', 'password=${restPassword}'),
         (0, '${restJavaUsername}', 'password=${restJavaPassword}'),
         (0, '${rosettaUsername}', 'password=${rosettaPassword}'),
         (0, '${web3Username}', 'password=${web3Password}') on conflict (nodeid, rolename)
  do
      update set authinfo = excluded.authinfo;
EOF)

  log "Fixing passwords and pg_dist_authinfo for all pods in the cluster"
  for pod in $(kubectl get pods -l 'app=StackGresCluster,role=master' -o name); do
    log "Updating passwords and pg_dist_authinfo for ${pod}"
    echo "$sql" | kubectl exec -i "${pod}" -c postgres-util -- psql -U "${superuserUsername}" -f -
  done

  checkCitusMetadataSyncStatus "${CURRENT_NAMESPACE}"
}

function getSnapshotHandle() {
  local sgBackup=$1

  volumeSnapshot=$(kubectl get sgbackups "${sgBackup}" -o json | jq -r '.status.volumeSnapshot.name')
  volumeSnapshotContent=$(kubectl get volumesnapshots "${volumeSnapshot}" -o json | \
    jq -r '.status.boundVolumeSnapshotContentName')
  kubectl get volumesnapshotcontents "${volumeSnapshotContent}" -o json | jq -r '.status.snapshotHandle'
}

function pickShardedBackup() {
  allBackups=($(kubectl get sgshardedbackups -o yaml | \
    yq '.items[] | select(.status.process.status == "Completed") | .status.process.timing.start + "/" + .metadata.name' | sort -r))
  if [[ ${#allBackups[@]} -eq 0 ]]; then
    log "No completed backups found. Exiting"
    exit 0
  elif [[ ${#allBackups[@]} -eq 1 ]]; then
    log "Only one SGShardedBackup available"
    choice="${allBackups[0]}"
  else
    # More than one backups
    index=1
    echo "Available backups ordered from most recent to oldest:"
    for backup in "${allBackups[@]}"; do
      IFS='/' read -r startDate name <<< "${backup}"
      echo "  ${index}. ${name} - created at ${startDate}"
      index=$((index + 1))
    done

    read -p "Enter the number of the backup to restore: " backupIndex
    [[ -z "${backupIndex}" || "${backupIndex}" -lt 1 || "${backupIndex}" -gt ${#allBackups[@]} ]] && \
      echo "Invalid choice" && exit 1 || true

    if [[ "${backupIndex}" -gt 1 ]]; then
      echo "WARNING!!! You are about to restore an older backup, all later backups have to be removed before proceeding"
      doContinue
      count=$((backupIndex-1))
      kubectl delete sgshardedbackups $(echo "${allBackups[@]:0:${count}}" | sed 's/[:TZ0-9\-]\+\///g')
      log "Deleted ${count} most recent SGShardedBackups"
    fi

    choice="${allBackups[$((backupIndex-1))]}"
  fi

  BACKUP_TO_RESTORE=$(echo "${choice}" | cut -d'/' -f2)
  status=$(kubectl get sgshardedbackups "${BACKUP_TO_RESTORE}" -o yaml | yq '.status')
  log "Will restore SGShardedBackup ${BACKUP_TO_RESTORE} with status ${status}"
  doContinue
}

function prepare() {
  findShardedCluster
  ensureNoBackupRunning
  pickShardedBackup
  createSGShardedClusterConfigWithRestore
  preserveResources
  unrouteTraffic "${CURRENT_NAMESPACE}"
  deleteCluster
}

function preserveResources() {
  for pvc in $(kubectl get pvc -l 'app=StackGresCluster' -o name); do
    kubectl patch "${pvc}" --type=json -p='[{"op": "remove", "path": "/metadata/ownerReferences"}]' || true
  done

  log "Removed ownerReferences from all stackgres PVCs to avoid being garbage collected"
}

function recreateManagedCluster() {
  if kubectl get helmrelease -n "${HELM_RELEASE_NAME}" > /dev/null 2>&1; then
    log "Resuming helmrelease to recreate managed SGShardedCluster"
    flux resume helmrelease -n "${CURRENT_NAMESPACE}" "${HELM_RELEASE_NAME}" --timeout 30m
  fi

  waitForStackGresClusterPods
  fixClusterAuth
  routeTraffic "${CURRENT_NAMESPACE}"
  log "SGShardedCluster ${CLUSTER} is ready"
}

function restoreBackup() {
  log "Restoring..."

  adjustCoordStorage
  rollbackZfsVolumes

  log "Creating SGShardedCluster with the restore configuration"
  echo "${CLUSTER_CONFIG}" | kubectl apply -f -

  waitForStackGresClusterPods
  fixClusterAuth
  checkCoordinatorReplica

  # Once again remove ownerReferences since in restore they will get updated with new owners
  preserveResources
  deleteCluster

  log "Backup ${BACKUP_TO_RESTORE} restored successfully"
}

function rollbackZfsVolumes() {
  # ZFS node id to GKE node map
  nodeIdMap=$(kubectl_common get zfsnodes -o json | \
    jq '[.items[].metadata] | map({(.name): .ownerReferences[0].name}) | add')
  # GKE node to ZFS daemonset pod map
  nodeZfsPodMap=$(kubectl_common get pods -l 'name=openebs-zfs-node' -o json | \
    jq '.items | map({(.spec.nodeName): .metadata.name}) | add')
  nodeIdZfsPodMap=$(echo "${nodeIdMap}" | \
    jq --argjson nodeZfsPodMap "${nodeZfsPodMap}" 'to_entries | map({(.key): $nodeZfsPodMap[.value]}) | add')

  for sgBackup in $(kubectl get sgshardedbackups "${BACKUP_TO_RESTORE}" -o json | jq -r '.status.sgBackups[]'); do
    snapshotHandle=$(getSnapshotHandle "${sgBackup}")
    zfsSnapshot=$(echo "${snapshotHandle}" | cut -d'@' -f2)
    IFS=' ' read -r nodeId poolName <<< \
      "$(kubectl_common get zfssnapshots "${zfsSnapshot}" -o json | jq -r '.spec | .ownerNodeID + " " + .poolName')"
    zfsPod=$(echo "${nodeIdZfsPodMap}" | jq -r --arg nodeId "${nodeId}" '.[$nodeId]')
    fullSnapshotHandle="${poolName}/${snapshotHandle}"

    log "Rolling back ZFS volume to snapshot ${fullSnapshotHandle}"
    kubectl_common exec -c openebs-zfs-plugin "${zfsPod}" -- zfs rollback -r "${fullSnapshotHandle}"
  done

  log "Rolled back all ZFS volumes to the snapshots created by the backup ${BACKUP_TO_RESTORE}"
}

function swapPv() {
  local pvcs=($1 $2)
  volumeNames=($(kubectl get pvc "${pvcs[@]}" -o json | jq -r '.items[].spec.volumeName'))
  firstPvcConfig=$(kubectl get pvc "${pvcs[0]}" -o json | \
    jq --arg volumeName "${volumeNames[1]}" \
      'del(.metadata.annotations["volume.kubernetes.io/selected-node"],.metadata.creationTimestamp,.metadata.resourceVersion,.metadata.uid,.status)
      | .spec.volumeName = $volumeName')
  secondPvcConfig=$(kubectl get pvc "${pvcs[1]}" -o json | \
      jq --arg volumeName "${volumeNames[0]}" \
        'del(.metadata.annotations["volume.kubernetes.io/selected-node"],.metadata.creationTimestamp,.metadata.resourceVersion,.metadata.uid,.status)
        | .spec.volumeName = $volumeName')

  log "Removing coordinator PVCs"
  kubectl delete pvc "${pvcs[@]}"

  log "Removing claimRef from coordinator PVs"
  for volumeName in "${volumeNames[@]}"; do
    kubectl patch pv/"${volumeName}" --type=json -p='[{"op": "remove", "path": "/spec/claimRef"}]' || true
  done

  log "Recreating coordinator PVCs to swap the volumes"
  echo "$firstPvcConfig" | kubectl apply -f -
  echo "$secondPvcConfig" | kubectl apply -f -
  kubectl wait --for=jsonpath='{.status.phase}'=Bound pvc "${pvcs[@]}" --timeout=-1s
}

function waitForStackGresClusterPods() {
  log "Waiting for all StackGresCluster StatefulSets to be created"
  while ! kubectl describe sgshardedclusters "${CLUSTER}" >/dev/null 2>&1; do
    sleep 1
  done

  expectedTotal=$(($(kubectl get sgshardedclusters "${CLUSTER}" -o jsonpath='{.spec.shards.clusters}')+1))
  while [[ "$(kubectl get sts -l 'app=StackGresCluster' -o name | wc -l)" -ne "${expectedTotal}" ]]; do
    sleep 1
  done

  log "Waiting for all StackGresCluster pods to be ready"
  for sts in $(kubectl get sts -l 'app=StackGresCluster' -o name); do
    expected=$(kubectl get "${sts}" -o jsonpath='{.spec.replicas}')
    kubectl wait --for=jsonpath='{.status.readyReplicas}'=${expected} "${sts}" --timeout=-1s
  done
}

CURRENT_NAMESPACE=$(kubectl config view --minify --output 'jsonpath={..namespace}')

prepare
restoreBackup
recreateManagedCluster
