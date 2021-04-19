# Continuous Deployment

## FluxCD 2
```
export GITHUB_TOKEN=...
brew install fluxcd/tap/flux
flux check --pre
flux bootstrap github --owner=hashgraph --repository=hedera-mirror-node --branch=deploy-flux2 --team=hedera-mirror-node --context=preprod --path=clusters/preprod

## Sealed Secrets
brew install kubeseal
helm upgrade -i --wait -n flux sealed-secrets stable/sealed-secrets
kubeseal --controller-namespace=flux-system --controller-name=sealed-secrets -o yaml <secret.yaml >sealed-secret.yaml
```

