FROM postgres:9.6
# Build using command from repo root:
# docker build -f hedera-mirror-importer/scripts/dbperf/data-empty/Dockerfile ./hedera-mirror-importer/ \
#   -t hedera-mirror-node/db-populatedhedera-mirror-node/postgres:latest .

# Update Ubuntu
RUN \
  bash -c 'apt-mark hold postgresql-common' && \
  bash -c 'apt-get -qq update && apt-get -y upgrade && apt-get -y autoclean && apt-get -y autoremove' && \
  bash -c 'DEBIAN_FRONTEND=noninteractive apt-get install -y curl wget tar'

ENV DB_NAME mirror_node
ENV DB_USER mirror_node
ENV DB_PASS mirror_node_pass
ENV POSTGRES_PASSWORD password

RUN mkdir -p /docker-entrypoint-initdb.d

COPY ./src/main/resources/db/scripts/init.sql /docker-entrypoint-initdb.d/
RUN chmod 755 /docker-entrypoint-initdb.d/init.sql

# Run as:
# docker run -d -e DB_NAME=mirror_node -e DB_USER=mirror_node -e DB_PASS=mirror_node_pass hedera-mirror-node/postgres:latest
