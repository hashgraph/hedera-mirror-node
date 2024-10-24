#!/usr/bin/env bash

GCP_PROJECT="${GCP_PROJECT}"
SNAPSHOT_ID="${SNAPSHOT_ID}"
GCP_K8S_CLUSTER_NAME="${GCP_K8S_CLUSTER_NAME}"
GCP_K8S_CLUSTER_REGION="${GCP_K8S_CLUSTER_REGION}"
GCP_COORDINATOR_POOL_NAME="${GCP_COORDINATOR_POOL_NAME:-citus-coordinator}"
GCP_WORKER_POOL_NAME="${GCP_WORKER_POOL_NAME:-citus-worker}"
GCP_SNAPSHOT_PROJECT="${GCP_SNAPSHOT_PROJECT:-$GCP_PROJECT}"
HELM_RELEASE_NAME="${HELM_RELEASE_NAME:-mirror}"
COMMON_NAMESPACE="${COMMON_NAMESPACE:-common}"
REPLACE_DISKS="${REPLACE_DISKS:-true}"
ZFS_POOL_NAME="${ZFS_POOL_NAME:-zfspv-pool}"

function doContinue() {
  read -p "Continue? (Y/N): " confirm && [[ $confirm == [yY] || $confirm == [yY][eE][sS] ]] || exit 1
}

function configureAndValidate() {
  CURRENT_CONTEXT=$(kubectl config current-context)

  if [[ -z "${GCP_PROJECT}" ]]; then
    echo "GCP_PROJECT is not set and is required. Exiting"
    exit 1
  fi

  if [[ -z "${GCP_K8S_CLUSTER_NAME}" ]]; then
    echo "GCP_K8S_CLUSTER_NAME is not set and is required. Exiting"
    exit 1
  fi

  if [[ -z "${GCP_K8S_CLUSTER_REGION}" ]]; then
    echo "GCP_K8S_CLUSTER_REGION is not set and is required. Exiting"
    exit 1
  fi

  local clusterFound=$(gcloud container clusters list --project "${GCP_PROJECT}" --filter="name=${GCP_K8S_CLUSTER_NAME}" --format="json(name)" --region="${GCP_K8S_CLUSTER_REGION}"|jq -r 'length == 1')
  if [[ "${clusterFound}" == "false" ]]; then
    echo "Cluster ${GCP_K8S_CLUSTER_NAME} not found in project ${GCP_PROJECT} and region ${GCP_K8S_CLUSTER_REGION}. Exiting"
    exit 1
  fi

  if [[ -z "${SNAPSHOT_ID}" ]]; then
    echo "SNAPSHOT_ID is not set and is required. Please provide an identifier that is unique across all snapshots. Exiting"
    gcloud compute snapshots list --project "${GCP_SNAPSHOT_PROJECT}" --format="table(name, diskSizeGb, sourceDisk, description)"
    exit 1
  fi

  DISK_PREFIX=$(helm get values "${HELM_RELEASE_NAME}" -n "${COMMON_NAMESPACE}" -o json | jq -r '.zfs.init.diskPrefix')
  if [[ -z "${DISK_PREFIX}" ]]; then
    echo "Unable to find disk prefix. Please check hedera-mirror-common for release ${HELM_RELEASE_NAME} configuration and rerun"
    exit 1
  fi

  if [[ -z "${ZFS_POOL_NAME}" ]]; then
    echo "Unable to find zfs pool name. set ZFS_POOL_NAME to value of zfs.paramaters.poolname in common values.yaml"
    exit 1
  fi

  SNAPSHOTS_TO_RESTORE=$(gcloud compute snapshots list --project "${GCP_SNAPSHOT_PROJECT}" \
    --filter="name~${SNAPSHOT_ID}" \
    --format="json(name, description)" |
    jq -r 'map({name: .name, description: (.description|fromjson|sort_by(.volumeName))})')
  if [[ -z "${SNAPSHOTS_TO_RESTORE}" ]]; then
    echo "No snapshots found for snapshot id ${SNAPSHOT_ID} in project ${GCP_SNAPSHOT_PROJECT}. Exiting"
    exit 1
  fi

  CITUS_CLUSTERS=$(kubectl get sgclusters.stackgres.io -A -o json |
    jq -r '.items|
           map(
             .metadata as $metadata|
             .spec.postgres.version as $pgVersion|
             ((.metadata.labels["stackgres.io/coordinator"] // "false")| test("true")) as $isCoordinator |
             .spec.configurations.patroni.initialConfig.citus.group as $citusGroup|
             .status.podStatuses[]|
               {
                 citusGroup: $citusGroup,
                 clusterName: $metadata.name,
                 isCoordinator: $isCoordinator,
                 namespace: $metadata.namespace,
                 pgVersion: $pgVersion,
                 podName: .name,
                 pvcName: "\($metadata.name)-data-\(.name)",
                 primary: .primary,
                 shardedClusterName: $metadata.ownerReferences[0].name
               }
           )')

  ZFS_VOLUMES=$(kubectl get pv -o json |
    jq -r --arg CITUS_CLUSTERS "${CITUS_CLUSTERS}" \
      '.items|
       map(select(.metadata.annotations."pv.kubernetes.io/provisioned-by"=="zfs.csi.openebs.io" and
                  .status.phase == "Bound")|
          (.spec.claimRef.name) as $pvcName |
          (.spec.claimRef.namespace) as $pvcNamespace |
          {
            namespace: ($pvcNamespace),
            volumeName: (.metadata.name),
            pvcName: ($pvcName),
            pvcSize: (.spec.capacity.storage),
            nodeId: (.spec.nodeAffinity.required.nodeSelectorTerms[0].matchExpressions[0].values[0]),
            citusCluster: ($CITUS_CLUSTERS | fromjson | map(select(.pvcName == $pvcName and
                                                            .namespace == $pvcNamespace))|first)
          }
      )')

  NAMESPACES=($(echo $ZFS_VOLUMES | jq -r '.[].namespace' | tr ' ' '\n' | sort -u | tr '\n' ' '))

  NODE_ID_MAP=$(echo -e "${SNAPSHOTS_TO_RESTORE}\n${ZFS_VOLUMES}" |
    jq -s '.[0] as $snapshots |
           .[1] as $volumes |
           $volumes | group_by(.nodeId) |
           map((.[0].nodeId) as $nodeId |
             map(.)|sort_by(.volumeName) as $pvcs |
             $pvcs |
             map(
               {
                 pvcName: .pvcName,
                 namespace: .namespace
               }
             ) as $pvcMatchData|
             {
               ($nodeId) :  {
                  pvcs: ($pvcs),
                  snapshot: ($snapshots | map(select(.description|contains($pvcMatchData))))
               }
             }
          )|add')

  UNIQUE_NODE_IDS=($(echo "${NODE_ID_MAP}" | jq -r 'keys[]'))
  for nodeId in "${UNIQUE_NODE_IDS[@]}"; do
    local nodeInfo=$(echo "${NODE_ID_MAP}" | jq -r --arg NODE_ID "${nodeId}" '.[$NODE_ID]')
    HAS_VALID_SNAPSHOT=$(echo "${nodeInfo}" | jq -r '.snapshot|length == 1')
    if [[ "${HAS_VALID_SNAPSHOT}" == "false" ]]; then
      echo -e "Unable to find valid snapshot for node id ${nodeId} in snapshot id ${SNAPSHOT_ID}.
                  Please verify snapshots contain same namespace, pvc name and postgres version.
                  ${nodeInfo}"
      exit 1
    else
      echo "Snapshot contains all pvcs for node ${nodeId}"
    fi
  done
}

function scaleDeployment() {
  local namespace="${1}"
  local replicas="${2}"
  local deploymentLabel="${3}"

  if [[ "${replicas}" -gt 0 ]]; then # scale up
    kubectl scale deployment -n "${namespace}" -l "${deploymentLabel}" --replicas="${replicas}"
    echo "Waiting for pods with label ${deploymentLabel} to be ready"
    kubectl wait --for=condition=Ready pod -n "${namespace}" -l "${deploymentLabel}" --timeout=-1s
  else # scale down
    local deploymentPods=$(kubectl get pods -n "${namespace}" -l "${deploymentLabel}" -o 'jsonpath={.items[*].metadata.name}')
    if [[ -z "${deploymentPods}" ]]; then
      echo "No pods found for deployment ${deploymentLabel} in namespace ${namespace}"
      return
    else
      echo "Removing pods ${deploymentPods} in ${namespace} for ${CURRENT_CONTEXT}"
      doContinue
      kubectl scale deployment -n "${namespace}" -l "${deploymentLabel}" --replicas="${replicas}"
      echo "Waiting for pods with label ${deploymentLabel} to be deleted"
      kubectl wait --for=delete pod -n "${namespace}" -l "${deploymentLabel}" --timeout=-1s
    fi
  fi
}

function pauseCitus() {
  local namespace="${1}"
  local citusPods=$(kubectl get pods -n "${namespace}" -l 'stackgres.io/cluster=true' -o 'jsonpath={.items[*].metadata.name}')
  if [[ -z "${citusPods}" ]]; then
    echo "Citus is not currently running"
  else
    echo "Removing pods (${citusPods}) in ${namespace} for ${CURRENT_CONTEXT}"
    doContinue
    kubectl annotate sgclusters.stackgres.io -n "${namespace}" --all stackgres.io/reconciliation-pause="true" --overwrite
    kubectl scale sts -n "${namespace}" -l 'stackgres.io/cluster=true' --replicas=0
    echo "Waiting for citus pods to terminate"
    kubectl wait --for=delete pod -l 'stackgres.io/cluster=true' -n "${namespace}" --timeout=-1s
  fi
}

function unpauseCitus() {
  local namespace="${1}"

  local citusPods=$(kubectl get pods -n "${namespace}" -l 'stackgres.io/cluster=true' -o 'jsonpath={.items[*].metadata.name}')
  if [[ -z "${citusPods}" ]]; then
    echo "Starting citus cluster in namespace ${namespace}"
    kubectl annotate endpoints -n "${namespace}" -l 'stackgres.io/cluster=true' initialize- --overwrite
    kubectl annotate sgclusters.stackgres.io -n "${namespace}" --all stackgres.io/reconciliation-pause- --overwrite
    echo "Waiting for citus pods to be ready"
    sleep 5
    kubectl wait --for=condition=Ready pod -l 'stackgres.io/cluster=true' -n "${namespace}" --timeout=-1s
    echo "Waiting citus replica pods to be ready"
    sleep 30 # Wait again as replicas will not spin up until the primary is started
    kubectl wait --for=condition=Ready pod -l 'stackgres.io/cluster=true' -n "${namespace}" --timeout=-1s
  else
    echo "Citus is already running in namespace ${namespace}. Skipping"
  fi
}

function resizeCitusNodePools() {
  local numNodes="${1}"

  echo "Scaling nodepool ${GCP_COORDINATOR_POOL_NAME} and ${GCP_WORKER_POOL_NAME} in cluster ${GCP_K8S_CLUSTER_NAME}
  for project ${GCP_PROJECT} to ${numNodes} nodes per zone"

  if [[ "${numNodes}" -gt 0 ]]; then
    gcloud container clusters resize "${GCP_K8S_CLUSTER_NAME}" --node-pool "${GCP_COORDINATOR_POOL_NAME}" --num-nodes "${numNodes}" --location "${GCP_K8S_CLUSTER_REGION}" --project "${GCP_PROJECT}" --quiet &
    gcloud container clusters resize "${GCP_K8S_CLUSTER_NAME}" --node-pool "${GCP_WORKER_POOL_NAME}" --num-nodes "${numNodes}" --location "${GCP_K8S_CLUSTER_REGION}" --project "${GCP_PROJECT}" --quiet &
    echo "Waiting for nodes to be ready"
    wait
    kubectl wait --for=condition=Ready node -l'citus-role=coordinator' --timeout=-1s
    kubectl wait --for=condition=Ready node -l'citus-role=worker' --timeout=-1s
  else
    local coordinatorNodes=$(kubectl get nodes -l'citus-role=coordinator' -o 'jsonpath={.items[*].metadata.name}')
    if [[ -z "${coordinatorNodes}" ]]; then
      echo "No coordinator nodes found"
    else
      echo "Scaling down coordinator nodes ${coordinatorNodes}"
      gcloud container clusters resize "${GCP_K8S_CLUSTER_NAME}" --node-pool "${GCP_COORDINATOR_POOL_NAME}" --num-nodes 0 --location "${GCP_K8S_CLUSTER_REGION}" --project "${GCP_PROJECT}" --quiet &
    fi

    local workerNodes=$(kubectl get nodes -l'citus-role=worker' -o 'jsonpath={.items[*].metadata.name}')
    if [[ -z "${workerNodes}" ]]; then
      echo "No worker nodes found"
    else
      echo "Scaling down worker nodes ${workerNodes}"
      gcloud container clusters resize "${GCP_K8S_CLUSTER_NAME}" --node-pool "${GCP_WORKER_POOL_NAME}" --num-nodes 0 --location "${GCP_K8S_CLUSTER_REGION}" --project "${GCP_PROJECT}" --quiet &
    fi
    echo "Waiting for nodes to be deleted"
    wait
    kubectl wait --for=delete node -l'citus-role=coordinator' --timeout=-1s
    kubectl wait --for=delete node -l'citus-role=worker' --timeout=-1s
  fi
}

function prepareDiskReplacement() {
  echo "Will spin down importer and citus in the namespaces (${NAMESPACES[*]}) for context ${CURRENT_CONTEXT} and
    Will restore snapshots ${SNAPSHOTS_TO_RESTORE}"
  doContinue

  for namespace in "${NAMESPACES[@]}"; do
    # Shutdown the importer
    scaleDeployment "${namespace}" 0 "app.kubernetes.io/component=importer"

    # Pause Citus
    pauseCitus "${namespace}"
  done

  # Spin down existing citus node pools
  resizeCitusNodePools 0
}

function renameZfsVolumes() {
  echo "Waiting for zfs pods to be ready"
  kubectl wait --for=condition=Ready pod -n "${COMMON_NAMESPACE}" -l 'component=openebs-zfs-node' --timeout=-1s

  local zfsNodePods=$(kubectl get pods -A -o wide -o json -l 'component=openebs-zfs-node' | jq -r '.items|map({node: (.spec.nodeName), podName: (.metadata.name)})')
  local zfsNodes=$(kubectl get zfsnodes.zfs.openebs.io -A -o json | jq -r '.items|map({nodeId: .metadata.name, node: .metadata.ownerReferences[0].name})')
  local nodeIdToPodMap=$(echo -e "${zfsNodePods}\n${zfsNodes}" |
    jq -s '.[0] as $zfsPods |
           .[1] |
           map(.node as $nodeName |
               {
                 (.nodeId) :  ($zfsPods[] | select(.node == $nodeName).podName)
               }
           )|
           add')

  echo "Renaming zfs datasets"
  for nodeId in "${UNIQUE_NODE_IDS[@]}"; do
    local podInfo=$(echo "${nodeIdToPodMap}" | jq -r --arg NODE_ID "${nodeId}" '.[$NODE_ID]')
    local nodeData=$(echo "${NODE_ID_MAP}" | jq -r --arg NODE_ID "${nodeId}" '.[$NODE_ID]')
    local pvcsCount=$(echo "${nodeData}" | jq -r '.pvcs|length')
    for pvcIndex in $(seq 0 $((pvcsCount - 1))); do
      local pvcVolumeName=$(echo "${nodeData}" | jq -r --arg PVC_INDEX "${pvcIndex}" '.pvcs[$PVC_INDEX|tonumber].volumeName')
      local snapshotPvcVolumeName=$(echo "${nodeData}" | jq -r --arg PVC_INDEX "${pvcIndex}" '.snapshot[0].description[$PVC_INDEX|tonumber].volumeName')

      if [[ "${pvcVolumeName}" != "${snapshotPvcVolumeName}" ]]; then
        echo "Renaming snapshot pvc ${ZFS_POOL_NAME}/${snapshotPvcVolumeName} to ${ZFS_POOL_NAME}/${pvcVolumeName}"
        kubectl exec -n "${COMMON_NAMESPACE}" "${podInfo}" -c openebs-zfs-plugin -- zfs rename "${ZFS_POOL_NAME}/${snapshotPvcVolumeName}" "${ZFS_POOL_NAME}/${pvcVolumeName}"
      else
        echo "Snapshot pvc ${ZFS_POOL_NAME}/${snapshotPvcVolumeName} already matches pvc ${ZFS_POOL_NAME}/${pvcVolumeName}"
      fi
    done
    kubectl exec -n "${COMMON_NAMESPACE}" "${podInfo}" -c openebs-zfs-plugin -- zfs list
  done
}

function replaceDisks() {
  echo "Will delete disks ${DISK_PREFIX}-(${UNIQUE_NODE_IDS[*]})-zfs in project ${GCP_PROJECT}"
  doContinue

  prepareDiskReplacement

  for nodeId in "${UNIQUE_NODE_IDS[@]}"; do
    local nodeInfo=$(echo "${NODE_ID_MAP}" | jq -r --arg NODE_ID "${nodeId}" '.[$NODE_ID]')
    local diskName="${DISK_PREFIX}-${nodeId}-zfs"
    local diskZone=$(echo "${nodeId}" | cut -d '-' -f 2-4)
    local snapshotName=$(echo "${nodeInfo}" | jq -r '.snapshot[0].name')
    local snapshotFullName="projects/${GCP_SNAPSHOT_PROJECT}/global/snapshots/${snapshotName}"
    echo "Recreating disk ${diskName} in ${GCP_PROJECT} with snapshot ${snapshotName}"
    gcloud compute disks delete "${diskName}" --project "${GCP_PROJECT}" --zone "${diskZone}" --quiet
    gcloud compute disks create "${diskName}" --project "${GCP_PROJECT}" --zone "${diskZone}" --source-snapshot "${snapshotFullName}" --type=pd-balanced --quiet &
  done

  echo "Waiting for disks to be created"
  wait

  resizeCitusNodePools 1
  renameZfsVolumes
}

function configureShardedClusterResource() {
  local pvcsInNamespace="${1}"
  local shardedClusterName="${2}"
  local namespace="${3}"

  local coordinatorPvcSize=$(echo "${pvcsInNamespace}" |
    jq -r 'map(select(.snapshotPrimary and .citusCluster.isCoordinator))|
                                  map(.snapshotPvcSize)|first')
  local workerPvcOverrides=$(echo "${pvcsInNamespace}" |
    jq -r 'map(select(.snapshotPrimary and .citusCluster.isCoordinator == false))|
                                sort_by(.citusCluster.citusGroup, .citusCluster.podName)|
                                to_entries|
                                map({index: .key, pods: {persistentVolume: {size: .value.snapshotPvcSize}}})')
  local shardedClusterPatch=$(echo "${workerPvcOverrides}" |
    jq -r --arg coordinatorPvcSize "${coordinatorPvcSize}" \
      '{
          spec: {
            coordinator: {
              pods: {
                persistentVolume: {
                  size: $coordinatorPvcSize
                }
              }
            },
            shards: {
              overrides: (.)
            }
         }
       }')
  echo "Patching sharded cluster ${shardedClusterName} in namespace ${namespace} with ${shardedClusterPatch}"
  kubectl patch sgshardedclusters.stackgres.io -n "${namespace}" "${shardedClusterName}" --type merge -p "${shardedClusterPatch}"
  echo "
  **** IMPORTANT ****
  Please configure your helm values.yaml for namespace ${namespace} to have the following values:

  stackgres.coordinator.pods.persistentVolume.size=${coordinatorPvcSize}

  stackgres.worker.overrides=${workerPvcOverrides}
  "
  echo "Continue to acknowledge config change is saved (do not need to apply the config change yet)"
  doContinue
}

function markAndConfigurePrimaries() {
  local pvcsInNamespace="${1}"
  local shardedClusterName="${2}"

  # Stackgres Passwords
  local primaryCoordinator=$(echo "${pvcsInNamespace}" |
          jq -r 'map(select(.snapshotPrimary and .citusCluster.isCoordinator))|first')
  local sgPasswordsSecretName=$(echo "${primaryCoordinator}" | jq -r '.citusCluster.clusterName')
  local sgPasswords=$(kubectl get secret -n "${namespace}" "${sgPasswordsSecretName}" -o json |
    ksd |
    jq -r '.stringData')
  local superuserUsername=$(echo "${sgPasswords}" | jq -r '.["superuser-username"]')
  local superuserPassword=$(echo "${sgPasswords}" | jq -r '.["superuser-password"]')
  local replicationUsername=$(echo "${sgPasswords}" | jq -r '.["replication-username"]')
  local replicationPassword=$(echo "${sgPasswords}" | jq -r '.["replication-password"]')
  local authenticatorUsername=$(echo "${sgPasswords}" | jq -r '.["authenticator-username"]')
  local authenticatorPassword=$(echo "${sgPasswords}" | jq -r '.["authenticator-password"]')

  # Mirror Node Passwords
  local mirrorNodePasswords=$(kubectl get secret -n "${namespace}" "${HELM_RELEASE_NAME}-passwords" -o json |
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
  local sqlCommands=(
  "alter user ${superuserUsername} with password '${superuserPassword}';"
  "alter user ${graphqlUsername} with password '${graphqlPassword}';"
  "alter user ${grpcUsername} with password '${grpcPassword}';"
  "alter user ${importerUsername} with password '${importerPassword}';"
  "alter user ${ownerUsername} with password '${ownerPassword}';"
  "alter user ${restUsername} with password '${restPassword}';"
  "alter user ${restJavaUsername} with password '${restJavaPassword}';"
  "alter user ${rosettaUsername} with password '${rosettaPassword}';"
  "alter user ${web3Username} with password '${web3Password}';"
  "alter user ${replicationUsername} with password '${replicationPassword}';"
  "alter user ${authenticatorUsername} with password '${authenticatorPassword}';")

  local clusterGroups=$(echo "${pvcsInNamespace}" |
    jq -r 'group_by(.citusCluster.clusterName)|
                     map({
                           (.[0].citusCluster.clusterName):
                           .|sort_by(.citusCluster.podName)|
                           map(
                             {
                                group: .citusCluster.citusGroup,
                                isCoordinator: .citusCluster.isCoordinator,
                                name: .citusCluster.podName,
                                primary: .snapshotPrimary,
                                shardedClusterName: .citusCluster.shardedClusterName
                             }
                          )
                        }
                    )|add')
  local clusterNames=($(echo "${clusterGroups}" | jq -r 'keys[]'))

  for clusterName in "${clusterNames[@]}"; do
    local groupPods=$(echo "${clusterGroups}" | jq -r --arg clusterName "${clusterName}" '.[$clusterName]')
    local clusterPatch=$(echo "${groupPods}" |
      jq -r '{status: {podStatuses: map({name: .name, primary: .primary})}}')
    local citusGroup=$(echo "${groupPods}" | jq -r '.[0].group')
    local primaryPod=$(echo "${groupPods}" | jq -r 'map(select(.primary))|first|.name')
    local patroniClusterStatus=$(kubectl exec -n "${namespace}" -c patroni "${primaryPod}" \
      -- patronictl list --group "${citusGroup}" -f json | jq -r 'map({primary: (.Role == "Leader"), name: .Member})')
    local patroniPrimaryPod=$(echo "${patroniClusterStatus}" | jq -r 'map(select(.primary))|first|.name')
    if [[ "${patroniPrimaryPod}" != "${primaryPod}" ]]; then
      echo "Primary pod ${primaryPod} is not the patroni primary ${patroniPrimaryPod} for ${shardedClusterName}
group ${citusGroup}. Will failover"
      kubectl exec -n "${namespace}" "${primaryPod}" -c patroni \
        -- patronictl failover "${shardedClusterName}" --group "${citusGroup}" --candidate "${primaryPod}" --force
      patroniPrimaryPod=$(echo "${patroniClusterStatus}" | jq -r 'map(select(.primary))|first|.name')
      while [[ "${patroniPrimaryPod}" != "${primaryPod}" ]]; do
        echo "Waiting for failover to complete expecting ${primaryPod} to be primary but got ${patroniPrimaryPod}"
        sleep 10
        patroniClusterStatus=$(kubectl exec -n "${namespace}" -c patroni "${primaryPod}" \
          -- patronictl list --group "${citusGroup}" -f json | jq -r 'map({primary: (.Role == "Leader"), name: .Member})')
        patroniPrimaryPod=$(echo "${patroniClusterStatus}" | jq -r 'map(select(.primary))|first|.name')
      done
    fi
    echo "Patching cluster ${clusterName} in namespace ${namespace} with ${clusterPatch}"
    kubectl patch sgclusters.stackgres.io -n "${namespace}" "${clusterName}" --type merge -p "${clusterPatch}"
    kubectl exec -n "${namespace}" "${primaryPod}" -c postgres-util \
                -- psql -U "${superuserUsername}" -c "${sql}"
    for sql in "${sqlCommands[@]}"; do
          echo "Executing sql command for cluster ${clusterName}: ${sql}"
          kubectl exec -n "${namespace}" "${primaryPod}" -c postgres-util \
            -- psql -U "${superuserUsername}" -c "${sql}"
    done

    kubectl exec -n "${namespace}" "${primaryPod}" -c postgres-util \
          -- psql -U "${superuserUsername}" -d "${dbName}" -c \
      "insert into pg_dist_authinfo(nodeid, rolename, authinfo)
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
      update set authinfo = excluded.authinfo;"
  done
}

function patchCitusClusters() {
  local pvcsByNamespace=$(echo -e "${SNAPSHOTS_TO_RESTORE}\n${ZFS_VOLUMES}" |
    jq -s '(.[0] | map(.description)| flatten) as $snapshots|
            .[1] as $volumes|
            $volumes | group_by(.namespace)|
            map((.[0].namespace) as $namespace |
              {
                ($namespace):
                   map(. as $pvc |
                       $snapshots[]|
                       select(.pvcName == $pvc.pvcName and .namespace == $pvc.namespace) as $snapshotPvc|
                    $pvc + {snapshotPvcSize: $snapshotPvc.pvcSize, snapshotPrimary: $snapshotPvc.primary})
              }
            )|
            add')
  for namespace in "${NAMESPACES[@]}"; do
    local pvcsInNamespace=$(echo "${pvcsByNamespace}" | jq -r --arg namespace "${namespace}" '.[$namespace]')
    local shardedClusterName=$(echo "${pvcsInNamespace}" | jq -r '.[0].citusCluster.shardedClusterName')

    configureShardedClusterResource "${pvcsInNamespace}" "${shardedClusterName}" "${namespace}"
    unpauseCitus "${namespace}"
    markAndConfigurePrimaries "${pvcsInNamespace}" "${shardedClusterName}"
    scaleDeployment "${namespace}" 1 "app.kubernetes.io/component=importer"
  done
}

configureAndValidate

if [[ "${REPLACE_DISKS}" == "true" ]]; then
  replaceDisks
else
  echo "REPLACE_DISKS is set to false. Skipping disk replacement"
fi

patchCitusClusters
