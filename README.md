# Continuous Deployment

This branch contains declarative configuration that represents the desired state of all Hedera managed mirror node environments.
The practice of using Git as a single source of truth for declarative infrastructure and applications is called [GitOps](https://www.gitops.tech).
To this end, we use [Flux](https://fluxcd.io) in our Kubernetes clusters to manage our deploys.

## Setup

### Kubernetes

Create a standard GKE cluster with at least Kubernetes 1.17.

### Sealed Secrets

```bash
brew install kubeseal
helm repo add sealed-secrets https://bitnami-labs.github.io/sealed-secrets
kubectl create namespace flux-system
helm upgrade -i --wait -n flux-system sealed-secrets sealed-secrets/sealed-secrets
kubeseal --controller-namespace=flux-system --controller-name=sealed-secrets -o yaml <secret.yaml >sealed-secret.yaml
```

### Flux v2

```bash
export GITHUB_TOKEN=...
brew install fluxcd/tap/flux
flux check --pre
flux bootstrap github --owner=hashgraph --repository=hedera-mirror-node --branch=deploy-flux2 --team=hedera-mirror-node --context=preprod --path=clusters/preprod --private=false
```

### Upgrade

```bash
brew upgrade
flux bootstrap github --owner=hashgraph --repository=hedera-mirror-node --branch=deploy-flux2 --team=hedera-mirror-node --context=preprod --path=clusters/preprod
```

