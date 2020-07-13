# Continuous Deployment

## Setup

```console
brew install fluxctl kubeseal
helm repo add fluxcd https://charts.fluxcd.io
helm repo add stable https://kubernetes-charts.storage.googleapis.com
kubectl create namespace flux
kubectl apply -f https://raw.githubusercontent.com/fluxcd/helm-operator/v1.1.0/chart/helm-operator/crds/helmrelease.yaml
helm upgrade -i --wait -n flux -f flux/flux.yaml flux fluxcd/flux
helm upgrade -i --wait -n flux -f flux/flux-helm.yaml flux-helm fluxcd/helm-operator
helm upgrade -i --wait -n flux sealed-secrets stable/sealed-secrets
kubeseal --controller-namespace=flux --controller-name=sealed-secrets -o yaml <secret.yaml >sealed-secret.yaml
```

