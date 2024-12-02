# Restore Citus Data From StackGresShardedBackup

## Problem

Need to restore Citus cluster from a StackGres sharded backup

## Prerequisites

- Have `jq` and `ksd`(kubernetes secret decrypter) installed
- The cluster has a running Citus cluster deployed with `hedera-mirror` chart
- StackGresShardedCluster backup is enabled
- All bash commands assume your working directory is `docs/runbook/scripts`
- Only a single citus cluster is installed per namespace
- The kubectl context is set to the cluster you want to restore backup to

## Steps

Run script and follow along with all prompts

```bash
./restore-stackgres-backup.sh
```
