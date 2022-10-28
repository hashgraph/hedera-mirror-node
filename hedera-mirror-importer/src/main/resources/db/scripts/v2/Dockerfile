FROM citusdata/citus:11.1.4-pg14

# install Citus
RUN apt-get update \
    && apt-get install -y git make gcc postgresql-server-dev-$PG_MAJOR libicu-dev sendemail htop mc systemtap-sdt-dev \
    && git clone https://github.com/pgpartman/pg_partman.git \
    && cd pg_partman \
    && make install

# add citus to default PostgreSQL config
RUN echo "shared_preload_libraries='citus,pg_partman_bgw'" >> /usr/share/postgresql/postgresql.conf.sample

# add scripts to run after initdb
COPY 001-create-citus-extension.sql /docker-entrypoint-initdb.d/
