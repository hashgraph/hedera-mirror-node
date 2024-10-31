#!/usr/bin/env bash
set -euo pipefail

function doContinue() {
  read -p "Continue? (Y/N): " confirm && [[ $confirm == [yY] || $confirm == [yY][eE][sS] ]] || exit 1
}

function log() {
  echo "$(date -u +"%Y-%m-%dT%H:%M:%SZ") ${1}"
}

function readUserInput() {
  read -p "${1}" input
  echo "${input}"
}

function scaleDeployment() {
  local namespace="${1}"
  local replicas="${2}"
  local deploymentLabel="${3}"

  if [[ "${replicas}" -gt 0 ]]; then # scale up
    kubectl scale deployment -n "${namespace}" -l "${deploymentLabel}" --replicas="${replicas}"
    log "Waiting for pods with label ${deploymentLabel} to be ready"
    kubectl wait --for=condition=Ready pod -n "${namespace}" -l "${deploymentLabel}" --timeout=-1s
  else # scale down
    local deploymentPods=$(kubectl get pods -n "${namespace}" -l "${deploymentLabel}" -o 'jsonpath={.items[*].metadata.name}')
    if [[ -z "${deploymentPods}" ]]; then
      log "No pods found for deployment ${deploymentLabel} in namespace ${namespace}"
      return
    else
      log "Removing pods ${deploymentPods} in ${namespace} for ${CURRENT_CONTEXT}"
      doContinue
      kubectl scale deployment -n "${namespace}" -l "${deploymentLabel}" --replicas="${replicas}"
      log "Waiting for pods with label ${deploymentLabel} to be deleted"
      kubectl wait --for=delete pod -n "${namespace}" -l "${deploymentLabel}" --timeout=-1s
    fi
  fi
}

function unrouteTraffic() {
  local namespace="${1}"
  if [[ "${AUTO_UNROUTE}" == "true" ]]; then
    log "Unrouting traffic to cluster in namespace ${namespace}"
    if kubectl get helmrelease -n "${namespace}" "${HELM_RELEASE_NAME}" > /dev/null; then
      log "Suspending helm release ${HELM_RELEASE_NAME} in namespace ${namespace}"
      doContinue
      flux suspend helmrelease -n "${namespace}" "${HELM_RELEASE_NAME}"
    else
      log "No helm release found in namespace ${namespace}. Skipping suspend"
    fi

    scaleDeployment "${namespace}" 0 "app.kubernetes.io/component=monitor"
  fi
  scaleDeployment "${namespace}" 0 "app.kubernetes.io/component=importer"
}

function routeTraffic() {
  local namespace="${1}"

  log "Running test queries"
  kubectl exec -it -n "${namespace}" "${HELM_RELEASE_NAME}-citus-coord-0" -c postgres-util -- psql -U mirror_rest -d mirror_node -c "select * from transaction limit 10"
  kubectl exec -it -n "${namespace}" "${HELM_RELEASE_NAME}-citus-coord-0" -c postgres-util -- psql -U mirror_node -d mirror_node -c "select * from transaction limit 10"
  doContinue
  scaleDeployment "${namespace}" 1 "app.kubernetes.io/component=importer"
  while true; do
    local statusQuery="select $(date +%s) - (max(consensus_end) / 1000000000) from record_file"
    local status=$(kubectl exec -n "${namespace}" "${HELM_RELEASE_NAME}-citus-coord-0" -c postgres-util -- psql -q --csv -t -U mirror_rest -d mirror_node -c "select $(date +%s) - (max(consensus_end) / 1000000000) from record_file" | tail -n 1)
    if [[ "${status}" -lt 10 ]]; then
      log "Importer is caught up with the source"
      break
    else
      log "Waiting for importer to catch up with the source. Current lag: ${status} seconds"
      sleep 10
    fi
  done
  if [[ "${AUTO_UNROUTE}" == "true" ]]; then
    if kubectl get helmrelease -n "${namespace}" "${HELM_RELEASE_NAME}" > /dev/null; then
      log "Resuming helm release ${HELM_RELEASE_NAME} in namespace ${namespace}.
          Be sure to configure values.yaml with any changes before continuing"
      doContinue
      flux resume helmrelease -n "${namespace}" "${HELM_RELEASE_NAME}" --timeout 30m
    else
      log "No helm release found in namespace ${namespace}. Skipping suspend"
    fi
    scaleDeployment "${namespace}" 1 "app.kubernetes.io/component=monitor"
  fi
}

function pauseCitus() {
  local namespace="${1}"
  local citusPods=$(kubectl get pods -n "${namespace}" -l 'stackgres.io/cluster=true' -o 'jsonpath={.items[*].metadata.name}')
  if [[ -z "${citusPods}" ]]; then
    log "Citus is not currently running"
  else
    log "Removing pods (${citusPods}) in ${namespace} for ${CURRENT_CONTEXT}"
    doContinue
    kubectl annotate sgclusters.stackgres.io -n "${namespace}" --all stackgres.io/reconciliation-pause="true" --overwrite
    sleep 5
    kubectl scale sts -n "${namespace}" -l 'stackgres.io/cluster=true' --replicas=0
    log "Waiting for citus pods to terminate"
    kubectl wait --for=delete pod -l 'stackgres.io/cluster=true' -n "${namespace}" --timeout=-1s
  fi
}

function unpauseCitus() {
  local namespace="${1}"
  local reinitializeCitus="${2:-false}"

  local citusPods=$(kubectl get pods -n "${namespace}" -l 'stackgres.io/cluster=true' -o 'jsonpath={.items[*].metadata.name}')
  if [[ -z "${citusPods}" ]]; then
    log "Starting citus cluster in namespace ${namespace}"
    if [[ "${reinitializeCitus}" == "true" ]]; then
      kubectl annotate endpoints -n "${namespace}" -l 'stackgres.io/cluster=true' initialize- --overwrite
    fi
    kubectl annotate sgclusters.stackgres.io -n "${namespace}" --all stackgres.io/reconciliation-pause- --overwrite
    log "Waiting for citus pods to be ready"
    sleep 5
    kubectl wait --for=condition=Ready pod -l 'stackgres.io/cluster=true' -n "${namespace}" --timeout=-1s
    log "Waiting for citus replica pods to be ready"
    sleep 30 # Wait again as replicas will not spin up until the primary is started
    kubectl wait --for=condition=Ready pod -l 'stackgres.io/cluster=true' -n "${namespace}" --timeout=-1s
  else
    log "Citus is already running in namespace ${namespace}. Skipping"
  fi
}

function getCitusClusters() {
  kubectl get sgclusters.stackgres.io -A -o json |
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
             )'
}

function getZFSVolumes () {
  kubectl get pv -o json |
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
        )'
}

function resizeCitusNodePools() {
  local numNodes="${1}"

  log "Scaling nodepool ${GCP_COORDINATOR_POOL_NAME} and ${GCP_WORKER_POOL_NAME} in cluster ${GCP_K8S_CLUSTER_NAME}
  for project ${GCP_PROJECT} to ${numNodes} nodes per zone"

  if [[ "${numNodes}" -gt 0 ]]; then
    gcloud container clusters resize "${GCP_K8S_CLUSTER_NAME}" --node-pool "${GCP_COORDINATOR_POOL_NAME}" --num-nodes "${numNodes}" --location "${GCP_K8S_CLUSTER_REGION}" --project "${GCP_PROJECT}" --quiet &
    gcloud container clusters resize "${GCP_K8S_CLUSTER_NAME}" --node-pool "${GCP_WORKER_POOL_NAME}" --num-nodes "${numNodes}" --location "${GCP_K8S_CLUSTER_REGION}" --project "${GCP_PROJECT}" --quiet &
    log "Waiting for nodes to be ready"
    wait
    kubectl wait --for=condition=Ready node -l'citus-role=coordinator' --timeout=-1s
    kubectl wait --for=condition=Ready node -l'citus-role=worker' --timeout=-1s
  else
    local coordinatorNodes=$(kubectl get nodes -l'citus-role=coordinator' -o 'jsonpath={.items[*].metadata.name}')
    if [[ -z "${coordinatorNodes}" ]]; then
      log "No coordinator nodes found"
    else
      log "Scaling down coordinator nodes ${coordinatorNodes}"
      gcloud container clusters resize "${GCP_K8S_CLUSTER_NAME}" --node-pool "${GCP_COORDINATOR_POOL_NAME}" --num-nodes 0 --location "${GCP_K8S_CLUSTER_REGION}" --project "${GCP_PROJECT}" --quiet &
    fi

    local workerNodes=$(kubectl get nodes -l'citus-role=worker' -o 'jsonpath={.items[*].metadata.name}')
    if [[ -z "${workerNodes}" ]]; then
      log "No worker nodes found"
    else
      log "Scaling down worker nodes ${workerNodes}"
      gcloud container clusters resize "${GCP_K8S_CLUSTER_NAME}" --node-pool "${GCP_WORKER_POOL_NAME}" --num-nodes 0 --location "${GCP_K8S_CLUSTER_REGION}" --project "${GCP_PROJECT}" --quiet &
    fi
    log "Waiting for nodes to be deleted"
    wait
    kubectl wait --for=delete node -l'citus-role=coordinator' --timeout=-1s
    kubectl wait --for=delete node -l'citus-role=worker' --timeout=-1s
  fi
}

COMMON_NAMESPACE="${COMMON_NAMESPACE:-common}"
HELM_RELEASE_NAME="${HELM_RELEASE_NAME:-mirror}"
CURRENT_CONTEXT="$(kubectl config current-context)"
CITUS_CLUSTERS="$(getCitusClusters)"
AUTO_UNROUTE="${AUTO_UNROUTE:-true}"
GCP_COORDINATOR_POOL_NAME="${GCP_COORDINATOR_POOL_NAME:-citus-coordinator}"
GCP_WORKER_POOL_NAME="${GCP_WORKER_POOL_NAME:-citus-worker}"