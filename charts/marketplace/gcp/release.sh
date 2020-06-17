#!/usr/bin/env bash
set -ex

if [[ "$#" -ne 1 ]]; then
  echo "You must provide the Mirror Node image tag"
  exit 1
fi

tag="${1}"
tag_minor="${tag%\.*}"
bats_tag="latest"
postgresql_tag="latest"
registry="gcr.io/mirror-node-public/hedera-mirror-node"

function retag() {
  local source=${1}
  local image=${2}
  local target="${registry}/${image}"

  # Main image
  if [[ -z "${image}" ]]; then
    target="${registry}"
  fi

  docker pull "${source}"
  docker tag "${source}" "${target}:${tag}"
  docker tag "${source}" "${target}:${tag_minor}"
  docker push "${target}:${tag}"
  docker push "${target}:${tag_minor}"
}

# Build Marketplace deployer image
docker build -f ./Dockerfile -t "${registry}/deployer:${tag}" -t "${registry}/deployer:${tag_minor}" --build-arg tag=${tag} ../..
docker push "${registry}/deployer:${tag}"
docker push "${registry}/deployer:${tag_minor}"

# Retag other images
retag "gcr.io/mirrornode/hedera-mirror-importer:${tag}" ""
retag "gcr.io/mirrornode/hedera-mirror-grpc:${tag}" "grpc"
retag "gcr.io/mirrornode/hedera-mirror-rest:${tag}" "rest"
retag "bats/bats:${bats_tag}" "test"
retag "bitnami/postgresql-repmgr:${postgresql_tag}" "postgresql-repmgr"

echo "Successfully pushed all images"
exit 0

