# This Dockerfile configuration is used to build
# Importer, Rosetta and PostgreSQL into one image
# and run the services using supervisord

# --------------------------  Clone Repository  -------------------------- #
FROM ubuntu:20.04 as cloner
RUN apt-get update && apt-get install -y git
ARG GIT_BRANCH=master
RUN git clone https://github.com/hashgraph/hedera-mirror-node.git
RUN cd hedera-mirror-node && git checkout "${GIT_BRANCH}"

# ------------------------------  Rosetta  ------------------------------- #
FROM golang:1.16 as rosetta-builder
WORKDIR /tmp
COPY --from=cloner /hedera-mirror-node ./hedera-mirror-node
WORKDIR /tmp/hedera-mirror-node/hedera-mirror-rosetta
RUN go build -o rosetta-executable ./cmd

# ---------------------------- Importer ----------------------------- #
FROM openjdk:11.0 as java-builder

RUN apt-get update
COPY --from=cloner /hedera-mirror-node ./hedera-mirror-node
RUN cd hedera-mirror-node && ./mvnw --batch-mode --no-transfer-progress --show-version clean package -DskipTests -pl hedera-mirror-importer

# ######################################################################## #
# --------------------------- Runner Container --------------------------- #
# ######################################################################## #

FROM ubuntu:20.04 as runner

# ---------------------- Install Deps & PosgreSQL ------------------------ #
# Add the PostgreSQL PGP key to verify their Debian packages.
# It should be the same key as https://www.postgresql.org/media/keys/ACCC4CF8.asc
RUN apt-get update && apt-get install -y gnupg lsb-release
RUN apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys B97B0AFCAA1A47F044F244A07FCC7D46ACCC4CF8

# Add PostgreSQL's repository. It contains the most recent stable release
#  of PostgreSQL.
RUN echo "deb http://apt.postgresql.org/pub/repos/apt/ $(lsb_release -cs)-pgdg main" > /etc/apt/sources.list.d/pgdg.list

# Install PostgreSQL 9.6, supervisor, git and openjdk-11
ARG DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y postgresql-9.6 postgresql-client-9.6 postgresql-contrib-9.6 supervisor openjdk-11-jdk-headless curl

USER root

# Create Volume PostgreSQL directory and Change default PostgreSQL directory
RUN mkdir -p /data/db
RUN chown postgres /data/db
RUN chmod 700 /data/db
RUN mv /var/lib/postgresql/9.6/main /data/db/main
RUN ln -s /data/db/main /var/lib/postgresql/9.6/main

# ---------------------------  Supervisord  --------------------------- #

# Copy the repository from the Cloner stage
COPY --from=cloner /hedera-mirror-node ./hedera-mirror-node
USER postgres

# Init db script
RUN /etc/init.d/postgresql start &&\
    createdb mirror_node &&\
    psql --command "create user mirror_node with SUPERUSER password 'mirror_node_pass'" &&\
    PGPASSWORD=mirror_node_pass psql -d mirror_node -U mirror_node -h 127.0.0.1 -f /hedera-mirror-node/hedera-mirror-importer/src/main/resources/db/scripts/init_v1.sql

RUN echo "listen_addresses='*'" >> /etc/postgresql/9.6/main/postgresql.conf
# Allow PG Admin access
RUN echo "host    all             all             172.17.0.1/16           trust" >> /etc/postgresql/9.6/main/pg_hba.conf

USER root

# Create Volume importer directory
RUN mkdir -p /data/data
RUN ln -s /data/data /hedera-mirror-node

# Copy the Rosetta Executable from the Rosetta Builder stage
WORKDIR /var/rosetta
COPY --from=rosetta-builder /tmp/hedera-mirror-node/hedera-mirror-rosetta/rosetta-executable .
COPY --from=rosetta-builder /tmp/hedera-mirror-node/hedera-mirror-rosetta/config/application.yml ./config/application.yml

# Copy the Importer Jar and Config from the Java-Builder stage
WORKDIR /var/importer
COPY --from=java-builder /hedera-mirror-node/hedera-mirror-importer/target/hedera-mirror-importer-*exec.jar ./hedera-mirror-importer.jar

WORKDIR /hedera-mirror-node/hedera-mirror-rosetta/build

# Expose the ports (DB)(Rosetta)
EXPOSE 5432 5700
ENTRYPOINT [ "./run_supervisord.sh" ]
