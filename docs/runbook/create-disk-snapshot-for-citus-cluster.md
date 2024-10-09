# Create Disk Snapshot for Citus Cluster

## Problem

Need to create disk snapshots for Citus cluster(s)

## Prerequisites

- Have access to a running Citus cluster deployed by the `hedera-mirror` chart
- Have `jq` installed
- Traffic is not routed to the environment where snapshots are being taken from
- All bash commands assume your working directory is `docs/runbook/scripts`

## Steps

1. Disable traffic to the cluster if it is currently servicing requests
2. Configure kubectl to point to the `source cluster`
   <br>
   `gcloud container clusters get-credentials {clusterName} --region {clusterRegion} --project {gcpProjectName}`
3. Run script and follow along with all prompts
   <br>
   `GCP_PROJECT={gcpProjectName} ./volume-snapshot.sh`
4. Enable traffic to the cluster
