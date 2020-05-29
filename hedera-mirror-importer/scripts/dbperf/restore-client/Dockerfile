# docker build -f hedera-mirror-importer/scripts/dbperf/Dockerfile ./hedera-mirror-importer/scripts/dbperf --build-arg dumpfile=testnet_1_24_1_flyway_schema_history_pgdump_2.gz  -t my-gloud-p
FROM debian:stretch AS build

# get dump file
ARG dumpfile
ARG jsonkeyfile

RUN apt-get -qqy update && apt-get install -qqy curl python-dev
RUN curl https://dl.google.com/dl/cloudsdk/release/google-cloud-sdk.tar.gz > /tmp/google-cloud-sdk.tar.gz
RUN tar -C /usr/local/ -xvf /tmp/google-cloud-sdk.tar.gz
RUN /usr/local/google-cloud-sdk/install.sh

COPY ./$jsonkeyfile /tmp/config.json
RUN /usr/local/google-cloud-sdk/bin/gcloud auth activate-service-account --key-file /tmp/config.json


RUN /usr/local/google-cloud-sdk/bin/gsutil cp gs://hedera-mirror-dev/database/$dumpfile /tmp/pgdump.gz
RUN rm /tmp/config.json

FROM alpine:latest
COPY --from=build /tmp/pgdump.gz /tmp/pgdump.gz
RUN apk --update add postgresql-client && rm -rf /var/cache/apk/*

ENTRYPOINT PGPASSWORD=mirror_node_pass pg_restore -U mirror_node -d mirror_node -p 6432 -h localhost /tmp/pgdump.gz
# docker run --network="host" -d my-gloud-pg
