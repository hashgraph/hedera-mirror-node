# Helm Chart

Installs the Hedera Mirror Node Helm wrapper chart. This chart will install the three mirror node components:

- [Hedera Mirror Importer](hedera-mirror-importer)
- [Hedera Mirror GRPC API](hedera-mirror-grpc)
- [Hedera Mirror REST API](hedera-mirror-rest)

## Requirements

- [Helm 3](https://helm.sh)
- [Kubernetes 1.17+](https://kubernetes.io)

Set environment variables that will be used for the remainder of the document:

export RELEASE="mirror1"

## Install

To install the wrapper chart:

```shell script
$ helm repo add hedera https://hashgraph.github.io/hedera-mirror-node/charts
$ helm upgrade --install "${RELEASE}" hedera/hedera-mirror
```

## Testing

To verify the chart installation is successful, you can run the helm tests. These tests are not automatically executed
by helm on install/upgrade, they have to be executed manually:

```shell script
helm test "${RELEASE}"
```

## Using

All of the APIs and dashboards can be accessed via a single IP. To get the load balancer IP:
```shell script
  export SERVICE_IP=$(kubectl get service "${RELEASE}-traefik" -o jsonpath="{.status.loadBalancer.ingress[0].ip}")
```

To access the GRPC API (using [grpcurl](https://github.com/fullstorydev/grpcurl)):
```shell script
  grpcurl -plaintext ${SERVICE_IP}:80 list
```

To access the REST API:
```shell script
  curl -s "http://${SERVICE_IP}:80/api/v1/transactions?limit=1"
```

To view the Grafana dashboard:
```shell script
  open "http://${SERVICE_IP}:80/grafana"
```

## Uninstall

To remove all the Kubernetes components associated with the chart and delete the release:

```shell script
$ helm delete "${RELEASE}"
```

The above command does not delete any of the underlying persistent volumes. To delete all the data associated with this release:

```shell script
$ kubectl delete $(kubectl get pvc -o name)
```

## Troubleshooting

To troubleshoot a pod, you can view its log and describe the pod to see its status. See the
[kubectl](https://kubernetes.io/docs/reference/kubectl/overview/) documentation for more commands.

```shell script
$ kubectl describe pod "${RELEASE}-importer-0"
$ kubectl logs -f --tail=100 "${RELEASE}-importer-0"
$ kubectl logs -f --prefix --tail=10 -l app.kubernetes.io/name=importer
```

To change application properties without restarting, you can create a
[ConfigMap](https://kubernetes.io/docs/tasks/configure-pod-container/configure-pod-configmap/#create-configmaps-from-files)
named `hedera-mirror-grpc` or `hedera-mirror-importer` and supply an `application.yaml` or `application.properties`.
Note that some properties that are used on startup will still require a restart.

```shell script
$ echo "logging.level.com.hedera.mirror.grpc=TRACE" > application.properties
$ kubectl create configmap hedera-mirror-grpc --from-file=application.properties
```

Dashboard and metrics can be viewed via [Grafana](https://grafana.com). To access, get the external IP and open it in a browser:

```shell script
$ open "http://$(kubectl get service "${RELEASE}-grafana" -o jsonpath='{.status.loadBalancer.ingress[0].ip}')"
```

To connect to the database and run queries:

```shell script
$ kubectl exec -it "${RELEASE}-postgres-postgresql-0" -c postgresql -- psql -d mirror_node -U mirror_node
```
