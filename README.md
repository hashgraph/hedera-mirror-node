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
export GITHUB_TOKEN=...
brew install fluxcd/tap/flux kubeseal
flux check --pre
flux bootstrap github --owner=hashgraph --repository=hedera-mirror-node --branch=deploy2 --team=hedera-mirror-node --context=preprod --path=clusters/preprod

### Sealed Secrets
kubeseal --controller-namespace=flux-system --controller-name=sealed-secrets -o yaml <secret.yaml >sealed-secret.yaml
```

