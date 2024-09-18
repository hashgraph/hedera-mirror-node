## Problem

The disk storage for the ZFS pool(s) is running out of space and needs to be increased

## Definitions

- `diskPrefix` - the prefix of the disk name. This is the value of `zfs.init.diskPrefix` in the
  `hedera-mirror-common` chart's `values.yaml`

## Prerequisites

- Have `jq` installed

## Solution

1. Identify the worker (and/or coordinator) pvc(s) that needs to be resized
   <br>
   `kubectl get pv -o json |jq -r '.items[]|select(.metadata.annotations."pv.kubernetes.io/provisioned-by"=="zfs.csi.openebs.io" and .status.phase == "Bound")|"\(.spec.claimRef.namespace) \(.spec.claimRef.name) \(.spec.capacity.storage) --> nodeId: \(.spec.nodeAffinity.required.nodeSelectorTerms[0].matchExpressions[0].values[0])"'`
2. Using the `nodeId` from the previous step, increase the disk size
   <br>
   `diskName` - {diskPrefix}-{nodeId}-zfs
   <br>
   `zone` - extracted from the `nodeId`
   <br>
   `diskSize` - the new size of the disk.
   <br>
   `gcloud compute disks resize "{diskName}" --size="{diskSize}" --zone="{zone}"`
3. Update the `hedera-mirror` chart's `values.yaml` to reflect the new disk size
   <br>
   Example:
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
   and deploy the changes. Be sure to leave wiggle room for zfs rounding
   see [here](https://github.com/openebs/zfs-localpv/blob/develop/docs/faq.md#7-why-the-zfs-volume-size-is-different-than-the-reqeusted-size-in-pvc)
