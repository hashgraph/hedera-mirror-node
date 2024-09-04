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

## Steps

1. Configure kubectl to point to the `source cluster`
   <br>
   `gcloud container clusters get-credentials {clusterName} --region {clusterRegion} --project {gcpProjectName}`
2. Get the details needed for the name and description of the snapshot
   <br>
   `kubectl get pvc -A -lstackgres.io/cluster="true" -o json |jq -r '.items[]|"{\n  pvcName: \(.metadata.name),\n  pvcSize: \(.spec.resources.requests.storage),\n  volumeName: \(.spec.volumeName),\n  namespace: \(.metadata.namespace)\n}"'`
   <br>
   `kubectl get zfsvolumes -A -o json |jq -r '.items[]|"{\n  volumeName: \(.metadata.name),\n  capacity: \(.spec.capacity),\n  nodeId: \(.metadata.labels["kubernetes.io/nodename"]),\n  namespace: \(.metadata.namespace)\n}"'`
3. Scale down the importer in all namespaces for effected disks
   <br>
   `kubectl scale deployment --context {sourceClusterContext} -n {namespace} mirror-importer --replicas=0 `
4. Identify which `pvc`s are the primary for coordinators and workers. The easiest way to do so is by looking at
   the `Stackgres` UI
   <br>
   `kubectl port-forward -n {commonNamespace}  services/stackgres-restapi 8085:443`
   <br>
   open a browser and go to `https://localhost:8085`
   <br>
   to get username and password for `Stackgres` UI, run
   <br>
   `kubectl get secrets -n {commonNamespace} stackgres-restapi-admin -o json |ksd |jq '.stringData|"userName: \(.k8sUsername), password: \(.clearPassword)"'`
5. Create a new disk snapshot for each disk used in the `source cluster`s. Use the values from step 2
   <br>
   Each unique `nodeId` from step 2 maps to a unique disk
   <br>
   `diskPrefix` - `{zfs.init.diskPrefix}` (from common chart's `values.yaml`)
   <br>
   `sourceDisk` - `{diskPrefix}-{nodeId}-zfs`
   <br>
   `nameOfSnapshot` - `{nodeId}-{dateSnapshotTaken}`
   <br>
   `pvcNames` - comma separated list of `pvcName`s where the `pvc` `volumeName` matches the `zfsvolume`
   `volumeName` for all `zfsvolumes` with the same `nodeId` used for `sourceDisk`
   <br>
   `pvcSizes` - comma separated list of `pvcSizes`s with position corresponding to the position in `pvcNames` list
   <br>
   `namespaces` - comma separated list of `namespace`s where the `pvc` is located
   with position corresponding to the position in `pvcNames` list
   <br>
   `volumeNames` - comma separated list of `volumeName`s where the `zfsvolume` `nodeId` matches the `nodeId` used for
   `sourceDisk` with position corresponding to the position in `pvcNames` list
   <br>
   `capacities` - comma separated list of `capacity` with position corresponding to the position in `pvcNames` list
   <br>
   `primary` - comma separated list of `true` or `false` with position corresponding to the position in `pvcNames`
   list
   <br>
   `snapshotRegion` - region where the snapshot will be stored
   <br>
   (note you can restore snapshots to a different region than the `snapshotRegion`)
   <br>
   `description` -
   `pvcNames={pvcNames}, volumeNames={volumeNames}, pvcSizes={pvcSizes}, capacities={capacities}, namespaces={namespaces}, primary={primary}`
   <br>
   `gcloud compute snapshots create {nameOfSnapshot} --project={project}  --source-disk={sourceDisk} --source-disk-zone={sourceDiskZone} --storage-location={snapshotRegion} --description={description}`
6. Once the snapshots are all finished creating, you may re-enable the importer for all namespaces
   spun down in step 3
   `kubectl scale deployment --context {sourceClusterContext} --replicas=1 -n {namespace} mirror-importer`