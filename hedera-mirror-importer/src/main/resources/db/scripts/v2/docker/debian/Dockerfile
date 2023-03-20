FROM citusdata/citus:11.2.0-pg14
ARG PG_CRON_DB='mirror_node'

# install pg cron and partman
RUN apt-get update \
    && apt-get upgrade -y --no-install-recommends \
    && apt-get install -y postgresql-14-cron postgresql-14-partman \
    && rm -rf /var/lib/apt/lists/*

# add citus, pg_cron and partman to default PostgreSQL config
RUN sed -i "s/shared_preload_libraries=.*$/shared_preload_libraries='citus,pg_cron,pg_partman_bgw'/" /usr/share/postgresql/postgresql.conf.sample
RUN echo "max_locks_per_transaction = 1024" >> /usr/share/postgresql/postgresql.conf.sample
RUN echo "cron.database_name = '${PG_CRON_DB}'" >> /usr/share/postgresql/postgresql.conf.sample
