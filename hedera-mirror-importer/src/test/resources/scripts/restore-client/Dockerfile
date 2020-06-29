# Creates image to be used as an executable container that performs a pg_restore against an already existing database
# docker build -f hedera-mirror-importer/src/test/resources/scripts/restore-client/Dockerfile ./hedera-mirror-importer/src/test/resources/scripts/ --build-arg dumpfile=testnet_100k_pgdump.gz --build-arg jsonkeyfile=bucket-download-key.json -t gcr.io/mirrornode/hedera-mirror-node/postgres-restore-client:latest
FROM alpine:latest AS build

# install gcloud tools
RUN apk --update add curl python3
RUN curl https://dl.google.com/dl/cloudsdk/release/google-cloud-sdk.tar.gz > /tmp/google-cloud-sdk.tar.gz
RUN tar -C /usr/local/ -xvf /tmp/google-cloud-sdk.tar.gz
RUN /usr/local/google-cloud-sdk/install.sh

ARG dumpfile
ARG jsonkeyfile

# pull in key file and set up account
COPY ./$jsonkeyfile /tmp/config.json
RUN /usr/local/google-cloud-sdk/bin/gcloud auth activate-service-account --key-file /tmp/config.json

# download file from remote bucket and remove key file
RUN /usr/local/google-cloud-sdk/bin/gsutil cp gs://hedera-mirror-dev/database/$dumpfile /tmp/pgdump.gz

FROM alpine:latest
COPY --from=build /tmp/pgdump.gz /tmp/pgdump.gz

# install postgres-client
RUN apk --update add postgresql-client && rm -rf /var/cache/apk/*

ENV DB_NAME mirror_node
ENV DB_USER mirror_node
ENV DB_PASS mirror_node_pass
ENV DB_PORT 5432

# pull in restore file to be run on startup of container
COPY ./restore.sh /tmp/restore.sh
RUN chmod 755 /tmp/restore.sh

ENTRYPOINT /tmp/restore.sh

# docker run --network="host" -d -e DB_PORT=6432 gcr.io/mirrornode/hedera-mirror-node/postgres-restore-client
