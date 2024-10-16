# Stackgres Upgrade

## Problem

After upgrading the Stackgres Helm chart version, we need to perform a security upgrade to ensure all its components
are updated in the current cluster.

## Execution

After successful deployment of the upgraded Stackgres Helm chart, we need to perform the following steps:

1. Determine the namespace and name of each sharded cluster and generate the yaml file below for each instance.

   ```
   kubectl get sgshardedclusters -A

   NAMESPACE       NAME           VERSION
   mainnet-citus   mirror-citus   16.2
   ```

2. Create a file containing the below yaml with `<namespace>` and `sgShardedCluster` replaced with the correct
   values from step one and execute the command:
   ```
   kubectl apply -n <namespace> -f - <<EOF
   apiVersion: stackgres.io/v1
   kind: SGShardedDbOps
   metadata:
     name: stackgres-upgrade
   spec:
     maxRetries: 1
     op: securityUpgrade
     scheduling:
       priorityClassName: critical
     securityUpgrade:
       method: InPlace
     sgShardedCluster: mirror-citus
   EOF
   ```
3. Verify that the clusters are annotated with the correct Stackgres version.
   ```
   kubectl get sgclusters -n <namespace> -o json | jq '.items[].metadata.annotations."stackgres.io/operatorVersion"'
   ```
4. Once the op completes successfully, you should delete the SGShardedDbOps resource(s).
   ```
   kubectl delete sgshardeddbops -n <namespace> stackgres-upgrade
   ```
