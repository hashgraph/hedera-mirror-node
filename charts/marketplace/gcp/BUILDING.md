# Overview

This folder contains logic to support the development and deployment of the Hedera Mirror Node in a Kubernetes cluster
in [Google Cloud Platform Marketplace](https://console.cloud.google.com/marketplace). It takes in the wrapper
`hedera-mirror` Helm chart and bundles it into the required deployer image which is built and pushed to Google Container
Registry on maven deploy.

# Setup

A Google Kubernetes Engine (GKE) cluster is required to verify and test the deployment of the Marketplace application.
Follow the GKE [quickstart](https://cloud.google.com/kubernetes-engine/docs/quickstart) to create a test cluster.

## Prerequisites

- [docker](https://docs.docker.com/install/)
- [gcloud](https://cloud.google.com/sdk/gcloud/)
- [kubectl](https://kubernetes.io/docs/tasks/tools/install-kubectl/)
- [mpdev](https://github.com/GoogleCloudPlatform/marketplace-k8s-app-tools/blob/master/docs/mpdev-references.md)

## Set environment variables

First ensure the following environment variables are populated with the appropriate version and names.
These variables will be used for the remainder of the document.

    NAME="marketplace"
    NAMESPACE="marketplace"
    TAG="x.y.z"

# Building

Apps are required to supply a `deployer` container image which is used in UI-based deployment.
The image should extend from one of the base images provided in the marketplace-k8s-app-tools registry.
This solution currently extends `gcr.io/cloud-marketplace-tools/k8s/deployer_helm`. Our deployer image build logic
is specified in the [Dockerfile](Dockerfile).

Additionally, GCP Marketplace restricts applications to images pulled from the Marketplace registry. Since the Mirror
Node uses some third party images like PostgreSQL, we need to re-publish these images to our staging registry and keep
them up to date.

Run the below commands to re-tag an existing image for testing with Marketplace:

    SRC_TAG="x.y.z"
    cd charts/marketplace/gcp
    ./release.sh "${SRC_TAG}" ${TAG}"

# Testing

The mpdev tool is provided by Google Cloud Platform Marketplace to help verify the marketplace solution and install it.

## Verify

Run `mpdev verify` to automatically install the application in a new namespace, execute acceptance tests against it and uninstall it.
Ensure [schema-test.yaml](schema-test.yaml) contains defaults for any required fields in [schema.yaml](schema.yaml).

    mpdev verify --deployer=gcr.io/mirror-node-public/hedera-mirror-node/deployer:${TAG}

## Install

To install run `mpdev install`. Note the properties in the `required` section of the [schema.yaml](schema.yaml)
file must be specified via the parameters flag.

    kubectl create namespace "${NAMESPACE}"
    mpdev install --deployer=gcr.io/mirror-node-public/hedera-mirror-node/deployer:${TAG} --parameters='{"name": "'${NAME}'",
        "namespace": "'${NAMESPACE}'",
        "importer.config.hedera.mirror.importer.downloader.accessKey": "GOOG1...",
        "importer.config.hedera.mirror.importer.downloader.secretKey": "...",
        "importer.config.hedera.mirror.importer.network": "MAINNET"}'

## Uninstall

Once installed in a Kubernetes cluster the Marketplace solution can be cleaned up with the following commands:

    kubectl delete application "${NAME}" -n "${NAMESPACE}"
    kubectl delete -n "${NAMESPACE}" $(kubectl get pvc -n "${NAMESPACE}")

Or you can simply delete the entire namespace if you created it during the install step and it's no longer needed:

    kubectl delete namespace "${NAMESPACE}"

# Releasing

Once all local testing is completed successfully and the images are tagged, run the below commands to republish the images
to the staging registry:

    git checkout "tags/v${TAG}"
    cd charts/marketplace/gcp
    ./release.sh "${TAG}"
