# Create Disk Snapshot for Citus Cluster

## Problem

Need to create a snapshot of disks for Citus cluster

## Definitions

* `coordinator` - disk that contains the coordinator data
* `worker` - disk that contains shard data
* `source cluster` - The kubernetes cluster where Citus cluster is installed

## Prerequisites

* Have access to running Citus cluster in a Kubernetes cluster
* Have `jq` installed
  TODO include postgres version in the description
* Traffic is not routed to the environment where snapshots are being taken from

## Steps

1. Configure kubectl to point to the `source cluster`
   <br>
   `gcloud container clusters get-credentials {clusterName} --region {clusterRegion} --project {gcpProjectName}`
2. Get the details needed for the name and description of the snapshots
   <br>
   `kubectl get pv -o json |jq -r '.items[]|select(.metadata.annotations."pv.kubernetes.io/provisioned-by"=="zfs.csi.openebs.io" and .status.phase == "Bound")|"namespace: \(.spec.claimRef.namespace) volumeName: \(.metadata.name) pvcName: \(.spec.claimRef.name) pvcSize: \(.spec.capacity.storage)  nodeId: \(.spec.nodeAffinity.required.nodeSelectorTerms[0].matchExpressions[0].values[0])"'`
3. Scale down the importer and Citus cluster(s) in all namespaces for effected disks. (TODO do 2 steps)
   <br>
   `kubectl scale deployment --context {sourceClusterContext} -n {namespace} mirror-importer --replicas=0`
   <br>
   `kubectl annotate sgclusters.stackgres.io -n {namespace} --all stackgres.io/reconciliation-pause="true" --overwrite`
   <br>
   `kubectl scale sts -n {namespace} -l 'stackgres.io/cluster=true' --replicas=0`
   <br>
   wait for Citus pods to fully terminate
   <br>
   `watch kubectl get pods -n {namespace}`
4. Identify which `pvc`s are for the primary coordinators and workers.
   <br>
   `kubectl get sgclusters.stackgres.io -A  -o json |jq -r '.items[]|"\(.metadata.namespace) \(.status.podStatuses[]|select(.primary)|.name)"'`
5. Identify the disks to snapshot
   <br>
   `gcloud compute disks list --project {gcpProjectName} --filter="name~{diskPrefix}.*-zfs" --format="table(name, sizeGb, users)"`
6. Using the values from step 2, create a new snapshot for each disk
   <br>
   Each unique `nodeId` from step 2 maps to a unique disk
   <br>
   `diskPrefix` - `{zfs.init.diskPrefix}` (from common chart's `values.yaml`)
   <br>
   `sourceDisk` - `{diskPrefix}-{nodeId}-zfs`
   <br>
   `nameOfSnapshot` - `{nodeId}-{dateSnapshotTaken}`
   <br>
   `pvcNames` - comma separated list of `pvcName`s with same `nodeId`
   <br>
   `pvcSizes` - comma separated list of `pvcSizes`s with position corresponding to the position in `pvcNames` list
   <br>
   `namespaces` - comma separated list of `namespace`s with position corresponding to the position in `pvcNames` list
   <br>
   `volumeNames` - comma separated list of `volumeName`s with position corresponding to the position in `pvcNames` list
   <br>
   `primary` - comma separated list of `true` or `false` with position corresponding to the position in `pvcNames`
   list
   <br>
   `snapshotRegion` - region where the snapshot will be stored
   <br>
   (note you can restore snapshots to a different region than the `snapshotRegion`)
   <br>
   `description` -
   `pvcNames={pvcNames}, volumeNames={volumeNames}, pvcSizes={pvcSizes}, namespaces={namespaces}, primary={primary}`
   <br>
   `gcloud compute snapshots create {nameOfSnapshot} --project={project}  --source-disk={sourceDisk} --source-disk-zone={sourceDiskZone} --storage-location={snapshotRegion} --description={description}`
7. Scale up the importer and Citus cluster(s) in all namespaces for effected disks
   <br>
   `kubectl annotate sgclusters.stackgres.io -n {namespace} stackgres.io/reconciliation-pause- --overwrite`
   <br>
   `kubectl scale deployment --context {sourceClusterContext} -n {namespace} mirror-importer --replicas=0`