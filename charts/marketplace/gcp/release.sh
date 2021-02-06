#!/usr/bin/env bash
set -ex

if [[ "$#" -lt 1 ]]; then
    echo "You must provide the Mirror Node image tag (e.g. 0.14.0)"
    exit 1
fi

# Same source and target tags
if [[ "$#" -eq 1 ]]; then
    source_tag="${1}"
    target_tag="${source_tag}"
fi

# Different source and target tags. Useful for testing chart changes with existing tags.
if [[ "$#" -eq 2 ]]; then
    source_tag="${1}"
    target_tag="${2}"
fi

target_tag="${target_tag#v}" # Strip v prefix if present
target_tag_minor="${target_tag%\.*}"
bats_tag="1.2.1"
postgresql_tag="12.5.0-debian-10-r79"
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
    docker tag "${source}" "${target}:${target_tag}"
    docker push "${target}:${target_tag}"

    # Don't update minor tag for pre-release tags
    if [[ ! "${target_tag}" =~ .*-.* ]]; then
        docker tag "${source}" "${target}:${target_tag_minor}"
        docker push "${target}:${target_tag_minor}"
    fi
}

# Ensure chart app version matches schema.yaml version
sed -i .bak "s/version: .*/version: ${target_tag}/" values.yaml

# Build Marketplace deployer image
docker build -f ./Dockerfile -t "${registry}/deployer:${target_tag}" --build-arg tag=${target_tag} ../..
docker push "${registry}/deployer:${target_tag}"

# Retag other images
retag "${registry}/deployer:${target_tag}" "deployer"
retag "gcr.io/mirrornode/hedera-mirror-importer:${source_tag}" ""
retag "gcr.io/mirrornode/hedera-mirror-grpc:${source_tag}" "grpc"
retag "gcr.io/mirrornode/hedera-mirror-rest:${source_tag}" "rest"
retag "bats/bats:${bats_tag}" "test"
retag "bitnami/postgresql-repmgr:${postgresql_tag}" "postgresql-repmgr"

mv values.yaml.bak values.yaml
echo "Successfully pushed all images"
exit 0
