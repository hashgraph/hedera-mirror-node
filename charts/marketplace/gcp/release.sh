#!/usr/bin/env bash
set -ex

if [[ "$#" -lt 1 ]]; then
  echo "You must provide the Mirror Node image tag (e.g. 0.14.0)"
  exit 1
fi

tag="${1#v}" # Strip v prefix if present
tag_minor="${tag%\.*}"
source_tag="${tag}"
bats_tag="v1.1.0"
postgresql_tag="12.3.0-debian-10-r35"
registry="${2:-gcr.io/mirror-node-public/hedera-mirror-node}"

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
retag "gcr.io/mirrornode/hedera-mirror-importer:${source_tag}" ""
retag "gcr.io/mirrornode/hedera-mirror-grpc:${source_tag}" "grpc"
retag "gcr.io/mirrornode/hedera-mirror-rest:${source_tag}" "rest"
retag "bats/bats:${bats_tag}" "test"
retag "bitnami/postgresql-repmgr:${postgresql_tag}" "postgresql-repmgr"

echo "Successfully pushed all images"
exit 0

