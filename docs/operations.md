# Operations

- [GRPC API](#grpc-api)
- [Importer](#importer)
- [Monitor](#monitor)
- [REST API](#rest-api)

## GRPC API

The GRPC process is a Java-based application and should be able to run on any platform that Java supports. That said, we
recommend Ubuntu 18.04 be used as the base operating system as that is the only OS we've tested against.

### File Layout

- `/etc/systemd/system/hedera-mirror-grpc.service` - systemd service definitions
- `/usr/etc/hedera-mirror-grpc/application.yml` - Configuration file
- `/usr/lib/hedera-mirror-grpc` - Binaries

### Starting

```
sudo systemctl start hedera-mirror-grpc.service
```

### Stopping

```
sudo systemctl stop hedera-mirror-grpc.service
```

### Upgrading

Download release artifact from the [releases](https://github.com/hashgraph/hedera-mirror-node/releases) page and scp it
to the target server. Then execute:

```shell script
tar -xvf ./hedera-mirror-grpc-*.tgz
cd hedera-mirror-grpc-*/scripts
sudo ./deploy.sh
```

### Monitoring

```
systemctl status hedera-mirror-grpc.service
sudo journalctl -fu hedera-mirror-grpc.service
```

## Importer

The Importer process is a Java-based application and should be able to run on any platform that Java supports. That
said, we recommend Ubuntu 18.04 be used as the base operating system as that is the only OS we've tested against.

### File Layout

- `/etc/systemd/system/hedera-mirror-importer.service` - systemd service definitions
- `/usr/etc/hedera-mirror-importer/application.yml` - Configuration file
- `/usr/lib/hedera-mirror-importer` - Binaries
- `/var/lib/hedera-mirror-importer` - Data
  - `addressbook.bin` - The current address book in use
  - `accountBalances` - The downloaded balance and signature files
  - `recordstreams` - The downloaded record and signature files

### Starting

```
sudo systemctl start hedera-mirror-importer.service
```

### Stopping

```
sudo systemctl stop hedera-mirror-importer.service
```

If shutdown cleanly the service will log a `Shutting down.....` message

### Upgrading

Download release artifact from the [releases](https://github.com/hashgraph/hedera-mirror-node/releases) page and scp it
to the target server. Then execute:

```shell script
tar -xvf ./hedera-mirror-importer-*.tgz
cd hedera-mirror-importer-*/scripts
sudo ./deploy.sh
```

### Monitoring

```
systemctl status hedera-mirror-importer.service
sudo journalctl -fu hedera-mirror-importer.service
```

### v1 to v2 Data Migration

To support time series logic the Mirror Node DB schema shifted from PostgeSQL (v1) to TimescaleDB (v2).
[Migrating from a Different PostgreSQL Database](https://docs.timescale.com/latest/getting-started/migrating-data#different-db) highlights the general recommended data migration steps when moving to TimescaleDB.

For mirror node operators running v1 db schema, the following steps can be taken to upgrade to v2.

1. Setup a new database container using TimeScale

    To install using docker-compose, update the `docker-compose.override.yml` file to disable postgres instead of TimeScaleDB

    ```yaml
    version: "3.3"
    services:
      db:
        entrypoint: ["echo", "PostgreSQL db is disabled"]
    ```

    Start up the TimescaleDB service:
    ```shell script
    $ docker-compose up tsdb
    ```

    Note: If the new db is running on the same server node as the original PostgeSQL db, then the port must be updated to something other than 5432.
    The `tsdb` port can be updated to a different port e.g. 6432 as follows:
    ```yaml
    ...
    services:
      ...
      tsdb:
        ports:
          - 6432:5432
    ```

2. Create DB & Init Schema

    The init script for v2 `hedera-mirror-importer/src/main/resources/db/scripts/init_v2.sql` may be used to create the database, users, schema, extensions and ensure all permissions are set.
    In the docker-compose case this file is already mounted under `/docker-entrypoint-initdb.d/` on the docker container and run on startup.

    This may be run manually against the db node if not using docker-compose:
    ```shell script
    $ psql "dbname=mirror_node host=localhost user=mirror_importer password=mirror_importer_pass port=6432" -f hedera-mirror-importer/src/main/resources/db/scripts/init_v2.sql
    ```

    > **_NOTE:_** The following steps assume the database, users and schema have been created as detailed above

3. Configure migration properties

    The configuration file `hedera-mirror-importer/src/main/resources/db/scripts/time-scale-migration/migration.config` contains db variables for easy running.
    These options include variables such as db names, passwords, users, hosts for both the existing db and the new db.

    Update these values appropriately for your db setup.

4. Run migration script

    From the `hedera-mirror-importer/src/main/resources/db` directory run the `timeScaleDbMigration.sh` script
    ```shell script
    $ ./scripts/time-scale-migration/timeScaleDbMigration.sh
    ```

   The script uses successive `psql` connections to backup, configure and restore data on the new database nodes.
   First it copies over the `flyway_schema_history` table, to maintain migration history.
   It then utilizes the migration sql script used by normal flyway operations to create the new tables and then creates the Timescale hyper tables based on these.
   Following this the tables from the old database are backed up as csv files using `\COPY` and then the data inserted into the new database also using `\COPY`.
   Finally the schema of the `flyway_schema_history` is updated and the sequence values are updated to ensure continuation.

## Monitor

The monitor is a Java-based application and should be able to run on any platform that Java supports. That said, we
recommend running it as a Docker container via [Docker Compose](/docker-compose.yml) or
[Helm](/charts/hedera-mirror-monitor).

## REST API

### Initial Installation

The REST API runs on [Node.js](https://nodejs.org) and should be able to run on any platform that Node.js supports. That
said, we recommend Ubuntu 18.04 be used as the base operating system as that is the only OS we've tested against. It is
also recommended to create a `restapi` user and execute all commands as that user.

```shell script
sudo apt-get install build-essential libssl-dev git postgresql-client
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.34.0/install.sh | bash
bash # reload shell
nvm install stable #pin the current versions node: v12.10.0, npm: 6.10.3
npm install -g pm2
sudo mkdir -p /opt/restapi
sudo chown restapi: /opt/restapi
cd /opt/restapi
cat > application.yml <<EOF
hedera:
  mirror:
    rest:
      db:
        host: dbhost
        password: mirror_api_pass
EOF
wget "https://github.com/hashgraph/hedera-mirror-node/releases/download/v0.8.1/hedera-mirror-rest-v0.8.1.tgz"
tar --strip-components=1 -xvf hedera-mirror-rest-v*.tgz
pm2 start pm2.json
```

### File Layout

- `/opt/restapi` - Binaries
- `/opt/restapi/application.yml` - Configuration

### Upgrading

Replace the version below with the appropriate version:

```shell script
sudo su - restapi
cd /opt/restapi
pm2 stop all
wget "https://github.com/hashgraph/hedera-mirror-node/releases/download/v0.8.1/hedera-mirror-rest-v0.8.1.tgz"
tar --strip-components=1 -xvf hedera-mirror-rest-v*.tgz
pm2 start pm2.json
```

### Starting

To start all 10 ports (6551-6560):

```shell script
pm2 start /opt/restapi/pm2.json
```

To manually start on a specific port:

```shell script
HEDERA_MIRROR_REST_PORT=8080 pm2 start server.js
```

### Stopping

```shell script
pm2 stop all
```

### Monitoring

```shell script
pm2 monit
pm2 status
pm2 logs <port>
```

Verify individual APIs manually:

```shell script
curl http://127.0.0.1:6551/api/v1/accounts
curl http://127.0.0.1:6551/api/v1/balances
curl http://127.0.0.1:6551/api/v1/transactions
```

Verify all ports:

```shell script
for port in {6551..6560}; do curl -s "http://127.0.0.1:${port}/api/v1/transactions?limit=1" && echo; done
```

To setup live monitoring, see [monitoring](../hedera-mirror-rest/monitoring/README.md) documentation.

### Open API Spec

The REST API supports the OpenAPI (Swagger) specification v3. This provides documentation and structure for metrics

#### View Spec UI

We utilize the [swagger-ui-express](https://github.com/scottie1984/swagger-ui-express) package to serve our
documentation based on the OpenAPI specification. The OpenAPI specification can be viewed at

- `/api/v1/docs` - API v1 doc serve path

Where `v1` corresponds to the Mirror Node REST API version and `docs` is the default path value as controlled
by `hedera.mirror.rest.openapi.swaggerUIPath`.

#### Update Spec

To update the spec, manually modify the spec file located at

- `hedera-mirror-rest/api/v1/openapi.yml`

Where `v1` corresponds to the Mirror Node REST API version and `openapi` is the default filename value as controlled
by `hedera.mirror.rest.openapi.specFileName`.

- `hedera-mirror-rest/api/v1/openapi.yml` - API v1 openapi spec

### Metrics

The REST API has metrics as provided by [Swagger Stats](https://swaggerstats.io). Using this 3 endpoints are made
available

- `/swagger/ui` - Metrics dashboard
- `/swagger/stats` - Aggregated statistics
- `/swagger/metrics` - Prometheus formatted metrics
Where `swagger` is the default metrics path as controlled by `hedera.mirror.rest.metrics.config.uriPath`.
