# Restore Citus Data From Disk Snapshots

## Problem

Need to restore Citus cluster from disk snapshots

## Prerequisites

- Snapshots of disks were created by following the [create snapshot](create-disk-snapshot-for-citus-cluster.md) runbook
- Have `jq` and `ksd`(kubernetes secret decrypter) installed
- The snapshots are from a compatible version of `postgres`
- The `target cluster` has a running `hedera-mirror` chart with Stackgres enabled
- The `target cluster` you are restoring to doesn't have any pvcs with a size larger than the size of the pvc in the
  snapshot. You can't decrease the size of a pvc. If needed, you can delete the existing cluster in the `target cluster`
  and redeploy the `hedera-mirror` chart with the default disk sizes.
- If you have multiple Citus clusters in the `target cluster`, you will need to restore all of them
- All bash commands assume your working directory is `docs/runbook/scripts`
- Only a single citus cluster is installed per namespace

## Steps

1. Disable traffic to the cluster if it is currently servicing requests
2. Configure kubectl to point to the `target cluster`
   <br>
   `gcloud container clusters get-credentials {clusterName} --region {clusterRegion} --project {gcpProjectName}`
3. Install the common chart if not installed already
   <br>
   `helm upgrade --install {releaseName} hashgraph/hedera-mirror-common --version {version} --create-namespace -n {commonNamespace} --values {pathToValuesYaml}`
   <br>
4. Create the Citus cluster by installing the `hedera-mirror` chart if not installed already.
   <br>
   `helm upgrade --install {releaseName} hashgraph/hedera-mirror --version {version} -n {clusterNamespace} --values {pathToCitusValuesYaml}`
   <br>
5. Find the epoch timestamp of the snapshot you intend to restore
   <br>
   `gcloud compute snapshots list --project "${GCP_SNAPSHOT_PROJECT}" --format="table(name, diskSizeGb, sourceDisk, description)"`
6. Run script and follow along with all prompts
   <br>
   `SNAPSHOT_ID=${SNAPSHOT_EPOCH} GCP_PROJECT=${TARGET_CLUSTER_PROJECT} GCP_K8S_CLUSTER_NAME=${TARGET_CLUSTER_NAME} GCP_K8S_CLUSTER_REGION=${TARGET_CLUSTER_REGION} GCP_SNAPSHOT_PROJECT=${GCP_SNAPSHOT_PROJECT} ./restore-volume-snapshot.sh`
7. Verify cluster health by running queries
   <br>
   `kubectl exec -it -n {clusterNamespace} {releaseName}-citus-coord-0 -- psql -U mirror_rest -d mirror_node -c "select * from transaction limit 10"`
   <br>
  `kubectl exec -it -n {clusterNamespace} {releaseName}-citus-coord-0 -- psql -U mirror_node -d mirror_node -c "select * from transaction limit 10"`
8. Apply the changes you were prompted to save in the `restore-volume-snapshot.sh` script
9. Enable traffic to the cluster
