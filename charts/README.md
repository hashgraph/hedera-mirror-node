# Helm Chart

Installs the Hedera Mirror Node Helm wrapper chart. This chart will install the three mirror node components:

- [Hedera Mirror Importer](hedera-mirror-importer)
- [Hedera Mirror GRPC API](hedera-mirror-importer)
- [Hedera Mirror REST API](hedera-mirror-importer)

## Requirements

- [Helm 3](https://helm.sh)
- [Kubernetes 1.17+](https://kubernetes.io)

## Install

To install the wrapper chart with a release name of `mirror`:

```shell script
$ helm upgrade --install mirror charts/hedera-mirror
```

## Uninstall

To remove all the Kubernetes components associated with the chart and delete the release:

```shell script
$ helm delete mirror
```

This command does not delete any of the underlying persistent volumes. To delete all the data associated with this release:

```shell script
$ kubectl delete $(kubectl get pvc -o name)
```
