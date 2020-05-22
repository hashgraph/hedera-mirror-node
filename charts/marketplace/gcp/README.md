# Overview

This section contains logic to support the development and deployment of the Hedera Mirror Node in a Kubernetes cluster in GCP Marketplace.

The module takes in the wrapper hedera-mirror Helm chart and bundles it into the required deployer image which is built and pushed to GCR repo on maven deploy.

# Setup

## Prerequisites

### Command line tools

- [gcloud](https://cloud.google.com/sdk/gcloud/)
- [kubectl](https://kubernetes.io/docs/tasks/tools/install-kubectl/)
- [docker](https://docs.docker.com/install/)
- [helm](https://helm.sh/docs/using_helm/#installing-helm)
- [git](https://git-scm.com/book/en/v2/Getting-Started-Installing-Git)
- [mpdev](https://github.com/GoogleCloudPlatform/marketplace-k8s-app-tools/blob/master/docs/mpdev-references.md)

### Kubernetes Setup
TBD

## Building the deployment container

Apps are required to supply a 'deployer' deployment container image which is used in UI-based deployment.
The image should extend from one of the base images provided in the marketplace-k8s-app-tools repository.
This solution currently extends gcr.io/cloud-marketplace-tools/k8s/deployer_helm

Our deployer image build logic is specified in [Dockerfile](Dockerfile)

To deploy the image run the following command from the root folder

    ./mvnw clean deploy -N -Ddocker.skip.deployer=false

Additional option params exist to specify the needed marketplace tag and desired application version

    ./mvnw clean deploy -N -Ddocker.skip.deployer=false -Ddocker.tags.0=0.12 -Ddocker.tag.version=0.12.0-rc1

## Local Marketplace Verification
The mpdev tool is provided by Google marketplace to help verify the marketplace solution and install it.

### Verify Marketplace Solution

    export REGISTRY=<insert registry location> e.g. gcr.io/mirrornode
    export TAG=<insert version tag> e.g. 0.12.0.-rc1
    mpdev verify --deployer=$REGISTRY/deployer:<TAG>

### Install Marketplace Solution
Assumes an existing kubernetes cluster has been setup
For simplification set REGISTRY and TAG environment variables

    export REGISTRY=<insert registry location> e.g. gcr.io/mirrornode
    export TAG=<insert version tag> e.g. 0.12.0.-rc1

To install run the command below. Note the properties in the 'required' section of the [schema.yaml](schema.yaml) file must be specified

    mpdev install --deployer=$REGISTRY/deployer:<TAG> --parameters='{"name": <appName>, "namespace": <existing kubernetes cluster namespace>, \
        "grpc.config.hedera.mirror.grpc.db.password": "mirror_grpc_pass", \
        "importer.config.hedera.mirror.importer.db.password": "mirror_node_pass", \
        "importer.config.hedera.mirror.importer.db.restPassword": "mirror_api_pass", \
        "rest.config.hedera.mirror.rest.db.password": "mirror_api_pass", \
        "postgresql.postgresql.repmgrPassword": "password", \
        "postgresql.postgresql.replicaCount": 2, \
        "importer.config.hedera.mirror.importer.db.restPassword": \
        "mirror_api_pass", "postgresql.pgpool.replicaCount": 2}'

### Marketplace Solution Cleanup
Once installed in a kubernetes cluster the market place solution can be cleaned up with the following kubectl commands

    kubectl delete deployment,statefulset,service,configmap,serviceaccount,clusterrole,clusterrolebinding,application,job --namespace <namespace> --selector app.kubernetes.io/name=<app name>

The solution utilizes persistent volume claims for the importer and postgres.

    kubectl delete pvc data-apptest-1-importer-0  data-postgresql-postgresql-0 data-postgresql-postgresql-1`
The above assumes 2 postgres-repmgr replicas and a single importer replica.
Run a `kubectl get pvc` to verify if others pvc resources should be removed, as above assumes 2 postgres-repmgr and 1 importer replica

## Deployed Postgres images
TBD
