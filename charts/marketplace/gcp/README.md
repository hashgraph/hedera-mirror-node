# Overview

This folder contains logic to support the development and deployment of the Hedera Mirror Node in a Kubernetes cluster in [Google Cloud Platform Marketplace](https://console.cloud.google.com/marketplace).
It takes in the wrapper `hedera-mirror` Helm chart and bundles it into the required deployer image which is built and pushed to Google Container Registry on maven deploy.

# Setup

A Google Kubernetes Engine (GKE) cluster is required to verify and test the deployment of the Marketplace application. Follow the GKE
[quickstart](https://cloud.google.com/kubernetes-engine/docs/quickstart) to create a test cluster.

## Prerequisites

- [docker](https://docs.docker.com/install/)
- [gcloud](https://cloud.google.com/sdk/gcloud/)
- [kubectl](https://kubernetes.io/docs/tasks/tools/install-kubectl/)
- [mpdev](https://github.com/GoogleCloudPlatform/marketplace-k8s-app-tools/blob/master/docs/mpdev-references.md)

# Building

## Deployer

Apps are required to supply a `deployer` container image which is used in UI-based deployment.
The image should extend from one of the base images provided in the marketplace-k8s-app-tools registry.
This solution currently extends `gcr.io/cloud-marketplace-tools/k8s/deployer_helm`. Our deployer image build logic
is specified in the [Dockerfile](Dockerfile).

To deploy the image run the below command from the root folder. Additional optional parameters exist to specify the
needed marketplace tag and desired application version:

    ./mvnw clean deploy -N -Ddocker.skip.deployer=false -Ddocker.tags.0=0.12 -Ddocker.tag.version=0.12.0-rc1

## Third Party Images

GCP Marketplace restricts applications to images pulled from the Marketplace registry. Since the mirror node uses some
third party images like PostgreSQL, we need to re-publish these images to our registry and keep them up to date. To do so,
set `MIRROR_VERSION` to the current mirror node version and run the following (replace `latest` with a specific version, if needed):

    docker pull bitnami/postgresql-repmgr:latest
    docker tag bitnami/postgresql-repmgr:latest gcr.io/mirrornode/hedera-mirror-node/postgresql-repmgr:${MIRROR_VERSION}
    docker push gcr.io/mirrornode/hedera-mirror-node/postgresql-repmgr:${MIRROR_VERSION}

    docker pull bitnami/pgpool:latest
    docker tag bitnami/pgpool:latest gcr.io/mirrornode/hedera-mirror-node/pgpool:${MIRROR_VERSION}
    docker push gcr.io/mirrornode/hedera-mirror-node/pgpool:${MIRROR_VERSION}

    docker pull bats/bats:latest
    docker tag bats/bats:latest gcr.io/mirrornode/hedera-mirror-node/test:${MIRROR_VERSION}
    docker push gcr.io/mirrornode/hedera-mirror-node/test:${MIRROR_VERSION}

# Testing

The mpdev tool is provided by Google Cloud Platform Marketplace to help verify the marketplace solution and install it.

## Verify

Run `mpdev verify` to automatically install the application in a new namespace, execute acceptance tests against it and uninstall it.
Set `TAG` to the version you want to test.

    mpdev verify --deployer=gcr.io/mirrornode/hedera-mirror-node/deployer:${TAG}

## Install

To install, first ensure the following environment variables are populated with the appropriate version and names:

    NAME="marketplace"
    NAMESPACE="marketplace"
    TAG="x.y.z"

Then you can run `mpdev install`. Note the properties in the 'required' section of the [schema.yaml](schema.yaml) file must be specified via the parameters flag.

    kubectl create namespace "${NAMESPACE}"
    mpdev install --deployer=gcr.io/mirrornode/hedera-mirror-node/deployer:${TAG} --parameters='{"name": "'${NAME}'",
        "namespace": "'${NAMESPACE}'",
        "global.rest.password": "password",
        "grpc.config.hedera.mirror.grpc.db.password": "password",
        "importer.config.hedera.mirror.importer.db.password": "password",
        "postgresql.postgresql.repmgrPassword": "password"}'

## Uninstall

Once installed in a Kubernetes cluster the Marketplace solution can be cleaned up with the following commands
(assumes previous environment variables are still set):

    kubectl delete application "${NAME}" -n "${NAMESPACE}"
    kubectl delete -n "${NAMESPACE}" $(kubectl get pvc -n "${NAMESPACE}")

Or you can simply delete the entire namespace if you created it during the install step and it's no longer needed:

    kubectl delete namespace "${NAMESPACE}"
