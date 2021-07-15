#!/usr/bin/env bash
set -ex

if [[ "$#" -lt 1 ]]; then
    echo "You must provide the Mirror Node image tag (e.g. 0.14.0)"
    exit 1
fi


target_tag="${1}"
target_tag="${target_tag#v}" # Strip v prefix if present

grpc_container_name="osc_mirror_grpc_${target_tag}"
importer_container_name="osc_mirror_importer_${target_tag}"

# create tars file references
docker export --output="hedera-mirror-grpc:${target_tag}.tar" "$(docker create --name ${grpc_container_name} gcr.io/mirrornode/hedera-mirror-grpc:${target_tag})"
docker export --output="hedera-mirror-importer:${target_tag}.tar" "$(docker create --name ${importer_container_name} gcr.io/mirrornode/hedera-mirror-importer:${target_tag})"

# create temporary license files
tar t --file="hedera-mirror-grpc:${target_tag}.tar" | grep -F "/licenses/" | sort > grpc-licenses.txt
tar t --file="hedera-mirror-importer:${target_tag}.tar" | grep -F "/licenses/" | sort > importer-licenses.txt
echo "Created temporary grpc and importer license files"

# create temporary source files
tar t --file="hedera-mirror-grpc:${target_tag}.tar"  | grep -F "/sources/" | sort > grpc-sources.txt
tar t --file="hedera-mirror-importer:${target_tag}.tar" | grep -F "/sources/" | sort > importer-sources.txt
echo "Created temporary grpc and importer source files"

sort -u  *-licenses.txt > mirror-java-licenses.txt
echo "Created merged license file"
sort -u  *-sources.txt > mirror-java-sources.txt
echo "Created merged sources file"

# clean up files
rm grpc-licenses.txt
rm grpc-sources.txt
rm "hedera-mirror-grpc:${target_tag}.tar"
rm "hedera-mirror-importer:${target_tag}.tar"
rm importer-licenses.txt
rm importer-sources.txt

# clean up containers
docker rm --force "${grpc_container_name}"
docker rm --force "${importer_container_name}"

exit 0
