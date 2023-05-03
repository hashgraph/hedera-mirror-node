# ZFS on Google Kubernetes Engine (GKE)

## Purpose

This document outlines the steps needed to run citus in GKE using a ZFS filesystem

## Setup

### GKE Requirements

1. all the nodes must have zfs utils installed
2. The zpool has been set up for provisioning the volume
3. You have access to install RBAC components into kube-system namespace. The OpenEBS ZFS driver components are
   installed in kube-system namespace to allow them to be flagged as system critical components.

### Recommended Resource Requirements

#### Node Pools

| Category          | CPU | Memory | Attached Disk Size |
|-------------------|-----|--------|--------------------|
| Citus Coordinator | 4   | 16GB   | 256GB              |
| Citus Worker      | 8   | 32GB   | 1.25TB             |

### Infrastructure

1. Create your Kubernetes cluster
   <br>
    1. Create a node pool specific for the coordinator node
        1. Attach an additional disk to the node per
           the [recommended resource requirements](#Recommended-Resource-Requirements).
        2. Use Ubuntu with containerd
        3. add a label to node to match
       ```yaml
       nodeSelector:
         citus-role: citus-primary
       ```
    2. Create a node pool specific for the worker node(s)
        1. Attach an additional disk to the node per
           the [recommended resource requirements](#Recommended-Resource-Requirements).
        2. Use Ubuntu with containerd
        3. add a label to match
         ```yaml
         node-selector:
           citus-role: citus-worker
         ```
    3. Disable automatic upgrades
        1. There is special manual steps required for new nodes coming up and configuration steps must be run manually.

2. Create and attach a disk for each worker and coordinator node
    1. `gcloud compute disks create <disk-name> --size <size> --type pd-balanced --zone <zone> --project <project>`
    2. `gcloud compute instances attach-disk <node-name> --disk <disk-name> --zone us-central1-a`
    3. ssh to node `gcloud compute ssh --zone <zone> <vm-name> --project <project>`
    4. Get the device name for the attached disk `lsblk`
    5. `sudo zpool create zfspv-pool /dev/{DEVICE_NAME}`
3. Install the openebs ZFS operator
    1. Connect kubectl to your new GKE cluster
    2. kubectl apply -f https://raw.githubusercontent.com/openebs/zfs-localpv/master/deploy/zfs-operator.yaml
    3. Run `kubectl get pods -n kube-system -l role=openebs-zfs -o wide` and ensure you have the driver running on each
       node
4. Create the storage class
   1. You may put any zfs config value in the parameters list
5. TODO:// Install DaemonSet to setup the zfs utils and zpool

```yaml
apiVersion: storage.k8s.io/v1
kind: StorageClass
metadata:
  name: openebs-zfspv
reclaimPolicy: Retain
parameters:
  recordsize: "32k"
  fstype: "zfs"
  poolname: "zfspv-pool2"
provisioner: zfs.csi.openebs.io
```

### values.yaml

1. Review the defined [recommended resource requirements](#Recommended-Resource-Requirements). Adjust per use case.

```yaml
citus:
  enabled: true
  primary:
    nodeSelector:
      citus-role: citus-primary
    persistence:
      storageClass: openebs-zfspv
      size: 128Gi
    resources:
      requests:
        cpu: 2
        memory: 16Gi
  readReplicas:
    nodeSelector:
      citus-role: citus-worker
    resources:
      requests:
        cpu: 4
        memory: 16Gi
    persistence:
      storageClass: openebs-zfspv
      size: 1124Gi
    replicaCount: 3

importer:
  env:
    SPRING_PROFILES_ACTIVE: v2
    SPRING_FLYWAY_PLACEHOLDERS_SHARDCOUNT: "16"
```

## Maintenance

### Expanding a disk

1. Expand the disk using gcloud command or UI
2. ssh into each effected node
   1. confirm current disk stats `zfs list`
   2. `sudo partprobe`
   3. confirm additional disk space added to pool `zfs list`
3. `kubectl edit pvc -n <namespace> <pvcName>`
   1. update `spec.resources.requests.storage` to new preferred size

### Recompressing

1. Changing ZFS compression settings only effects writes going forward.
   In order to recompress, you must copy the data to a new folder.

### Updating ZFS parameters
For list of parameters see [here](https://github.com/openebs/zfs-localpv/blob/develop/docs/storageclasses.md)
1. Identify your pvc `kubectl get pvc -n <namespace>`
2. Identify your zv by matching the pvc `kubectl get zv --all-namespaces`
3. Edit the parameters on the zv `kubectl edit zv -n openebs <zvName>`
### GKE Cluster Upgrade

1. TODO

EDITING ZFS properties
https://github.com/openebs/zfs-localpv#4-zfs-property-change

Recompress data...disk must be large enough to fit a copy in new compression

## Additonal ZFS Parameters

https://github.com/openebs/zfs-localpv/blob/develop/docs/storageclasses.md

configure shard count!

## Used dependencies

[OpenEBS](https://github.com/openebs/zfs-localpv)
[OpenZFS](https://openzfs.github.io/openzfs-docs/man/7/zfsprops.7.html)