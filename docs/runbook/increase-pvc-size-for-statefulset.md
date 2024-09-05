# Statefulset PVC Volume Size Increase

## Problem

There are times when volumes of a stateful set may need to be increased. Some deployments, such as loki can't do this
automatically via chart config.

## Pre-requisites

- kubectl is pointing to the cluster containing the stateful set you wish to increase the volume size for

### Increase PVC Size

1. `kubectl delete sts --cascade=orphan -n {TARGET_NAMESPACE} {STATEFUL_SET_NAME}`
2. Run patch PVC for each PVC in stateful set
   `kubectl patch pvc -n common -p '{"spec":{"resources":{"requests":{"storage":"{NEW_SIZE}"}}}}' {PVC_NAME}`
3. Configure the new size in the `volumeClaimTemplates` section of the stateful set manifest
4. If using flux, `flux reconcile helmrelease {RELEASE_NAME} -n {TARGET_NAMESPACE} --force --timeout 30m`. If using
   helm, `helm upgrade --install {RELEASE_NAME} {CHART_NAME} -n {TARGET_NAMESPACE}`
5. `kubectl rollout restart sts -n {TARGET_NAMESPACE} {STATEFUL_SET_NAME}`
