# Upgrade K8s Version for Citus Node Pool(s)

## Problem

Need to update k8s version for Citus Node Pool(s)

## Solution

1. Suspend the helm release in all namespaces running a Citus cluster
   `flux suspend helmrelease -n {namespace} {releaseName}`
2. Scale down the importer, monitor, and Citus cluster(s) in all namespaces running a Citus cluster
   <br>
   `kubectl scale deployment --context {sourceClusterContext} -n {namespace} mirror-importer --replicas=0`
   <br>
   `kubectl scale deployment --context {sourceClusterContext} -n {namespace} mirror-monitor --replicas=0`
   <br>
   `kubectl annotate sgclusters.stackgres.io -n {namespace} --all stackgres.io/reconciliation-pause="true" --overwrite`
   <br>
   `kubectl scale sts -n {namespace} -l 'stackgres.io/cluster=true' --replicas=0`
3. Perform k8s version upgrade for the node pool(s)
   <br>
   `gcloud container clusters upgrade {clusterName} --node-pool=citus-worker --cluster-version={version} --location={region} --project={project}`
    <br>
   `gcloud container clusters upgrade {clusterName} --node-pool=citus-coordinator --cluster-version={version} --location={region} --project={project}`
4. Re-enable the importer and Citus cluster reconciliation for all namespaces spun down in step 2
   <br>
   `kubectl annotate sgclusters.stackgres.io -n {namespace} stackgres.io/reconciliation-pause- --overwrite`
   <br>
   Wait for the Citus pods to be ready and then scale up the importer
   <br>
   `kubectl scale deployment --context {sourceClusterContext} --replicas=1 -n {namespace} mirror-importer`
5. Monitor importer logs and wait for it to catch up with the Citus cluster
    <br>
    `kubectl logs -n {namespace} -lapp.kubernetes.io/name=importer --follow`
6. Scale up the monitor
   <br>
   `kubectl scale deployment --context {sourceClusterContext} --replicas=1 -n {namespace} mirror-monitor`
7. Resume the helm release in all namespaces suspended in step 1
   `flux resume helmrelease -n {namespace} {releaseName} --timeout=30m`
