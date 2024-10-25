# Change Machine Type for Citus Node Pool(s)

## Problem

Need to Change Machine Type for Citus Node Pool(s)

## Prerequisites

- Have `jq` installed
- kubectl is pointing to the cluster you want to create snapshots from
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
   export POOLS_TO_UPDATE=("${GCP_WORKER_POOL_NAME}" "${GCP_COORDINATOR_POOL_NAME}")
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
   resizeCitusNodePools 0
   for pool in "${POOLS_TO_UPDATE[@]}"
   do
    gcloud container node-pools update ${pool} --project=${GCP_PROJECT} --cluster=${GCP_K8S_CLUSTER_NAME} --location=${GCP_K8S_CLUSTER_REGION} --machine-type=${MACHINE_TYPE}
   done
   resizeCitusNodePools 1
   for namespace in "${NAMESPACES[@]}"
   do
     unpauseCitus "${namespace}"
     routeTraffic "${namespace}"
   done
   ```
