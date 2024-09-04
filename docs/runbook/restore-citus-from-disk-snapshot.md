# Restore Citus Data From Disk Snapshots

## Problem

Need to restore Citus cluster from disk snapshots

## Definitions

* `coordinator` - pod that contains the coordinator data
* `worker` - pod that contains shard data
* `citus role` - the role of the node in the Citus cluster. Can be either `coordinator` or `worker`
* `target cluster` - the Kubernetes cluster to which the disk snapshot(s) will be restored
* `source cluster` - the Kubernetes cluster where the Citus cluster(s) contained in snapshots were installed

## Prerequisites

* Snapshots of disks were created by following the [create snapshot](create-disk-snapshot-for-citus-cluster.md) runbook
* Have `jq` and `ksd`(kubernetes secret decrypter) installed
* Have the hashgraph helm repository added
  <br>
  `helm repo add hashgraph https://hashgraph.github.io//hedera-mirror-node/charts`

## Requirements

* If you have multiple Citus clusters in the `source cluster`, you will need to restore all of them if data for
  those clusters are contained in the same snapshot for the Citus cluster you are intending to restore

## Setup

Complete this setup ONLY if you are replacing disks in a current cluster with existing resources

1. Identify the snapshots you intend to restore and verify all clusters contained in the snapshots by reviewing
   the snapshot description
2. Delete the existing resources.
   This includes deleting the `SGShardedCluster`, `PersistentVolume`, `ZFSVolume`, and `PersistentVolumeClaim` resources
3. Detach and delete the disks that need to be replaced from snapshots
   see step 2 in [steps](#steps) to determine the name of the disks to delete

```bash
gcloud compute disks describe {diskName} --project {gcpProject} --zone {diskZone}
gcloud compute instances detach-disk {instanceName}  --disk {diskName} --zone {diskZone} --project {gcpProject}
gcloud compute disks delete {diskName} --project {gcpProject} --zone {diskZone}
```

## Steps

1. Configure kubectl to point to the `target cluster`
   `gcloud container clusters get-credentials {clusterName} --region {clusterRegion} --project {gcpProjectName}`
2. Create a new disk for each snapshot. Name the disk according to
   <br>
   `{diskPrefix}-{worker OR coordinator}-{zone}-{index}-zfs`
   <br>
   where `diskPrefix` is the value of `zfs.init.diskPrefix` in the common chart's `values.yaml` and `index` is an
   integer counter for each `citus role` and `zone` combination starting at 0 and incremented for each disk with
   same `citus role` in the same `zone`.
   <br>
   `gcloud compute disks create {nameOfDisk} --project={project} --zone={targetDiskZone} --source-snapshot={nameOfSnapshot}`
3. Install the common chart in the target cluster if not already installed
   <br>
   `helm upgrade --install {releaseName} hashgraph/hedera-mirror-common --version {version} --create-namespace -n {commonNamespace} --values {pathToValuesYaml}`
4. Create the namespace where the Citus cluster will be installed
   <br>
   `kubectl create namespace {clusterNamespace}`
5. Create the `PersistentVolume`, `ZFSVolume`, and `PersistentVolumeClaim` resources for each disk restored in step 2
   See [example yaml](#example-yaml) below.
   <br>
   `kubectl apply -f {pathToPvYaml}`
   <br>
   `kubectl apply -f {pathToZfsVolumeYaml} -n {commonNamespace}`
   <br>
   `kubectl apply -f {pathToPvcYaml} -n {clusterNamespace}`
6. Configure the `hedera-mirror` chart to run the postgres-util container
   (enabled by `.stackgres.(coordinator AND worker).enablePostgresUtil` in the `hedera-mirror` chart)
7. Create the Citus cluster by installing the `hedera-mirror` chart. Note you may need to wait several minutes for
   the recovery process to complete before pods are ready.
   <br>
   `helm upgrade --install {releaseName} hashgraph/hedera-mirror --version {version} -n {clusterNamespace} --values {pathToCitusValuesYaml}`
   <br>
   If you have differing disk sizes that do not match `zfs.(coordinator|worker).initialDiskSize` in
   `hedera-mirror-common` chart's `values.yaml`, be sure that you populate
   `stackgres.(coordinator|worker).overrides[statefulSetIndex].pods.persistentVolume.size` in the `hedera-mirror`
   chart's `values.yaml` with the correct size for the `coordinator` or `worker`
8. When the cluster starts, you may need to manually mark the primary for each cluster
   <br>
   `kubectl exec -it -n {clusterNamespace} {releaseName}-citus-coord-0 -c patroni bash`
   <br>
   `patronictl failover`
   <br>
   Primary information should be stored in the description of the snapshot you used to create the disks.
   You will need to perform this step for each primary currently marked as a replica.
9. You may also need to update the `sgCluster` resources by setting `primary=true` for the correct primary under
   `status.podStatuses`
   <br>
   `kubectl get sgcluster -n {targetNamespace} -o json | jq '.items[].metadata.name'`
   <br>
   `kubectl edit sgcluster -n {targetNamespace} {clusterName}`
10. [update passwords](#update-passwords)
11. Repeat steps 2-10 for each namespace where `hedera-mirror` chart is installed and contained in the restored
    snapshots

### Update Passwords

1. `Stackgres` doesn't allow for the `superuser` or `replication` password to be set by configuration so you will need
   to
   retrieve the old password from the cluster by executing the below command:
   <br>
   `kubectl exec -it -n mainnet-citus -c postgres-util  {releaseName}-citus-shard0-0  -- psql -U postgres -d mirror_node -c "select * from pg_dist_authinfo where rolename='postgres'"`
2. Open a session using `postgres-util` container on the primary coordinator
   <br>
   `kubectl exec -it -n {targetNamespace} -c postgres-util {releaseName}-citus-coord-{indexOfPrimaryCoordinatorInstance} -- psql -U postgres -d mirror_node`
   <br>
3. Use the password from step 1 to configure the below to configure communication with other clusters
   `insert into pg_dist_authinfo(nodeid, rolename, authinfo)
    values (0, 'postgres', 'password=') on conflict (nodeid, rolename)
    do
    update set authinfo = excluded.authinfo;`
4. Retrieve the new passwords for this cluster
   <br>
   `kubectl get secrets -n {targetNamespace} {releaseName}-citus-coord -o yaml |ksd`
   <br>
   `kubectl get secrets -n {targetNamespace} {releaseName}-passwords -o yaml |ksd`
   <br>
5. Fill in the SQL commands in the [template](#sql-commands-to-update-passwords) with the correct passwords
   and execute in the session opened in step 2

### SQL Commands to Update Passwords

```sql
alter user postgres with password '';

insert into pg_dist_authinfo(nodeid, rolename, authinfo)
values (0, 'postgres', 'password=')
on conflict (nodeid, rolename)
    do update set authinfo = excluded.authinfo;

SELECT run_command_on_workers($cmd$
insert into pg_dist_authinfo(nodeid, rolename, authinfo) values  
(0, 'postgres', 'password=')
on conflict (nodeid, rolename)
do update set authinfo = excluded.authinfo;
$cmd$);

SELECT run_command_on_workers($cmd$
alter user mirror_graphql with password '';
$cmd$);
SELECT run_command_on_workers($cmd$
alter user mirror_grpc with password '';
$cmd$);
SELECT run_command_on_workers($cmd$
alter user mirror_importer with password '';
$cmd$);
SELECT run_command_on_workers($cmd$
alter user mirror_node with password '';
$cmd$);
SELECT run_command_on_workers($cmd$
alter user mirror_rest with password '';
$cmd$);
SELECT run_command_on_workers($cmd$
alter user mirror_rest_java with password '';
$cmd$);
SELECT run_command_on_workers($cmd$
alter user mirror_rosetta with password '';
$cmd$);
SELECT run_command_on_workers($cmd$
alter user mirror_web3 with password '';
$cmd$);
SELECT run_command_on_workers($cmd$
alter user replicator with password '';
$cmd$);

alter user replicator with password '';
alter user mirror_graphql with password '';
alter user mirror_grpc with password '';
alter user mirror_importer with password '';
alter user mirror_node with password '';
alter user mirror_rest with password '';
alter user mirror_rest_java with password '';
alter user mirror_rosetta with password '';
alter user mirror_web3 with password '';
```

## Example YAML

### PersistentVolume

Replace the following placeholders with the correct values:
<br>
`{volumeName}` - name of the volume obtained from the snapshot description
<br>
`{pvcName}` - name of the pvc obtained from the snapshot description
<br>
`{pvcSize}` - size of the pvc obtained from the snapshot description
<br>
`{nodeId}` - the node id is `{worker OR coordinator}-{zone}-{index}` used in step 2 in [steps](#steps)
<br>
`{targetNamespace}` - the namespace where the citus cluster will be installed
<br>
add an entry under `items` for each volume described in the snapshot description

```yaml
apiVersion: v1
items:
  - apiVersion: v1
    kind: PersistentVolume
    metadata:
      name: {volumeName}
    spec:
      accessModes:
        - ReadWriteOnce
      capacity:
        storage: {pvcSize}
      claimRef:
        apiVersion: v1
        kind: PersistentVolumeClaim
        name: {pvcName}
        namespace: {targetNamespace}
      csi:
        driver: zfs.csi.openebs.io
        fsType: zfs
        volumeAttributes:
          openebs.io/cas-type: localpv-zfs
          openebs.io/poolname: zfspv-pool
        volumeHandle: {volumeName}
      nodeAffinity:
        required:
          nodeSelectorTerms:
            - matchExpressions:
                - key: openebs.io/nodeid
                  operator: In
                  values:
                    - {nodeId}
      persistentVolumeReclaimPolicy: Retain
      storageClassName: zfs
      volumeMode: Filesystem
kind: List
metadata:
  resourceVersion: ""
```

### ZFSVolume

Note if the defaults for `zfs.parameters` in the `hedra-mirror-common`'s values were changed,
you will also need to change the values under spec to match those parameters

replace the following placeholders with the correct values:
<br>
`{volumeName}` - name of the volume obtained from the snapshot description
<br>
`{volumeCapacity}` - capacity of the `zfsVolume` obtained from the snapshot description
<br>
`{nodeId}` - the node id is `{worker OR coordinator}-{zone}-{index}` used in step 2 in [steps](#steps)
<br>
`{commonNamespace}` - the namespace where the `hedera-mirror-common` chart is installed
<br>
add an entry under `items` for each volume described in the snapshot description

```yaml
apiVersion: v1
items:
  - apiVersion: zfs.openebs.io/v1
    kind: ZFSVolume
    metadata:
      finalizers:
        - zfs.openebs.io/finalizer
      labels:
        kubernetes.io/nodename: {nodeId}
      name: {volumeName}
      namespace: {commonNamespace}
    spec:
      capacity: "{volumeCapacity}"
      compression: zstd-6
      fsType: zfs
      ownerNodeID: {nodeId}
      poolName: zfspv-pool
      recordsize: 32k
      volumeType: DATASET
    status:
      state: Ready
kind: List
metadata:
  resourceVersion: ""
```

### PersistentVolumeClaim

Replace the following placeholders with the correct values:
<br>
`{pvcName}` - name of the pvc obtained from the snapshot description
<br>
`{pvcSize}` - size of the pvc obtained from the snapshot description
<br>
`{targetNamespace}` - the namespace where the citus cluster will be installed
<br>
add an entry under `items` for each volume described in the snapshot description

```yaml
apiVersion: v1
items:
  - apiVersion: v1
    kind: PersistentVolumeClaim
    metadata:
      name: {pvcName}
      namespace: {targetNamespace}
    spec:
      accessModes:
        - ReadWriteOnce
      resources:
        requests:
          storage: {pvcSize}
      storageClassName: zfs
kind: List
metadata:
  resourceVersion: ""
```
