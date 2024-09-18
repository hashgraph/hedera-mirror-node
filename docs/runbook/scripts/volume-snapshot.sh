#!/usr/bin/env bash

GCP_PROJECT="${GCP_PROJECT}"
COMMON_NAMESPACE="${COMMON_NAMESPACE:-common}"
HELM_RELEASE_NAME="${HELM_RELEASE_NAME:-mirror}"

if [[ -z "${GCP_PROJECT}" ]]; then
  echo "GCP_PROJECT is not set and is required. Exiting"
  exit 1
fi

CURRENT_CONTEXT=$(kubectl config current-context)
ZFS_VOLUMES=$(kubectl get pv -o json |
jq -r '.items|map(select(.metadata.annotations."pv.kubernetes.io/provisioned-by"=="zfs.csi.openebs.io" and .status.phase == "Bound")|
              {
                namespace: (.spec.claimRef.namespace),
                volumeName: (.metadata.name),
                pvcName: (.spec.claimRef.name),
                pvcSize: (.spec.capacity.storage),
                nodeId: (.spec.nodeAffinity.required.nodeSelectorTerms[0].matchExpressions[0].values[0])
              })')

NAMESPACES=($(echo $ZFS_VOLUMES | jq -r '.[].namespace'| tr ' ' '\n' | sort -u | tr '\n' ' '))

function doContinue() {
  read -p "Continue? (Y/N): " confirm && [[ $confirm == [yY] || $confirm == [yY][eE][sS] ]] || exit 1
}

echo "Will spin down importer and citus in the namespaces (${NAMESPACES[*]}) for context $CURRENT_CONTEXT"
doContinue

for namespace in "${NAMESPACES[@]}"
do
   IMPORTER_PODS=$(kubectl get pods -n "${namespace}" -l 'app.kubernetes.io/component=importer' -o 'jsonpath={.items[*].metadata.name}')
   if [[ -n "${IMPORTER_PODS}" ]]; then
     echo "Will delete importer ${IMPORTER_PODS} for namespace ${namespace}"
     doContinue
     kubectl scale deployment -n "${namespace}" -l app.kubernetes.io/component=importer --replicas=0
     echo "Waiting for importer pods to be deleted"
     kubectl wait --for=delete pod -n "${namespace}"  -l 'app.kubernetes.io/component=importer' --timeout=-1s
   else
     echo "Importer is not currently running"
   fi

   CITUS_PODS=$(kubectl get pods -n "${namespace}" -l 'stackgres.io/cluster=true' -o 'jsonpath={.items[*].metadata.name}')
   if [[ -n "${CITUS_PODS}" ]]; then
     echo "Will delete citus pods ($CITUS_PODS) cluster in ${namespace}"
     doContinue
     kubectl annotate sgclusters.stackgres.io -n "${namespace}" --all stackgres.io/reconciliation-pause="true" --overwrite
     sleep 30
     kubectl scale sts -n "${namespace}" -l 'stackgres.io/cluster=true' --replicas=0
     echo "Waiting for citus pods to terminate"
     kubectl wait --for=delete pod -l 'stackgres.io/cluster=true' -n "${namespace}"  --timeout=-1s
   else
     echo "Citus is not currently running"
   fi
done

DISK_PREFIX=$(helm get values "${HELM_RELEASE_NAME}" -n "${COMMON_NAMESPACE}" -o json |jq -r '.zfs.init.diskPrefix')
if [[ -z "${DISK_PREFIX}" ]]; then
  echo "Unable to find diskPrefix. Please check hedera-mirror-common configuration and rerun"
  exit 1
fi
echo "Finding disks with prefix ${DISK_PREFIX}"
DISKS_TO_SNAPSHOT=$(gcloud compute disks list --project "${GCP_PROJECT}"  --filter="name~${DISK_PREFIX}.*-zfs" --format="json(name, sizeGb, users, zone)")

if [[ -z "${DISKS_TO_SNAPSHOT}" ]]; then
  echo "No disks found for prefix. Exiting"
  exit 1
else
  DISK_NAMES=($(echo $DISKS_TO_SNAPSHOT | jq -r '.[].name'))
  echo "Will snapshot disks ${DISK_NAMES[*]}"
  doContinue
  CITUS_CLUSTERS=$(kubectl get sgclusters.stackgres.io -A  -o json  |
                   jq -r '.items|
                   map(
                     .metadata as $metadata|
                     .spec.postgres.version as $pgVersion|
                     .status.podStatuses[]|
                       {
                         clusterName: $metadata.name,
                         namespace: $metadata.namespace,
                         podName: .name,
                         pvcName: "\($metadata.name)-data-\(.name)",
                         pgVersion: $pgVersion,
                         primary: .primary
                       }
                   )')
  EPOCH_SECONDS=$(date +%s)
  for diskName in "${DISK_NAMES[@]}"
  do
    DISK_NODE_ID=${diskName#"$DISK_PREFIX"-}
    DISK_NODE_ID=${DISK_NODE_ID%"-zfs"}
    NODE_VOLUMES=$(echo "${ZFS_VOLUMES}" |
                   jq -r --arg NODE_ID "${DISK_NODE_ID}" 'map(select(.nodeId == $NODE_ID))')
    SNAPSHOT_DESCRIPTION=$(echo -e "${CITUS_CLUSTERS}\n${NODE_VOLUMES}" |
                          jq -r -s --arg NODE_ID "${DISK_NODE_ID}" '.[0] as $clusters |
                                                                    .[1] as $volumes |
                                                                    $volumes |
                                                                    map(. as $volume|
                                                                        $clusters[]|
                                                                        select(.pvcName == $volume.pvcName and .namespace == $volume.namespace)|
                                                                        {
                                                                          pvcName: $volume.pvcName,
                                                                          volumeName: $volume.volumeName,
                                                                          pvcSize: $volume.pvcSize,
                                                                          namespace: $volume.namespace,
                                                                          primary: .primary,
                                                                          pgVersion: .pgVersion
                                                                        })')
    SNAPSHOT_NAME="${diskName}-${EPOCH_SECONDS}"
    SNAPSHOT_REGION=$(echo "${DISK_NODE_ID}" | cut -d '-' -f 2-3)
    DISK_ZONE=$(echo "${DISK_NODE_ID}" | cut -d '-' -f 2-4)
    echo "Creating snapshot ${SNAPSHOT_NAME} for ${diskName} with ${SNAPSHOT_DESCRIPTION} in ${SNAPSHOT_REGION}"
    gcloud compute snapshots create "${SNAPSHOT_NAME}" --project="${GCP_PROJECT}"  --source-disk="${diskName}" --source-disk-zone="${DISK_ZONE}" --storage-location="${SNAPSHOT_REGION}" --description="${SNAPSHOT_DESCRIPTION}" &
  done
  echo "Waiting for snapshots to finish"
  wait
  echo "Snapshots finished"

  for namespace in "${NAMESPACES[@]}"
  do
    kubectl annotate sgclusters.stackgres.io -n "${namespace}" stackgres.io/reconciliation-pause- --all --overwrite
    echo "Waiting for citus pods to be ready"
    sleep 5
    kubectl wait --for=condition=Ready pod -l 'stackgres.io/cluster=true' -n "${namespace}" --timeout=-1s
    echo "Waiting citus replica pods to be ready"
    sleep 30 # Wait again as replicas will not spin up until the primary is started
    kubectl wait --for=condition=Ready pod -l 'stackgres.io/cluster=true' -n "${namespace}" --timeout=-1s
    kubectl scale deployment -n "${namespace}" -l app.kubernetes.io/component=importer --replicas=1
    echo "Waiting for importer pods to be ready"
    sleep 5
    kubectl wait --for=condition=Ready pod -n "${namespace}"  -l 'app.kubernetes.io/component=importer' --timeout=-1s
  done
fi