# This Dockerfile configuration is used to build
# Importer, Rosetta and PostgreSQL into one image
# and run the services using supervisord

FROM ubuntu:20.04 as builder
ARG DEBIAN_FRONTEND=noninteractive
# GIT_REF can be a branch, a commit SHA, or a tag
ARG GIT_REF=main
RUN apt-get update && apt-get install -y gcc git openjdk-11-jdk-headless
RUN git clone https://github.com/hashgraph/hedera-mirror-node && cd hedera-mirror-node && git checkout ${GIT_REF}
WORKDIR /hedera-mirror-node
RUN ./mvnw --batch-mode --no-transfer-progress --show-version clean package -Dmaven.test.skip \
    -pl hedera-mirror-importer,hedera-mirror-rosetta
WORKDIR /app
RUN mkdir importer rosetta scripts \
    && cp -r /hedera-mirror-node/hedera-mirror-rosetta/build . \
    && cp /hedera-mirror-node/hedera-mirror-importer/target/hedera-mirror-importer-*exec.jar ./importer/hedera-mirror-importer.jar \
    && cp /hedera-mirror-node/hedera-mirror-rosetta/bin/hedera-mirror-rosetta ./rosetta \
    && cp /hedera-mirror-node/hedera-mirror-importer/src/main/resources/db/scripts/init.sh ./scripts

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
    && apt-get install -y postgresql-${PG_VERSION} postgresql-client-${PG_VERSION} supervisor openjdk-11-jre-headless \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder /app /app
# Create users for importer and rosetta
RUN useradd -s /bin/bash importer && useradd -s /bin/bash rosetta

ENV PATH="/usr/lib/postgresql/${PG_VERSION}/bin:${PATH}"
ENV PGCONF="/etc/postgresql/${PG_VERSION}/main"
ENV PGDATA="/var/lib/postgresql/${PG_VERSION}/main"

USER postgres
RUN cp /app/build/pg_hba.conf ${PGCONF}/ && cp /app/build/postgresql.conf ${PGCONF}/conf.d
# Init db script
RUN /etc/init.d/postgresql start && /app/scripts/init.sh && /etc/init.d/postgresql stop

USER root
WORKDIR /app/build

# Expose the ports (DB)(Rosetta)
EXPOSE 5432 5700
ENTRYPOINT [ "./run_supervisord.sh" ]
