# docker build -f hedera-mirror-importer/src/test/resources/data/restore-client//Dockerfile \
#   ./hedera-mirror-importer/src/test/resources/data/  \
#   --build-arg dumpfile=<pgdump.gz>  --build-arg jsonkeyfile=bucket-download-key.json
#   -t hedera-mirror-node/postgres-restore-client
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

ENV DB_NAME mirror_node
ENV DB_USER mirror_node
ENV DB_PASS mirror_node_pass
ENV DB_PORT 5432

ENTRYPOINT PGPASSWORD=$DB_PASS pg_restore -U $DB_USER -d $DB_NAME -p $DB_PORT -h localhost /tmp/pgdump.gz
# docker run --network="host" -d -e DB_PORT=6432 hedera-mirror-node/postgres-restore-client
