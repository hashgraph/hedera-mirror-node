# Stackgres Security Upgrade

## Problem

After upgrading the Stackgres Helm chart version, we need to perform a security upgrade to ensure all components are
loaded to current cluster.

## Execution

After successful upgrade of the Stackgres Helm chart, we need to perform the following steps to ensure the security of
the cluster:

1. Determine the namespace and name of each sharded cluster and generate the yaml file below for each instance.
   `kubectl get sgshardedclusters -A -o json | jq '.items[].metadata|"name: \(.name) namespace: \(.namespace)"'`
2. Create a file containing the below yaml with <namespace> and <nameOfShardedCluster> replaced with the correct values
   from step 1 and execute the command
   `kubectl apply -f <filename> -n <namespace>`
3. Verify that the clusters are annotated with the correct Stackgres version.
   `kubectl get sgclusters -A -o json | jq '.items[].metadata.annotations."stackgres.io/operatorVersion"`
4. Once the op completes successfully, you should delete the SGShardedDbOps resource(s).
   `kubectl delete sgshardeddbops -n <namespace> security-upgrade`

## Yaml

```yaml
apiVersion: stackgres.io/v1
kind: SGShardedDbOps
metadata:
  name: security-upgrade-<nameOfShardedCluster>
  namespace: <namespace>
spec:
  sgShardedCluster: <nameOfShardedCluster>
  op: securityUpgrade
  maxRetries: 1
  securityUpgrade:
    mode: InPlace
```
    
