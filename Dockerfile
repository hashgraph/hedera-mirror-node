# ------------------------------  Rosetta  ------------------------------- #
FROM golang:1.13 as rosetta-builder
WORKDIR /tmp/src/hedera-mirror-rosetta
# TODO Use Git Clone instead
COPY ./hedera-mirror-rosetta . 
RUN go build -o rosetta-executable ./cmd

# ---------------------------- Importer/GRPC ----------------------------- #
FROM openjdk:11.0 as java-builder

RUN apt-get update && apt-get install -y git
RUN git clone https://github.com/LimeChain/hedera-mirror-node.git
RUN cd hedera-mirror-node && ./mvnw clean package -DskipTests

# ######################################################################## #
# --------------------------- Runner Container --------------------------- #
# ######################################################################## #

FROM ubuntu:18.04 as runner

# ----------------------------- PostgreSQL ----------------------------- #
# Add the PostgreSQL PGP key to verify their Debian packages.
# It should be the same key as https://www.postgresql.org/media/keys/ACCC4CF8.asc
RUN apt-get update && apt-get install -y gnupg
RUN apt-key adv --keyserver hkp://p80.pool.sks-keyservers.net:80 --recv-keys B97B0AFCAA1A47F044F244A07FCC7D46ACCC4CF8

# Add PostgreSQL's repository. It contains the most recent stable release
#  of PostgreSQL.
RUN echo "deb http://apt.postgresql.org/pub/repos/apt/ precise-pgdg main" > /etc/apt/sources.list.d/pgdg.list

# Install PostgreSQL 9.6, supervisor, git and openjdk-11 
ARG DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y postgresql-9.6 postgresql-client-9.6 postgresql-contrib-9.6 supervisor git openjdk-11-jdk-headless
# RUN add-apt-repository ppa:openjdk-r/ppa && apt-get update && apt install -y openjdk-11-jdk-headless

USER postgres

# TODO use the init db script and not hardcoded values!
RUN    /etc/init.d/postgresql start &&\
    psql --command "create user mirror_grpc WITH password 'mirror_grpc_pass';" &&\
    psql --command "create user mirror_node with SUPERUSER password 'mirror_node_pass'" &&\
    createdb -O mirror_grpc mirror_node &&\
    psql --command "grant connect on database mirror_node to mirror_grpc;" &&\
    psql --command "alter default privileges in schema public grant select on tables to mirror_grpc;" &&\
    psql --command "grant select on all tables in schema public to mirror_grpc;"


# And add ``listen_addresses`` to ``/etc/postgresql/9.6/main/postgresql.conf``
RUN echo "listen_addresses='*'" >> /etc/postgresql/9.6/main/postgresql.conf
# Allow PG Admin access
RUN echo "host    all             all             172.17.0.1/16           trust" >> /etc/postgresql/9.6/main/pg_hba.conf

USER root

# ---------------------------  Supervisord  --------------------------- #

# Copy the Rosetta Executable from the Rosetta Builder stage
WORKDIR /var/rosetta
COPY --from=rosetta-builder /tmp/src/hedera-mirror-rosetta/rosetta-executable .
COPY --from=rosetta-builder /tmp/src/hedera-mirror-rosetta/config/application.yml ./config/application.yml

# Copy the Importer Jar and Config from the Java-Builder stage
WORKDIR /var/importer
COPY --from=java-builder /hedera-mirror-node/hedera-mirror-importer/target/hedera-mirror-importer-0.17.0-rc1-exec.jar ./hedera-mirror-importer.jar
COPY --from=java-builder /hedera-mirror-node/hedera-mirror-importer/src/main/resources/application.yml .

# Copy the GRPC Jar and Config from the Java-Builder stage
WORKDIR /var/grpc
COPY --from=java-builder /hedera-mirror-node/hedera-mirror-grpc/target/hedera-mirror-grpc-0.17.0-rc1-exec.jar ./hedera-mirror-grpc.jar
COPY --from=java-builder /hedera-mirror-node/hedera-mirror-grpc/src/main/resources/application.yml .

WORKDIR /

# TODO Use Git Clone instead
COPY supervisord.conf supervisord.conf 

# Expose the ports
EXPOSE 5432 5700

ENTRYPOINT [ "supervisord" ]
