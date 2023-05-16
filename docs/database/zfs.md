# ZFS on Google Kubernetes Engine (GKE)

## Purpose

This document outlines the steps needed to run citus in GKE using a ZFS filesystem

## Pre-Requisites

All listed commands with relative paths are assumed to be run from the repository root.

1. kubectl installed
2. Helm installed
3. Access to create clusters in GKE

### GKE Requirements

1. all the nodes must have ZFS utils installed (handled by installing the common helm chart with ZFS enabled)
2. The zpool has been set up for provisioning the volume (handled by installing the common helm chart with ZFS enabled)
3. !Important! Node pools that have ZFS volumes need to have a service account configured that allows them permission to
   create and attach disks. The daemonset included in the helm chart performs this process.
    1. This can be done using the default service account for testing and just enabling the following
       scope `--scopes "https://www.googleapis.com/auth/cloud-platform"`
    2. additional details for configuring the service account can be
       found [here](https://cloud.google.com/solutions/automatically-bootstrapping-gke-nodes-with-daemonsets#provision_a_service_account_to_manage_gke_clusters)
4. Auto upgrade is disabled for the cluster. Special steps need to be taken to ensure the volumes are created
   appropriately

## Security

This guide instructs you to create a service account to initialize nodes running ZFS pools. Ensure that this service
account is limited to only components it needs (query instance metadata, create/list disks)

## Setup

### Infrastructure

1. Create your Kubernetes cluster
   <br>
    1. Create a node pool specific for the coordinator node(s)
        1. Use Ubuntu with containerd
        2. add a label and taint to node to match
       ```yaml
       tolerations:
       - key: zfs
         operator: Equal
         value: "true"
         effect: NoSchedule
       nodeSelector:
         citus-role: coordinator
         csi-type: zfs
          ```
    2. Create a node pool specific for the worker node(s)
        1. Use Ubuntu with containerd
        2. add a label and taint to match
         ```yaml
       tolerations:
       - key: zfs
         operator: Equal
         value: "true"
         effect: NoSchedule
       node-selector:
         citus-role: worker
         csi-type: zfs
         ```

## Install

### Install the ZFS Driver

Before performing these steps, ensure that your kubectl is pointing to the correct cluster `kubectl config get-contexts`

1. `cd charts`
2. `helm dependency build hedera-mirror-common/`
3. `helm upgrade --install  mirror ./hedera-mirror-common -f <valuesFile> --create-namespace --namespace common`
4. Update the openEBS CRDs (You can skip this step if you plan to not use zstd compression)
    1. edit the compression regex to include zstd compression `zstd|zstd-[1-9]|zstd-1[0-9]`
        1. `kubectl edit crd zfsvolumes.zfs.openebs.io`
        2. `kubectl edit crd zfsrestores.zfs.openebs.io`
        3. `kubectl edit crd zfssnapshots.zfs.openebs.io`

#### values.yaml

```yaml
zfs:
  enabled: true
```

### Install Citus

1. `cd charts`
2. `helm dependency build hedera-mirror/`
3. `helm upgrade --install  mirror ./hedera-mirror -f <valuesFile> --create-namespace --namespace citus`

#### values.yaml

```yaml
citus:
  enabled: true
  primary:
    nodeSelector:
      citus-role: coordinator
    tolerations:
      - key: zfs
        operator: Equal
        value: "true"
        effect: NoSchedule
    persistence:
      storageClass: zfs
      size: 128Gi
    resources:
      requests:
        cpu: 2
        memory: 8Gi
  readReplicas:
    tolerations:
      - key: zfs
        operator: Equal
        value: "true"
        effect: NoSchedule
    nodeSelector:
      citus-role: worker
    resources:
      requests:
        cpu: 4
        memory: 16Gi
    persistence:
      storageClass: zfs
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
For a guide on modifying the parameters, please follow the
guide [here](https://github.com/openebs/zfs-localpv#4-zfs-property-change)

1. Identify your pvc `kubectl get pvc -n <namespace>`
2. Identify your zv by matching the pvc `kubectl get zv --all-namespaces`
3. Edit the parameters on the zv `kubectl edit zv -n <namespace> <zvName>`

### Deleting a pool on a node

To delete a zpool on the node for a pvc, you must identify the pvc in question `kubectl get pvc --all-namespaces` and
find the corresponding zvolume `kubectl get zv --all-namespaces` then you may delete the volume
using `kubectl delete zv -n <namespace> <zVolumeName>`. This will do the cleanup on the node itself and make the space
available in the zpool again

### GKE Cluster Upgrade

1. Take note of where pods using the ZFS volumes are being scheduled. You will need to know the node a particular
   worker/coordinator was scheduled on and what zone it is in.
2. Uninstall citus chart and any services that depend on it.
3. Delete the ZFS persistent volumes and persistent volume claims that are relevant to the decommissioned nodes
4. Follow the steps [here](https://cloud.google.com/kubernetes-engine/docs/how-to/upgrading-a-cluster#upgrading-nodes)
   to perform the upgrade of the cluster and node pools
5. Wait for the new nodes to come up and for the init containers to install ZFS tools on the new nodes
6. Install just citus chart again
7. Wait for citus to be ready
8. Kill citus again (we just wanted it to create the new PV and PVCs)
9. For each new node that comes up, attach the old disk to the new vm
    1. `gcloud compute instances attach-disk <instanceName> --disk <oldInstanceName>-zfs --zone us-central1-f --project <project>`
    2. ssh to the node and run `zfs list` take node of the poolnames and pools
    3. destroy existing pool `sudo zpool destroy <poolName>`
    4. Now list pools available for import `sudo zpool import`
    5. perform the import `sudo zpool import <poolname> -f`
    6. rename the imported pool to match the name of the one previously
       destroyed `sudo zfs rename <poolName>/<pvcId> to <poolName>/<idShownInStep2>`
10. Ready to deploy all charts

## Used dependencies

[OpenEBS Helm](https://openebs.github.io/zfs-localpv/)