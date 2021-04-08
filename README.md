# Continuous Deployment

## Flux v1

```console
brew install fluxctl kubeseal
helm repo add fluxcd https://charts.fluxcd.io
helm repo add stable https://kubernetes-charts.storage.googleapis.com
kubectl create namespace flux
kubectl apply -f https://raw.githubusercontent.com/fluxcd/helm-operator/v1.2.0/chart/helm-operator/crds/helmrelease.yaml
kubectl apply -f flux/priorityclass.yaml
helm upgrade -i --wait -n flux -f flux/flux.yaml flux fluxcd/flux
helm upgrade -i --wait -n flux -f flux/flux-helm.yaml flux-helm fluxcd/helm-operator
helm upgrade -i --wait -n flux sealed-secrets stable/sealed-secrets
kubeseal --controller-namespace=flux --controller-name=sealed-secrets -o yaml <secret.yaml >sealed-secret.yaml
```

## Flux v2
```
brew install fluxcd/tap/flux kubeseal
flux check --pre
flux install
flux create source git hedera-mirror-node --branch=deploy --interval=1m --url=https://github.com/hashgraph/hedera-mirror-node.git --export > ./flux/gitrepository.yaml

### Sealed Secrets
flux create source helm sealed-secrets --interval=1h --url=https://bitnami-labs.github.io/sealed-secrets --export > ./sealed-secrets/helmrepository.yaml
flux create helmrelease sealed-secrets --interval=1h --release-name=sealed-secrets --source=HelmRepository/sealed-secrets --chart=sealed-secrets --chart-version="1.15.x" --export > ./sealed-secrets/helmrelease.yaml
kubeseal --controller-namespace=flux-system --controller-name=sealed-secrets -o yaml <secret.yaml >sealed-secret.yaml
```

