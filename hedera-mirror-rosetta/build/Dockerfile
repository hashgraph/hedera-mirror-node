# This Dockerfile configuration is used to build
# Importer, Rosetta and PostgreSQL into one image
# and run the services using supervisord

FROM ubuntu:20.04 as builder
ARG DEBIAN_FRONTEND=noninteractive
ARG GIT_REF=main
RUN apt-get update && apt-get install -y gcc git openjdk-11-jdk-headless
RUN git clone https://github.com/hashgraph/hedera-mirror-node
WORKDIR /hedera-mirror-node
RUN git checkout ${GIT_REF}
RUN ./mvnw --batch-mode --no-transfer-progress --show-version clean package -DskipTests \
    -pl hedera-mirror-importer,hedera-mirror-rosetta

# ######################################################################## #
# --------------------------- Runner Container --------------------------- #
# ######################################################################## #

FROM ubuntu:20.04 as runner

# ---------------------- Install Deps & PosgreSQL ------------------------ #
# Add the PostgreSQL PGP key to verify their Debian packages.
# It should be the same key as https://www.postgresql.org/media/keys/ACCC4CF8.asc
ARG DEBIAN_FRONTEND=noninteractive
ENV PG_VERSION=13
RUN apt-get update \
    && apt-get install -y ca-certificates gnupg lsb-release \
    && apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys B97B0AFCAA1A47F044F244A07FCC7D46ACCC4CF8 \
    && echo "deb https://apt.postgresql.org/pub/repos/apt/ $(lsb_release -cs)-pgdg main" > /etc/apt/sources.list.d/pgdg.list \
    && apt-get update \
    && apt-get install -y postgresql-${PG_VERSION} supervisor openjdk-11-jre-headless \
    && rm -rf /var/lib/apt/lists/*

USER root

# ---------------------------  Supervisord  --------------------------- #

COPY --from=builder /hedera-mirror-node/hedera-mirror-importer/src/main/resources/db/scripts/init.sh /scripts/init.sh
USER postgres
ENV PATH="/usr/lib/postgresql/${PG_VERSION}/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
ENV PGCONF="/etc/postgresql/${PG_VERSION}/main"
ENV PGDATA="/var/lib/postgresql/${PG_VERSION}/main"

# Init db script
RUN /etc/init.d/postgresql start && /scripts/init.sh && /etc/init.d/postgresql stop

RUN echo "listen_addresses = '*'\nmax_wal_size = 8GB\nwork_mem = 256MB" >> "${PGCONF}/postgresql.conf"

# Allow PG Admin access
# RUN echo "host    all             all             172.17.0.1/16           trust" >> "${PGCONF}/pg_hba.conf"

USER root

# Copy the Rosetta Executable from the Rosetta Builder stage
COPY --from=builder /hedera-mirror-node/hedera-mirror-rosetta/bin/hedera-mirror-rosetta /var/rosetta/

# Copy the Importer Jar and Config from the Java-Builder stage
COPY --from=builder /hedera-mirror-node/hedera-mirror-importer/target/hedera-mirror-importer-*exec.jar /var/importer/hedera-mirror-importer.jar
# Enable persisting non-fee transfers to reconstruct the original transfer list
ENV HEDERA_MIRROR_IMPORTER_PARSER_RECORD_ENTITY_PERSIST_NONFEETRANSFERS=true
# Disable Redis because gRPC process is not run within this execution
ENV HEDERA_MIRROR_IMPORTER_PARSER_RECORD_ENTITY_REDIS_ENABLED=false

# Create users for impoter and rosetta
RUN useradd -s /bin/bash importer && useradd -s /bin/bash rosetta

COPY --from=builder /hedera-mirror-node/hedera-mirror-rosetta/build /build
WORKDIR /build

# Expose the ports (DB)(Rosetta)
EXPOSE 5432 5700
ENTRYPOINT [ "./run_supervisord.sh" ]
