# Installation

The Mirror Node can be run [locally](#running-locally) or via [Docker](#running-via-docker-compose).

## Building

To run locally, first build the project using Java. Ensure you have OpenJDK 17 installed, then run the following command
from the top level directory. This will compile a runnable mirror node JAR file in the `target` directory.

```console
./gradlew clean build -x test
```

## Running Locally

### Database Setup

In addition to OpenJDK 17, you will need to install a [PostgreSQL](https://postgresql.org) database and initialize it.

Since [Flyway](https://flywaydb.org) will manage the database schema, the only required step is to run the database
initialization script. Locate the SQL script at `hedera-mirror-importer/src/main/resources/db/scripts/init.sh` and edit
the file to change the name and password variables at the top of the file to the desired values. Then make sure the
application [configuration](configuration.md)
matches the values in the script. Run the SQL script as a super user and check the output carefully to ensure no errors
occurred.

```console
./hedera-mirror-importer/src/main/resources/db/scripts/init.sh
```

### Importer

To run the Importer, first populate the configuration at one of the supported
[configuration](configuration.md#importer) paths, then run:

```console
java -jar hedera-mirror-importer/build/libs/hedera-mirror-importer-*.jar
```

Additionally, there is a Systemd unit file located in the `hedera-mirror-importer/scripts/` directory that can be used
to manage the process. See the [operations](operations.md) documentation for more information.

### gRPC API

To run the gRPC API, first populate the configuration at one of the supported
[configuration](configuration.md#grpc-api) paths, then run:

```console
java -jar hedera-mirror-grpc/build/libs/hedera-mirror-grpc-*.jar
```

### Monitor

To run the monitor, first populate the configuration at one of the supported
[configuration](configuration.md#monitor) paths, then run:

```console
java -jar hedera-mirror-monitor/build/libs/hedera-mirror-monitor-*.jar
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
Go 1.20+
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

The `All-in-One` configuration aggregates the PostgreSQL, Importer, and Rosetta services into a single Dockerfile
configuration. Configuration is based on the Rosetta specification,
found [here](https://www.rosetta-api.org/docs/node_deployment.html). Data persistence is based on Rosetta specification
as well, found [here](https://www.rosetta-api.org/docs/standard_storage_location.html). Exposed ports are `5432`
(PostgreSQL) and `5700` (Rosetta).

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

For ease, the `NETWORK` environment variable can be set to override the Importer and Rosetta default Hedera network
configuration:

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

Docker Compose scripts are provided and can run all the mirror node components. Containers use the following persistent
volumes:

- `./db` on your local machine maps to `/var/lib/postgresql/data` in the db container. This contains the files for the
  PostgreSQL database. If the database container fails to initialise properly and the database fails to run, you will
  have to delete this folder prior to attempting a restart otherwise the database initialisation scripts will not be
  run.

### Configuration

Before starting, [configure](configuration.md) the application by updating the [application.yml](/application.yml)
file with the desired custom values. This file is passed to Docker Compose and allows customized configuration for each
of the mirror node components. The `application.yml` file contents represent the minimal set of fields required to
configure requester pays and must be uncommented and filled in.

### Starting

Finally, run the commands to build and startup:

```console
docker compose up
```

### Verifying

When running the mirror node using Docker, activity logs and container status for each module container can be viewed in
the [Docker Desktop Dashboard](https://docs.docker.com/desktop/dashboard/) or the `docker` CLI to verify expected
operation. You can also interact with mirror node APIs to verify their operation.

First list running docker container information using and verify an `Up` status is present:

```shell
$ docker ps
CONTAINER ID    IMAGE                                      COMMAND                 CREATED         STATUS          PORTS                   NAMES
21fa2a986d99    gcr.io/mirrornode/hedera-mirror-rest:main  "docker-entrypoint.s…"  7 minutes ago   Up 12 seconds   0.0.0.0:5551->5551/tcp  hedera-mirror-node_rest_1
56647c384d49    gcr.io/mirrornode/hedera-mirror-grpc:main  "java -cp /app/resou…"  8 minutes ago   Up 16 seconds   0.0.0.0:5600->5600/tcp  hedera-mirror-node_grpc_1
```

Using the IP address and port, the APIs' endpoints can be called to confirm data is processed and available.

#### Database

The following log can be used to confirm the database is up and running:

```shell
database system is ready to accept connections
```

If you have PostgreSQL installed you can connect directly to your database using the following `psql` command and
default [configurations](/docs/configuration.md):

```shell
psql "dbname=mirror_node host=localhost user=mirror_node password=mirror_node_pass port=5432"
```

If `psql` is not available locally you can `docker exec -it <CONTAINER ID> bash` into the db container and run the same
command above.

#### Importer

Logs similar to the following snippets can be used to confirm the Importer is downloading and persisting transactions to
the database:

```shell
o.f.c.i.c.DbMigrate Schema "public" is up to date. No migration necessary.
c.h.m.i.MirrorImporterApplication Started MirrorImporterApplication in 18.77 seconds
...
c.h.m.i.p.b.AccountBalanceFileParser Successfully processed 1474 items from 2022-01-05T18_30_00.150597126Z_Balances.pb.gz in 12.78 ms
c.h.m.i.d.r.RecordFileDownloader Downloaded 1 signatures in 102.8 ms (9/s)
c.h.m.i.p.r.RecordFileParser Successfully processed 2 items from 2022-01-05T18_16_24.581564299Z.rcd in 3.784 ms
```

#### GraphQL API

The GraphQL API container will display logs similar to the below at start:

```shell
c.h.m.graphql.GraphQlApplication Started GraphqlApplication in 23.05 seconds
```

To manually verify the GraphQL API endpoints run the [acceptance tests](/docs/graphql/README.md#acceptance-tests).

#### gRPC API

The gRPC container will display logs similar to the below at start:

```shell
c.h.m.g.MirrorGrpcApplication Started MirrorGrpcApplication in 15.808 seconds
```

To manually verify the gRPC streaming endpoint please consult the [operations](/docs/operations.md#verifying) guide.

#### REST API

The REST API container will display logs similar to the below at start:

```shell
Server running on port: 5551
```

To manually verify REST API endpoint please consult the [operations](/docs/operations.md#verifying-1) guide.

#### Rosetta API

The Rosetta API container will display logs similar to the below at start:

```shell
Successfully connected to Database
Serving Rosetta API in ONLINE mode
Listening on port 5700
```

To manually verify the Rosetta API endpoints follow the [operations](/docs/operations.md#verifying-2) details.

#### Web3 API

The Web3 API container will display logs similar to the below at start:

```shell
c.h.m.web3.Web3Application Started Web3Application in 27.808 seconds
```

To manually verify the Web3 API endpoints run the [acceptance tests](/docs/web3/README.md#acceptance-tests).

### Stopping

Shut down the containers via `docker compose down`.
