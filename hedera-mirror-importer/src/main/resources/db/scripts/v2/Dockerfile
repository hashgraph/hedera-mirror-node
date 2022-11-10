# This file is auto generated from it's template,
# see citusdata/tools/packaging_automation/templates/docker/alpine/alpine.tmpl.dockerfile.
FROM postgres:14.5-alpine
ARG VERSION=11.1.4
ARG PG_CRON_VERSION=1.4.2
ARG PG_PARTMAN_VERSION=4.7.1
ARG DB='mirror_node'

LABEL maintainer="Citus Data https://citusdata.com" \
      org.label-schema.name="Citus" \
      org.label-schema.description="Scalable PostgreSQL for multi-tenant and real-time workloads" \
      org.label-schema.url="https://www.citusdata.com" \
      org.label-schema.vcs-url="https://github.com/citusdata/citus" \
      org.label-schema.vendor="Citus Data, Inc." \
      org.label-schema.version=${VERSION}-alpine \
      org.label-schema.schema-version="1.0"

# Build citus and delete all used libraries. Warning: Libraries installed in this section will be deleted after build completion
RUN apk add --no-cache \
            --virtual builddeps \
        build-base \
        krb5-dev \
        curl \
        curl-dev \
        openssl-dev \
        ca-certificates \
        clang \
        llvm \
        lz4-dev \
        zstd-dev \
        libxslt-dev \
        libxml2-dev \
        icu-dev && \
    apk add --no-cache libcurl && \
    curl -sfLO "https://github.com/citusdata/citus/archive/v${VERSION}.tar.gz" && \
    tar xzf "v${VERSION}.tar.gz" && \
    cd "citus-${VERSION}" && \
   ./configure --with-security-flags && \
    make install && \
    cd .. && \
    rm -rf "citus-${VERSION}" "v${VERSION}.tar.gz" && \
    apk del builddeps

#--------End of Citus Build

# Install pg_cron and pg_partman
RUN apk add --no-cache --virtual .fetch-deps build-base ca-certificates clang-dev llvm13 openssl tar \
    && wget -O /pg_cron.tar.gz https://github.com/citusdata/pg_cron/archive/v$PG_CRON_VERSION.tar.gz \
    && mkdir -p pg_cron \
    && tar xvzf pg_cron.tar.gz \
    && cd pg_cron-$PG_CRON_VERSION \
    && sed -i.bak -e 's/-Werror//g' Makefile \
    && sed -i.bak -e 's/-Wno-implicit-fallthrough//g' Makefile \
    && make && make install \
    && cd .. && rm -rf pg_cron.tgz && rm -rf pg_cron-*

RUN apk add --no-cache --virtual .build-deps \
    && wget -O pg_partman.tar.gz "https://github.com/pgpartman/pg_partman/archive/v$PG_PARTMAN_VERSION.tar.gz" \
    && tar xvzf pg_partman.tar.gz \
    && cd pg_partman-$PG_PARTMAN_VERSION \
    && make \
    && make install \
    && cd .. \
    && rm -rf pg_partman.tar.gz  pg_partman-* \
    && apk del .fetch-deps .build-deps


# add citus, pg_cron, pg_partman_bgw to default PostgreSQL config
RUN echo "shared_preload_libraries='citus,pg_cron,pg_partman_bgw'" >> /usr/local/share/postgresql/postgresql.conf.sample
RUN echo "max_locks_per_transaction = 1024" >> /usr/local/share/postgresql/postgresql.conf.sample
RUN echo "cron.database_name = '${DB}'" >> /usr/local/share/postgresql/postgresql.conf.sample

# add scripts to run after initdb
COPY 001-create-citus-extension.sql /docker-entrypoint-initdb.d/

# add health check script
COPY pg_healthcheck /

# entry point unsets PGPASSWORD, but we need it to connect to workers
# https://github.com/docker-library/postgres/blob/33bccfcaddd0679f55ee1028c012d26cd196537d/12/docker-entrypoint.sh#L303
RUN sed "/unset PGPASSWORD/d" -i /usr/local/bin/docker-entrypoint.sh

# Add lz4 dependencies
RUN apk add zstd zstd-dev lz4 lz4-dev

HEALTHCHECK --interval=4s --start-period=6s CMD ./pg_healthcheck
