# Installation

The Mirror Node can be ran [locally](#running-locally) or via [Docker](#running-via-docker-compose). To run locally,
it'll first need to be built using Java. To run via Docker, either build locally, or pull latest images from GCR.

```console
docker-compose pull
```

## Building

Ensure you have OpenJDK 11 installed, then run the following command from the top level directory. This will compile a
runnable Mirror Node JAR file in the `target` directory.

```console
./mvnw clean package -DskipTests
```

## Running Locally

### Database Setup

In addition to OpenJDK 11, you will need to install a database and initialize it. The Mirror Node
utilizes [PostgreSQL](https://postgresql.org) v9.6 or [TimescaleDB](https://docs.timescale.com/latest/main) v2 depending
on the version of its database schema.

For both databases, since [Flyway](https://flywaydb.org) will manage the database schema, the only required setup steps
include:

* creating the database, users, schema, and extensions.
* ensuring all permissions are set.

Scripts for v1 and v2 are provided to accomplish this. Make sure the application [configuration](configuration.md)
matches the values in the script. Run the script as a super user and check the output carefully to ensure no errors
occurred.

#### PostgreSQL (V1)

Run the SQL script located at `hedera-mirror-importer/src/main/resources/db/scripts/init_v1.sql`. Edit the file and
change the `db_name`, `db_user`, `db_password` `db_owner`, `grpc_user`, `grpc_password`, `rosetta_user`
or `rosetta_password` as appropriate.

```console
psql postgres -f hedera-mirror-importer/src/main/resources/db/scripts/init_v1.sql
```

#### TimescaleDB (V2)

Run the SQL script located at `hedera-mirror-importer/src/main/resources/db/scripts/init_v2.sql`. Edit the file and
change the db user names, passwords and schema as appropriate.

```console
psql postgres -f hedera-mirror-importer/src/main/resources/db/scripts/init_v2.sql
```

### Importer

To run the Importer, first populate the configuration at one of the supported
[configuration](configuration.md#importer) paths, then run:

```console
java -jar hedera-mirror-importer/target/hedera-mirror-importer-*.jar
```

Additionally, there is a Systemd unit file located in the `hedera-mirror-importer/scripts/` directory that can be used
to manage the process. See the [operations](operations.md) documentation for more information.

### GRPC API

To run the GRPC API, first populate the configuration at one of the supported
[configuration](configuration.md#grpc-api) paths, then run:

```console
java -jar hedera-mirror-grpc/target/hedera-mirror-grpc-*.jar
```

### Monitor

To run the monitor, first populate the configuration at one of the supported
[configuration](configuration.md#monitor) paths, then run:

```console
java -jar hedera-mirror-monitor/target/hedera-mirror-monitor-*.jar
```

The monitor is mainly intended to be run as a Docker container. See our [Docker Compose](/docker-compose.yml) and
our [Helm chart](/charts/hedera-mirror-monitor) for more details.

### REST API

To start the REST API ensure you have the necessary [configuration](configuration.md#rest-api) populated and run:

```console
cd hedera-mirror-rest
npm install
npm start
```

#### Unit Tests

Run the unit tests using jest by using:

```console
npm test
```

### Rosetta API

#### Prerequisites

``
Go 1.13+
``

To start the Rosetta API ensure you have the necessary [configuration](configuration.md#rosetta-api) populated and run:

```console
cd hedera-mirror-rosetta
go run cmd/*
```

#### Unit Tests

Run the unit tests by executing:

```console
cd hedera-mirror-rosetta
go test ./...
```

#### Rosetta CLI Validation

After you have started the Rosetta API, in another terminal run:

```console
cd hedera-mirror-rosetta/scripts/validation
./run-validation.sh
```

Currently, Rosetta CLI Validation supports only `DEMO` and `TESTNET`, where
`DEMO` is default and `TESTNET` can be run via:

```console
./run-validation.sh testnet
```

#### Rosetta All-in-One Dockerfile configuration

The `All-in-One` configuration aggregates the PostgreSQL, Importer and Rosetta services in a single Dockerfile
configuration. Configuration is based on Rosetta specification,
found [here](https://www.rosetta-api.org/docs/node_deployment.html). Data Persistence is based on Rosetta specification
as well, found [here](https://www.rosetta-api.org/docs/standard_storage_location.html). Exposed ports are `5432` (
PostgreSQL) and `5700` (Rosetta).

To build the Dockerfile, run:

```console
cd hedera-mirror-rosetta/build
docker build .
```

Image container can be run via:

```console
docker run <image>
```

With a mounted volume:

```console
docker run -v <volume>:/data <image>
```

The built Docker image can be run in `online` (default) and `offline` mode. The `online` mode runs all the above
specified services, where in `offline` - only the Rosetta service.

To run in `offline` mode:

```console
docker run -e MODE=offline <image>
```

You can override Importer and Rosetta services default configuration by passing
`environment variables`, specified [here](./configuration.md).

For ease, an additional environment variable, called `NETWORK` is added, where you can both override the Importer and
Rosetta default network configuration:

```console
docker run -e NETWORK=TESTNET <image>
```

In order Importer to sync data, different from default, the following environment variables need to be overridden:

```console
HEDERA_MIRROR_IMPORTER_DOWNLOADER_ACCESSKEY=
HEDERA_MIRROR_IMPORTER_DOWNLOADER_BUCKETNAME=
HEDERA_MIRROR_IMPORTER_DOWNLOADER_CLOUDPROVIDER=
HEDERA_MIRROR_IMPORTER_DOWNLOADER_GCPPROJECTID=
HEDERA_MIRROR_IMPORTER_DOWNLOADER_SECRETKEY=
HEDERA_MIRROR_IMPORTER_START_DATE=
```

regardless of specified `NETWORK`.

A full example for `testnet` network in `online` mode:

```console
docker run -e NETWORK=TESTNET \
-e HEDERA_MIRROR_IMPORTER_DOWNLOADER_ACCESSKEY= \
-e HEDERA_MIRROR_IMPORTER_DOWNLOADER_BUCKETNAME= \
-e HEDERA_MIRROR_IMPORTER_DOWNLOADER_CLOUDPROVIDER= \
-e HEDERA_MIRROR_IMPORTER_DOWNLOADER_GCPPROJECTID= \
-e HEDERA_MIRROR_IMPORTER_DOWNLOADER_SECRETKEY= \
-e HEDERA_MIRROR_IMPORTER_START_DATE= \
-p 5700:5700 \
<image>
```

## Running via Docker Compose

Docker Compose scripts are provided and run all the mirror node components:

- PostgreSQL/TimescaleDB database
- GRPC API
- Importer
- Monitor
- REST API
- Rosetta API

Containers use the following persisted volumes:

- `./db` on your local machine maps to `/var/lib/postgresql/data` in the containers. This contains the files for the
  PostgreSQL/TimescaleDB database. If the database container fails to initialise properly and the database fails to run,
  you will have to delete this folder prior to attempting a restart otherwise the database initialisation scripts will
  not be run.

### Configuration

#### TimescaleDB vs PostgreSQL

To utilize the TimescaleDB database over the default PostgreSQL database, disable the PostgreSQL container and enable
the TimescaleDB container.

To achieve this the `docker-compose.yml` can be updated to set the postgres `db` service replicas to 0 whiles removing
this same setting from the `timescaledb` service as follows:

```yaml
    ...
    services:
      db:
        deploy:
          replicas: 0
      ...
      timescaledb:
      #    deploy:
      #      replicas: 0
      ...
```

### Starting

Before starting, [configure](configuration.md) the application by updating the [application.yml](../application.yml)
file with the desired custom values. This file is passed to Docker compose and allows customized configuration for each
of the sub modules.

Finally, run the commands to build and startup:

```console
docker-compose up
```

### Stopping

Shutting down the database container via `docker-compose down` may result in a corrupted database that may not restart
or may take longer than usual to restart. In order to avoid this, shell into the container and issue the following
command:

Use `docker ps` to get the name of the database container, it should be something like `hedera-mirror-node_db_1`.

Use the command `docker exec -it hedera-mirror-node_db_1 /bin/sh` to get a shell in the container.

`su - postgres -c "PGDATA=$PGDATA /usr/local/bin/pg_ctl -w stop"`

You may now power down the docker image itself.
