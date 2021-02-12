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

### Verifying

The gRPC streaming endpoint can be verified using clients that support [HTTP/2](https://http2.github.io/). Some useful
clients we've encountered include:

- [grpcurl](https://github.com/fullstorydev/grpcurl)
  - Run the following command making substitutions for `topicNum`, `grpcContainerIP` and `grpcContainerPort`:

```shell
grpcurl -plaintext -d '{"topicID":{"shardNum":0,"realmNum":0,"topicNum":{topicNum}},"consensusStartTime":{"seconds":0,"nanos":0},"limit":10}' {grpcContainerIP}:{grpcContainerPort} com.hedera.mirror.api.proto.ConsensusService/subscribeTopic
```

- [Bloom](https://github.com/uw-labs/bloomrpc)

## Importer

The Importer process is a Java-based application and should be able to run on any platform that Java supports. That
said, we recommend Ubuntu 18.04 be used as the base operating system as that is the only OS we've tested against.

### File Layout

- `/etc/systemd/system/hedera-mirror-importer.service` - systemd service definitions
- `/usr/etc/hedera-mirror-importer/application.yml` - Configuration file
- `/usr/lib/hedera-mirror-importer` - Binaries

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

In an effort to increase performance and reduce storage costs, the Mirror Node DB schema shifted from PostgreSQL (v1) to
TimescaleDB (v2).
[Migrating from a Different PostgreSQL Database](https://docs.timescale.com/latest/getting-started/migrating-data#different-db)
highlights the general recommended data migration steps when moving to TimescaleDB.

For mirror node operators running the v1 database schema, the following steps can be taken to upgrade to v2.

1. Upgrade the importer

   Ensure the importer is on the latest version and all v1 database migrations have completed before continuing.

2. Stop the importer

   If still on a version that still downloads files to the filesystem, ensure the valid directory for each stream type
   is empty (e.g. `/var/lib/hedera-mirror-importer/*/valid/`   where `/var/lib/hedera-mirror-importer` is the
   configured `dataPath`). If it's not empty, let the importer process fully catch up before attempting the migration.

3. Set up a new TimescaleDB database

   A new TimescaleDB server must be spun up. Refer to the Mirror Node [DB Installation](installation.md#database-setup)
   for manual instructions. For example, to use the Mirror Node configured docker container, simply run:

    ```shell script
    $ docker-compose up timescaledb
    ```

   Refer to the [TimescaleDB Installation Instructions](https://docs.timescale.com/latest/getting-started/installation)
   for other installation options.

   > **_NOTE:_** The following steps assume the database, users and schema have been created as detailed above

4. Configure properties

   First, locate the directory that contains the TimescaleDB migration scripts. For source code, the target directory is
   located at `hedera-mirror-importer/src/main/resources/db/scripts/timescaledb`. For binaries, the migration directory
   can be extracted from the JAR file:

   ```shell
   jar -xvf hedera-mirror-importer-*.jar
   cd BOOT-INF/classes/db/scripts/timescaledb
   ```

   The configuration file `migration.config` contains database variables that need to be modified before running the
   migration. These options include variables such as database names, passwords, users, hosts for both the existing
   database and the new database. Update these values appropriately for your database setup.

5. Run migration

   Run the migration script that was previously extracted:

    ```shell
    $ ./migration.sh
    ```

   The script uses successive `psql` connections to back up, configure and restore data on the new database nodes. First
   it copies over the `flyway_schema_history` table, to maintain migration history. It then utilizes the migration SQL
   script used by normal flyway operations to create the new tables and then creates the TimescaleDB hypertables based
   on these. Following this, the tables from the old database are backed up as CSV files using `COPY` and then the data
   inserted into the new database also using `COPY`. Finally, the schema of the `flyway_schema_history` is updated and
   the sequence values are updated to ensure continuation.

6. Start the importer

   Configure the importer with the new database information. Note that the default schema has been changed from `public`
   to `mirrornode` and the database user has been split into a separate owner and regular user. The importer will need
   to be updated with the same values specified in the `migration.config`. Example configuration:

   ```yaml
   hedera:
     mirror:
       importer:
         db:
           host: timescaledb_host
           owner: mirror_node
           ownerPassword: mirror_node_pass
           password: mirror_importer_pass
           port: 5432
           schema: mirrornode
           username: mirror_importer
   ```

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

### Verifying

The REST API endpoints can be verified either through the browser or the terminal. The following endpoints are
suggestions that can be accessed from your browser. Modify the below IP's and ports if they differ from your running
containers.

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

## Rosetta API

### Initial Installation / Upgrade

The Rosetta API runs on [Go](https://golang.org/) and should be able to run on any platform that Golang supports. That
said, we recommend Ubuntu 18.04 be used as the base operating system as that is the only OS we've tested against.

```shell script
wget "https://github.com/hashgraph/hedera-mirror-node/releases/download/v0.27.0/hedera-mirror-rosetta-v0.27.0.tgz"
tar -xvf hedera-mirror-rosetta-v*.tgz
cd hedera-mirror-rosetta-*/scripts
sudo ./deploy.sh
```

### File Layout

- `/usr/lib/hedera-mirror-rosetta` - Binaries
- `/usr/etc/hedera-mirror-rosetta/application.yml` - Configuration file

### Starting

```
sudo systemctl start hedera-mirror-rosetta.service
```

The Rosetta API container will display logs similar to the below at start:

    Successfully connected to Database
    Serving Rosetta API in ONLINE mode
    Listening on port 5700

### Stopping

```
sudo systemctl stop hedera-mirror-rosetta.service
```

### Monitoring

```
systemctl status hedera-mirror-rosetta.service
sudo journalctl -fu hedera-mirror-rosetta.service
```

### Verifying

The REST API endpoints can be verified through the terminal using the `curl` command. The following endpoint is a
suggestion to get the genesis block. Modify the below IP's and ports if they differ from your running containers.

```shell script
curl -H "Content-Type: application/json" -d '{ "network_identifier": {"blockchain":"Hedera", "network": "testnet", "sub_network_identifier": { "network": "shard 0 realm 0" }}, "block_identifier": {"index":0} }' 'http://localhost:5700/block'
```
