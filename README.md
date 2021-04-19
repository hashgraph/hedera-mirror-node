# Continuous Deployment Setup

## ArgoCD

```bash
brew install argocd
kubectl create namespace argocd
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj-labs/argocd-image-updater/master/manifests/install.yaml
kubectl patch statefulset argocd-application-controller -n argocd --type=json -p '[{"op": "add", "path": "/spec/template/spec/containers/0/command/-", "value": "--app-resync=60"}]'
```

## Sealed Secrets

```bash
brew install kubeseal
helm repo add sealed-secrets https://bitnami-labs.github.io/sealed-secrets
helm upgrade -i --wait -n argocd sealed-secrets sealed-secrets/sealed-secrets
kubeseal --controller-namespace=argocd --controller-name=sealed-secrets -o yaml <secret.yaml >sealed-secret.yaml
```

## Applications

```bash
kubectl apply -f clusters/<name>.yaml
```

