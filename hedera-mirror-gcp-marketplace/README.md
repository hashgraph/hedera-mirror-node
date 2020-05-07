# Overview

This submodule contains logic to support the development and deployment of the Hedera Mirror Node in a Kubernetes cluster in GCP Marketplace.

The module takes in the wrapper hedera-mirror Helm chart and bundles it into the required deployer image which is built and pushed to GCR repo on maven deploy.

# Setup

## Prerequisites

### Command line tools

- [gcloud](https://cloud.google.com/sdk/gcloud/)
- [kubectl](https://kubernetes.io/docs/tasks/tools/install-kubectl/)
- [docker](https://docs.docker.com/install/)
- [helm](https://helm.sh/docs/using_helm/#installing-helm)
- [git](https://git-scm.com/book/en/v2/Getting-Started-Installing-Git)

### Kubernetes Setup
TBD

### MPDev
TBD

# Building the deployment container

Apps are required to supply a 'deployer' deployment container image which is used in UI-based deployment.
The image should extend from one of the base images provided in the marketplace-k8s-app-tools repository.
This solution currently extends gcr.io/cloud-marketplace-tools/k8s/deployer_helm

TBD
