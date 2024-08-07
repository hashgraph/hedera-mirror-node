#!/bin/bash

SOLO_CLUSTER=solo-e2e
SOLO_NAMESPACE=solo
SOLO_IMAGE_LIST=( \
  docker.io/bitnami/postgresql-repmgr:14.11.0-debian-12-r8 \
  docker.io/envoyproxy/envoy:v1.21.1 \
  docker.io/grafana/grafana:10.1.5 \
  docker.io/haproxytech/haproxy-alpine:2.4.25 \
  quay.io/prometheus-operator/prometheus-config-reloader:v0.68.0 \
  docker.io/otel/opentelemetry-collector-contrib:0.72.0 \
  gcr.io/hedera-registry/hedera-mirror-node-explorer:24.4.0 \
  gcr.io/hedera-registry/uploader-mirror:1.3.0 \
  gcr.io/mirrornode/hedera-mirror-grpc:0.103.0 \
  quay.io/prometheus-operator/prometheus-operator:v0.68.0 \
  gcr.io/mirrornode/hedera-mirror-importer:0.103.0 \
  gcr.io/mirrornode/hedera-mirror-monitor:0.103.0 \
  gcr.io/mirrornode/hedera-mirror-rest:0.103.0 \
  quay.io/prometheus/alertmanager:v0.26.0 \
  gcr.io/mirrornode/hedera-mirror-web3:0.103.0 \
  ghcr.io/hashgraph/full-stack-testing/ubi8-init-java21:0.24.5 \
  quay.io/prometheus/node-exporter:v1.6.1 \
  ghcr.io/hashgraph/hedera-json-rpc-relay:0.46.0 \
  quay.io/kiwigrid/k8s-sidecar:1.25.1 \
  quay.io/minio/minio:RELEASE.2024-02-09T21-25-16Z \
  quay.io/minio/operator:v5.0.7 \
  quay.io/prometheus/prometheus:v2.47.1 \
  registry.k8s.io/kube-state-metrics/kube-state-metrics:v2.10.0 \
)
function download_images() {
  for im in "${SOLO_IMAGE_LIST[@]}"; do
    echo "Pulling image: ${im}"
      docker pull --quiet "${im}"
      sleep 1
  done
}
function load_images() {
  for im in "${SOLO_IMAGE_LIST[@]}"; do
    echo "Loading image: ${im}"
    kind load docker-image "${im}" -n $SOLO_CLUSTER
  done
}
download_images;
kind create cluster -n $SOLO_CLUSTER;
load_images;
npm i -g @hashgraph/solo@0.27.0;
solo init -n $SOLO_NAMESPACE -i node0,node1,node2;
solo cluster setup --prometheus-stack
solo network deploy --prometheus-svc-monitor
solo node keys --gossip-keys --tls-keys --key-format pem
solo node setup
solo node start
solo mirror-node deploy
solo relay deploy -i node0,node1,node2