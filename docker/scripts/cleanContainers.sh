#!/bin/bash

docker container rm -f docker_mirror-node-flyway_1
docker container rm -f docker_mirror-node-balance-parser_1
docker container rm -f docker_mirror-node-record-download-parse_1
docker container rm -f docker_mirror-node-102-file-update_1
docker container rm -f docker_mirror-node-balance-downloader_1
docker container rm -f docker_mirror-node-postgres_1
docker container rm -f docker_mirror-node-rest-api_1
docker container rm -f hedera-mirrornode_compiler_1
