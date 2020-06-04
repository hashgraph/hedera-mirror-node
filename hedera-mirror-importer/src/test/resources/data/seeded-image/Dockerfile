# Creates a fully populated postgres database from the specified pg dump file
# Build using command from repo root:
# docker build -f hedera-mirror-importer/src/test/resources/data/seeded-image/Dockerfile \
#   ./hedera-mirror-importer/src/test/resources/data/  \
#   --build-arg dumpfile=<pgdump.gz>  --build-arg jsonkeyfile=bucket-download-key.json
#   -t gcr.io/mirrornode/hedera-mirror-node/postgres-seeded:latest
FROM debian:stretch AS build

ARG dumpfile
ARG jsonkeyfile

# install gcloud tools
RUN apt-get -qqy update && apt-get install -qqy curl python-dev
RUN curl https://dl.google.com/dl/cloudsdk/release/google-cloud-sdk.tar.gz > /tmp/google-cloud-sdk.tar.gz
RUN tar -C /usr/local/ -xvf /tmp/google-cloud-sdk.tar.gz
RUN /usr/local/google-cloud-sdk/install.sh

# pull in key file and set up account
COPY ./$jsonkeyfile /tmp/config.json
RUN /usr/local/google-cloud-sdk/bin/gcloud auth activate-service-account --key-file /tmp/config.json

# download file from remote bucket and remove key file
RUN /usr/local/google-cloud-sdk/bin/gsutil cp gs://hedera-mirror-dev/database/$dumpfile /tmp/pgdump.gz
RUN rm /tmp/config.json

FROM gcr.io/mirrornode/hedera-mirror-node/postgres:latest
COPY --from=build /tmp/pgdump.gz /tmp/pgdump.gz

ENV DB_NAME mirror_node
ENV DB_USER mirror_node
ENV DB_PASS mirror_node_pass
ENV DB_PORT 5432

# place restore file in docker-entrypoint-initdb.d to be run by postgres process on startup of container
COPY ./restore.sh /docker-entrypoint-initdb.d/
RUN chmod 755 /docker-entrypoint-initdb.d/restore.sh

# place custom postgres configs in volume to be picked up on startup
COPY ./postgresql.conf /etc/postgresql/postgresql.conf

# Run as:
# docker run -d -p 8432:5432 gcr.io/mirrornode/hedera-mirror-node/postgres-seeded:latest
# docker run -d -p 5432:5432 -e DB_NAME=mirror_node -e DB_USER=mirror_node -e DB_PASS=mirror_node_pass DB_PORT=8432 gcr.io/mirrornode/hedera-mirror-node/postgres-seeded:latest
