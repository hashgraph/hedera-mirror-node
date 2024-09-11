# Restore Citus Data From Disk Snapshots

## Problem

Need to restore Citus cluster from disk snapshots

## Definitions

* `coordinator` - pod that contains the coordinator data
* `worker` - pod that contains shard data
* `citus role` - the role of the node in the Citus cluster. Can be either `coordinator` or `worker`
* `target cluster` - the Kubernetes cluster to where the disk snapshot(s) will be restored to
* `source cluster` - the Kubernetes cluster where the Citus cluster(s) contained in snapshots are installed
* `diskPrefix` - the prefix of the disk name. This is the value of `zfs.init.diskPrefix` in the
  `hedera-mirror-common` chart's `values.yaml`
* `statefulSetIndex` - this is the id part of the `statefulSet`'s pod name. For example, in the `statefulSet` pod's name
  `mirror-citus-coord-0`, the `statefulSetIndex` is `0`
* `diskIndex` - the index of the disk in the `citus role` and `zone` combination. This is an integer counter for each
  `citus role` and `zone` combination starting at 0 and incremented for each disk with same `citus role` in the same
  `zone`
* `nodeId` - the node id is `{worker OR coordinator}-{zone}-{diskIndex}` used in step 2 in [steps](#steps)
* `diskName` - `diskPrefix`-`nodeId`-`zfs`

## Prerequisites

* Snapshots of disks were created by following the [create snapshot](create-disk-snapshot-for-citus-cluster.md) runbook
* Have `jq` and `ksd`(kubernetes secret decrypter) installed
* Have the hashgraph helm repository added
  <br>
  `helm repo add hashgraph https://hashgraph.github.io//hedera-mirror-node/charts`
  TODO scale sts back up kubectl annotate sgclusters.stackgres.io -n {namespace} --all
  stackgres.io/reconciliation-pause- --overwrite
* All environments being restored do not have external traffic routed to them

## Requirements

* If you have multiple Citus clusters in the `source cluster`, you will need to restore all of them if data for
  those clusters are contained in the same snapshot for a Citus cluster you are intending to restore
* The `hedera-mirror` chart is configured to run the `postgres-util` container
  (enabled by `.stackgres.(coordinator AND worker).enablePostgresUtil` in the `hedera-mirror` chart)

## Steps

1. Disable traffic to the cluster if it is currently servicing requests
2. Configure kubectl to point to the `target cluster`
   <br>
   `gcloud container clusters get-credentials {clusterName} --region {clusterRegion} --project {gcpProjectName}`
3. Install the common chart
   <br>
   `helm upgrade --install {releaseName} hashgraph/hedera-mirror-common --version {version} --create-namespace -n {commonNamespace} --values {pathToValuesYaml}`
   <br>
4. Create the Citus cluster by installing the `hedera-mirror` chart if not already installed.
   <br>
   `helm upgrade --install {releaseName} hashgraph/hedera-mirror --version {version} -n {clusterNamespace} --values {pathToCitusValuesYaml}`
   <br>
5. Get disk info used by Citus clusters and verify your snapshots contain the necessary data
   <br>
   `kubectl get pv -o json |jq -r '.items[]|select(.metadata.annotations."pv.kubernetes.io/provisioned-by"=="zfs.csi.openebs.io" and .status.phase == "Bound")|"namespace: \(.spec.claimRef.namespace) volumeName: \(.metadata.name) pvcName: \(.spec.claimRef.name) pvcSize: \(.spec.capacity.storage)  nodeId: \(.spec.nodeAffinity.required.nodeSelectorTerms[0].matchExpressions[0].values[0])"'`
6. [Pause The Citus Cluster](#pause-the-citus-cluster)
7. [Remove Existing Disks](#remove-existing-disks)
8. [Create Disks](#create-disks)
9. [Update ZFS Datasets](#update-zfs-datasets)
10. [Start the Citus cluster](#start-the-citus-cluster)
11. [Mark Primary Coordinator and Workers](#set-primary-coordinator-and-workers)
12. [Update Passwords](#update-passwords)
13. Deploy the `hedera-mirror` chart with the importer enabled
    <br>
    `helm upgrade --install {releaseName} hashgraph/hedera-mirror --version {version} -n {clusterNamespace} --values {pathToValuesYaml}`

### Pause The Citus Cluster

1. Scale down the importer in all namespaces running on the effected node pool(s)
   <br>
   `kubectl scale deployment -n {namespace} mirror-importer --replicas=0`
   <br>
2. Wait for the importer to terminate gracefully
3. Pause Citus
   <br>
   `kubectl annotate sgclusters.stackgres.io -n {namespace} --all stackgres.io/reconciliation-pause="true" --overwrite`
   <br>
   `kubectl scale sts -n {namespace} -l 'stackgres.io/cluster=true' --replicas=0`
   <br>
4. wait for Citus pods to fully terminate
   <br>
   `watch kubectl get pods -n {namespace}`

### Remove Existing Disks

1. Scale the Citus node pools down to 0 nodes
   <br>
   `gcloud container clusters resize {clusterName} --node-pool {poolName} --num-nodes 0 --location {clusterRegion} --project {gcpProjectName}`
2. Wait for node pools from previous step to scale down.
   <br>
   `watch kubectl get nodes`
3. Identify the disks that need to be replaced. Users should be empty once the nodes have successfully scaled down
   <br>
   `gcloud compute disks list --project {gcpProjectName} --filter="name~{diskPrefix}.*-zfs" --format="table(name, sizeGb, users)"`
4. Delete existing disks
   <br>
   `gcloud compute disks delete {diskName} --project {gcpProjectName} --zone {diskZone}`

### Create Disks

1. Identify the snapshots you intend to restore
   <br>
   `gcloud compute snapshots list --project {snapshotSourceProject} --filter="sourceDisk~{diskPrefixOfSource}.*-zfs" --format="table(name, diskSizeGb, sourceDisk, description)"`
2. Get mapping of where `pvc`s are located so the correct snapshot can be restored to the correct disk
   <br>
   `kubectl get pv -o json |jq -r '.items[]|select(.metadata.annotations."pv.kubernetes.io/provisioned-by"=="zfs.csi.openebs.io" and .status.phase == "Bound")|"namespace: \(.spec.claimRef.namespace) volumeName: \(.metadata.name) pvcName: \(.spec.claimRef.name) pvcSize: \(.spec.capacity.storage)  nodeId: \(.spec.nodeAffinity.required.nodeSelectorTerms[0].matchExpressions[0].values[0])"'`
3. Create a new disk for each snapshot.
   <br>
   Name the disk according to: `{diskPrefix}-{nodeId}-zfs`
   <br>
   Given the output of step 2
   <br>
   [<img src="disk-create.png"/>](disk-create.png "Map Snapshot to Disk")
   <br>
   The following commands would be executed (note in this example `diskPrefix` is `runbook`)
   <br>
   `gcloud compute disks create runbook-coordinator-us-east1-c-0-zfs --project={gcpProjectName} --zone=us-east1-c --source-snapshot=projects/{snapshotSourceProject}/global/snapshots/mainnet-na-coord-0-primary-sep-9 --type=pd-balanced`
   <br>
   `gcloud compute disks create runbook-coordinator-us-east1-d-0-zfs --project={gcpProjectName} --zone=us-east1-d --source-snapshot=projects/{snapshotSourceProject}/global/snapshots/mainnet-na-coord-1-replica-sep-9 --type=pd-balanced`
   <br>
   `gcloud compute disks create runbook-worker-us-east1-c-0-zfs --project={gcpProjectName} --zone=us-east1-c --source-snapshot=projects/{snapshotSourceProject}/global/snapshots/mainnet-na-shard0-sep-9 --type=pd-balanced`
   <br>
   `gcloud compute disks create runbook-worker-us-east1-d-0-zfs --project={gcpProjectName} --zone=us-east1-d --source-snapshot=projects/{snapshotSourceProject}/global/snapshots/mainnet-na-shard1-sep-9 --type=pd-balanced`
   <br>
   `gcloud compute disks create runbook-worker-us-east1-b-0-zfs --project={gcpProjectName} --zone=us-east1-b --source-snapshot=projects/{snapshotSourceProject}/global/snapshots/mainnet-na-shard2-sep-9 --type=pd-balanced`

4. Scale node pools for worker and coordinator back up to the original number of nodes
   <br>
   `gcloud container clusters resize {clusterName} --node-pool {poolName} --num-nodes 1 --location {clusterRegion} --project {gcpProjectName}`

### Update ZFS Datasets

Wait for zfs init `daemonset` pods to be ready
<br>
`watch kubectl get pods -n {commonNamespace} -l app=zfs-init`
<br>
Once the pods are ready, determine which `Kubernetes` node contains the data for each `nodeId`
<br>
`kubectl get pods -n {commonNamespace} -l component=openebs-zfs-node -o wide`
<br>
`kubectl get zfsnodes.zfs.openebs.io -A -o json | jq -r '.items[].metadata|"nodeId: \(.name) nodeName: \(.ownerReferences[0].name)"'`
<br>
`kubectl get pv -o json |jq -r '.items[]|select(.metadata.annotations."pv.kubernetes.io/provisioned-by"=="zfs.csi.openebs.io" and .status.phase == "Bound")|"namespace: \(.spec.claimRef.namespace) volumeName: \(.metadata.name) pvcName: \(.spec.claimRef.name) pvcSize: \(.spec.capacity.storage)  nodeId: \(.spec.nodeAffinity.required.nodeSelectorTerms[0].matchExpressions[0].values[0])"'`
<br>
`kubectl exec -it -n {commonNamespace} {zfsInitPodName} -c openebs-zfs-plugin -- bash`'
<br>
`zfs list` - to list the zfs datasets
<br>
`zfs rename {currentName} {newName}` - to rename the zfs datasets to match the volume name of the related pvc
<br>
Example:
<br>
[<img src="update-zfs-datasets.png"/>](update-zfs-datasets.png "Map PVC to Kubernetes Node")

### Start The Citus Cluster

Need to configure `hedera-mirror` chart's `values.yaml` with the sizes of the new disks

```yaml
stackgres:
  coordinator:
    persistentVolume:
      size: 256Gi
  worker:
    overrides:
      - index: 0
        pods:
          persistentVolume:
            size: 12000Gi
      - index: 1
        pods:
          persistentVolume:
            size: 14000Gi
      - index: 2
        pods:
          persistentVolume:
            size: 3200Gi
```

1. Remove the initialize id
   <br>
   `kubectl annotate endpoints -n {targetNamespace} -l 'stackgres.io/cluster=true' initialize- --overwrite`
2. Apply the changes to the `hedera-mirror` chart (disable importer for now)
   <br>
   `helm upgrade --install {releaseName} hashgraph/hedera-mirror --version {version} -n {clusterNamespace} --values {pathToValuesYaml} --set importer.enabled=false`
3. Unpause the Citus cluster
   <br>
   `kubectl annotate sgclusters.stackgres.io -n {namespace} --all stackgres.io/reconciliation-pause- --overwrite`

### Set Primary Coordinator and Workers

Primary information should be stored in the description of the snapshot you used to create the disks.
You will need to perform this step for each primary currently marked as a replica.
<br>
When the cluster starts, you may need to manually mark the primary for each cluster
<br>
Use `patronictl` to mark the primary coordinator and workers for each cluster
`kubectl exec -it -n {clusterNamespace} {releaseName}-citus-coord-0 -c patroni bash`
<br>
`patronictl failover`
<br>
You may also need to update the `sgCluster` resources by setting `primary=true` for the correct primary under
`status.podStatuses`
<br>
`kubectl get sgcluster -n {targetNamespace} -o json | jq '.items[].metadata.name'`
<br>
`kubectl edit sgcluster -n {targetNamespace} {clusterName}`

### Update Passwords

1. Retrieve the new passwords for this cluster
   <br>
   For `authenticator`, `postgres`, and `replicator` passwords:
   <br>
   `kubectl get secrets -n {targetNamespace} {releaseName}-citus-coord -o yaml |ksd`
   <br>
   For all other passwords:
   <br>
   `kubectl get secrets -n {targetNamespace} {releaseName}-passwords -o yaml |ksd`
   <br>
2. Using the `postgres` user password from step 1, update the `postgres` user password for each primary coordinator
   and worker pod. Replace the below pod names with the correct pod names for the primary coordinator and
   worker pod
   <br>
   `kubectl exec -it -n {targetNamespace} -c postgres-util  {releaseName}-citus-coord-0  -- psql -U postgres -c "alter user postgres with password ''"`
   <br>
   `kubectl exec -it -n {targetNamespace} -c postgres-util  {releaseName}-citus-shard0-0  -- psql -U postgres -c "alter user postgres with password ''"`
   <br>
   `kubectl exec -it -n {targetNamespace} -c postgres-util  {releaseName}-citus-shard1-0  -- psql -U postgres -c "alter user postgres with password ''"`
   <br>
   `kubectl exec -it -n {targetNamespace} -c postgres-util  {releaseName}-citus-shard2-0  -- psql -U postgres -c "alter user postgres with password ''"`
3. Open a session using `postgres-util` container on the primary coordinator
   <br>
   `kubectl exec -it -n {targetNamespace} -c postgres-util {releaseName}-citus-coord-{indexOfPrimaryCoordinatorInstance} -- psql -U postgres -d mirror_node`
   <br>
4. Fill in the SQL commands in the [template](#sql-commands-to-update-passwords) with the correct passwords
   and execute in the session opened in step 3

### SQL Commands to Update Passwords

```sql
insert into pg_dist_authinfo(nodeid, rolename, authinfo)
values (0, 'postgres', 'password='),
       (0, 'mirror_graphql', 'password='),
       (0, 'mirror_grpc', 'password='),
       (0, 'mirror_importer', 'password='),
       (0, 'mirror_node', 'password='),
       (0, 'mirror_rest', 'password='),
       (0, 'mirror_rest_java', 'password='),
       (0, 'mirror_rosetta', 'password='),
       (0, 'mirror_web3', 'password=') on conflict (nodeid, rolename)
do
update set authinfo = excluded.authinfo;

SELECT run_command_on_workers($cmd$
insert into pg_dist_authinfo(nodeid, rolename, authinfo)
values (0, 'postgres', 'password='),
       (0, 'mirror_graphql', 'password='),
       (0, 'mirror_grpc', 'password='),
       (0, 'mirror_importer', 'password='),
       (0, 'mirror_node', 'password='),
       (0, 'mirror_rest', 'password='),
       (0, 'mirror_rest_java', 'password='),
       (0, 'mirror_rosetta', 'password='),
       (0, 'mirror_web3', 'password=')
          on conflict (nodeid, rolename)
       do update set authinfo = excluded.authinfo;
$cmd$
);

alter user mirror_graphql with password '';
alter user mirror_grpc with password '';
alter user mirror_importer with password '';
alter user mirror_node with password '';
alter user mirror_rest with password '';
alter user mirror_rest_java with password '';
alter user mirror_rosetta with password '';
alter user mirror_web3 with password '';
alter user replicator with password '';
alter user authenticator with password '';

SELECT run_command_on_workers($cmd$
alter user mirror_graphql with password '';
$cmd$
);
SELECT run_command_on_workers($cmd$
alter user mirror_grpc with password '';
$cmd$
);
SELECT run_command_on_workers($cmd$
alter user mirror_importer with password '';
$cmd$
);
SELECT run_command_on_workers($cmd$
alter user mirror_node with password '';
$cmd$
);
SELECT run_command_on_workers($cmd$
alter user mirror_rest with password '';
$cmd$
);
SELECT run_command_on_workers($cmd$
alter user mirror_rest_java with password '';
$cmd$
);
SELECT run_command_on_workers($cmd$
alter user mirror_rosetta with password '';
$cmd$
);
SELECT run_command_on_workers($cmd$
alter user mirror_web3 with password '';
$cmd$
);
SELECT run_command_on_workers($cmd$
alter user replicator with password '';
$cmd$
);
SELECT run_command_on_workers($cmd$
alter user authenticator with password '';
$cmd$
);
```