# Continuous Deployment

This branch contains declarative configuration that represents the desired state of all Hedera managed mirror node environments.
The practice of using Git as a single source of truth for declarative infrastructure and applications is called [GitOps](https://www.gitops.tech).
To this end, we use [Flux](https://fluxcd.io) in our Kubernetes clusters to manage our deploys.

## Install

### Kubernetes

Create a standard GKE cluster with at least Kubernetes 1.19.

### Sealed Secrets

```bash
brew install kubeseal
helm repo add sealed-secrets https://bitnami-labs.github.io/sealed-secrets
kubectl create namespace flux-system
helm upgrade -i --wait -n flux-system sealed-secrets sealed-secrets/sealed-secrets
```

### Flux v2

```bash
export GITHUB_TOKEN=...
export CONTEXT=preprod
brew install fluxcd/tap/flux
flux check --pre
flux bootstrap github --owner=hashgraph --repository=hedera-mirror-node --branch=deploy --team=hedera-mirror-node --context="${CONTEXT}" --path="clusters/${CONTEXT}" --private=false --interval=1m
```

## Configure

Use the `kubseal` command to connect to the in-cluster sealed-secrets controller to encrypt local secrets, then commit the sealed secret to git.

```bash
kubeseal --controller-namespace=flux-system --controller-name=sealed-secrets -o yaml <secret.yaml >sealed-secret.yaml
git add sealed-secret.yaml
```

## Upgrade

```bash
brew upgrade
flux bootstrap github --owner=hashgraph --repository=hedera-mirror-node --branch=deploy --team=hedera-mirror-node --context="${CONTEXT}" --path="clusters/${CONTEXT}" --private=false --interval=1m
```

## Restore

If just a particular workload or a namespace has problems, Flux should automatically reconcile it to restore it to its proper state.
If the entire cluster is corrupted or lost, it will need to be restored manually using the below instructions.
Since everything but the database and the sealed secrets private key is stored in git, restoring the cluster is pretty straight forward.
Simply install sealed secrets, restore the backed up private key then install Flux and the cluster will reconstitute itself.

1. Install [Sealed Secrets](#sealed-secrets)
2. `kubectl scale -n flux-system --replicas=0 deployment sealed-secrets`
3. `kubectl delete secret -n flux-system -l sealedsecrets.bitnami.com/sealed-secrets-key=active`
4. `kubectl apply -f <backed up sealed-secrets key>`
5. `kubectl scale -n flux-system --replicas=1 deployment sealed-secrets`
6. Install [Flux](#flux-v2)

## Testing

minikube start
kubectl create ns test
flux install
flux create source helm hedera-mirror-node --url=https://hashgraph.github.io/hedera-mirror-node/charts -n test
flux create helmrelease mirror -n test --source=HelmRepository/hedera-mirror-node --chart=hedera-mirror

