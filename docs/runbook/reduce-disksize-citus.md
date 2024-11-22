## Problem

Citus disks are over provisioned and need to be reduced in size.

## Prerequisites

- All Citus PVCS are defined are sized in Gi
- `jq` is installed
- The kubectl context is set to the cluster you want to reduce disk size for from

## Solution

Run script and follow along with all prompts

```bash
./reduce-citus-disk-size.sh
```
