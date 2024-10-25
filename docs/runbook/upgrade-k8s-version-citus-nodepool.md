# Upgrade K8s Version for Citus Node Pool(s)

## Problem

Need to update k8s version for Citus Node Pool(s)

## Prerequisites

- Have `jq` installed
- kubectl is pointing to the cluster you want to upgrade
- All bash commands assume your working directory is `docs/runbook/scripts`

## Solution

1. Follow the steps to [create a disk snapshot for Citus cluster](./create-disk-snapshot-for-citus-cluster.md)
   to backup the current cluster data
2. Configure and export env vars
   ```bash
   export GCP_PROJECT="my-gcp-project"
   export GCP_K8S_CLUSTER_NAME="my-cluster-name"
   export GCP_K8S_CLUSTER_REGION="my-cluster-region"
   export GCP_WORKER_POOL_NAME="citus-worker"
   export GCP_COORDINATOR_POOL_NAME="citus-coordinator"
   export MACHINE_TYPE="new-machine-type"
   export AUTO_UNROUTE="true" # Automatically suspend/resume helm release and scale monitor 
   export VERSION="new-k8s-version" # Specify the new k8s version
   export POOLS_TO_UPDATE=("${GCP_WORKER_POOL_NAME}" "${GCP_COORDINATOR_POOL_NAME}" "default-pool")
   ```
3. Run
   ```bash
   source ./utils.sh
   NAMESPACES=($(kubectl get sgshardedclusters.stackgres.io -A -o jsonpath='{.items[*].metadata.namespace}'))
   for namespace in "${NAMESPACES[@]}"
   do
     unrouteTraffic "${namespace}"
     pauseCitus "${namespace}"
   done
   for pool in "${POOLS_TO_UPDATE[@]}"
   do
    gcloud container clusters upgrade ${GCP_K8S_CLUSTER_NAME} --node-pool=${pool} --cluster-version=${VERSION} --location=${GCP_K8S_CLUSTER_REGION} --project=${GCP_PROJECT}
   done
   for namespace in "${NAMESPACES[@]}"
   do
     unpauseCitus "${namespace}"
     routeTraffic "${namespace}"
   done
   ```
