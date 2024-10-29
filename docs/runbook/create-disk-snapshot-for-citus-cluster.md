# Create Disk Snapshot for Citus Cluster

## Problem

Need to create disk snapshots for Citus cluster(s)

## Prerequisites

- Have access to a running Citus cluster deployed by the `hedera-mirror` chart 
- Have `jq` installed
- All bash commands assume your working directory is `docs/runbook/scripts`
- The kubectl context is set to the cluster you want to create snapshots from

## Solution

Run script and follow along with all prompts
```bash
./volume-snapshot.sh
```
