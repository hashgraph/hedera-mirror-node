# Installation

The Mirror Node can be ran [locally](#running-locally) or via [Docker](#running-via-docker-compose).
To run locally, it'll first need to be built using Java. To run via Docker, either build locally, or pull latest images
from GCR.

```console
docker-compose pull
```

## Building

Ensure you have OpenJDK 11 installed, then run the following command from the top level directory. This will
compile a runnable Mirror Node JAR file in the `target` directory.

```console
./mvnw clean package -DskipTests
```

## Running Locally

### Database Setup

In addition to OpenJDK 11, you will need to install [PostgreSQL](https://postgresql.org) 9.6 and initialize it. The only
setup required is to create the initial database and owner since [Flyway](https://flywaydb.org) manages the
database schema. The SQL script located at `hedera-mirror-importer/src/main/resources/db/scripts/init.sql` can be used to
accomplish this. Edit the file and change the `db_name`, `db_user`, `db_password` `db_owner`, `grpc_user`, or
`grpc_password` as appropriate.
Make sure the application [configuration](configuration.md) matches the values in the script. Run the script as a DB
admin user and check the output carefully to ensure no errors occurred.

```console
psql postgres -f hedera-mirror-importer/src/main/resources/db/scripts/init.sql
```

### Importer

To run the Importer, first populate the configuration at one of the supported
[configuration](configuration.md) paths, then run:

```console
java -jar hedera-mirror-importer/target/hedera-mirror-importer-*.jar
```

Additionally, there is a Systemd unit file located in the `hedera-mirror-importer/scripts/` directory that can be used to
manage the process. See the [operations](operations.md) documentation for more information.

### GRPC API

To run the GRPC API, first populate the configuration at one of the supported
[configuration](configuration.md) paths, then run:

```console
java -jar hedera-mirror-grpc/target/hedera-mirror-grpc-*.jar
```

Additionally, there is a Systemd unit file located in the `hedera-mirror-grpc/scripts/` directory that can be used to
manage the process. See the [operations](operations.md) documentation for more information.

### REST API

To start the REST API ensure you have the necessary [configuration](configuration.md) populated and run:

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

To start the Rosetta API ensure you have the necessary [configuration](configuration.md) populated and run:

```console
cd hedera-mirror-rosetta
go run cmd/*
```

#### Rosetta CLI Validation

After you have started the Rosetta API, in another terminal run:

```console
cd hedera-mirror-rosetta/validation
./run-validation.sh
```

Currently, Rosetta CLI Validation supports only **DEMO** and **TESTNET**, where
**DEMO** is default and **TESTNET** can be run via:
```console
./run-validation.sh testnet
```

## Running via Docker Compose

Docker Compose scripts are provided and run all the mirror node components:

-   PostgreSQL database
-   Importer
-   REST API
-   GRPC API

Containers use the following persisted volumes:

-   `./db` on your local machine maps to `/var/lib/postgresql/data` in the containers. This contains the files for the
    PostgreSQL database. If the database container fails to initialise properly and the database fails to run, you will
    have to delete this folder prior to attempting a restart otherwise the database initialisation scripts will not be
    run.

-   `./data` on your local machine maps to `/var/lib/hedera-mirror-importer` in the container. This contains files downloaded
    from S3 or GCP. These are necessary not only for the database data to be persisted, but also so that the parsing
    containers can access file obtained via the downloading containers

### Starting

Before starting, [configure](configuration.md) the application by updating the [application.yml](../application.yml) file with the desired custom
values. This file is passed to Docker compose and allows customized configuration for each of the sub modules.

Finally, run the commands to build and startup:

```console
docker-compose up
```

### Stopping

Shutting down the database container via `docker-compose down` may result in a corrupted database that may not restart
or may take longer than usual to restart. In order to avoid this, shell into the container and issue the following command:

Use `docker ps` to get the name of the database container, it should be something like `hedera-mirror-node_db_1`.

Use the command `docker exec -it hedera-mirror-node_db_1 /bin/sh` to get a shell in the container.

`su - postgres -c "PGDATA=$PGDATA /usr/local/bin/pg_ctl -w stop"`

You may now power down the docker image itself.
