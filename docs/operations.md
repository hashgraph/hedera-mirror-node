# Operations

- [GRPC API](#grpc-api)
- [Importer](#importer)
- [Monitor](#monitor)
- [REST API](#rest-api)
- [Rosetta API](#rosetta-api)

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

### Historical Data Ingestion

The following resource allocation and configuration is recommended to speed up historical data ingestion. The importer
should be able to ingest one month's worth of mainnet data in less than 1.5 days.

1. Importer

- Resource allocation

  Run the importer with 4 vCPUs and 10 GB of heap.

- Configuration:

   ```yaml
     hedera:
       mirror:
         importer:
           downloader:
             record:
               batchSize: 600
               frequency: 1
           parser:
             record:
               entity:
                 redis:
                   enabled: false
               frequency: 10
               queueCapacity: 40
   ```

  Note once the importer has caught up all data, please change the configuration to the default where applicable.

2. PostgreSQL Database

- Resource allocation

  Run a PostgreSQL 13 instance with at least 4 vCPUs and 16 GB memory.

- Configuration:

  Set the following parameters. Note the unit is kilobytes.

  - max_wal_size = 8388608
  - work_mem = 262144

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
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.0/install.sh | bash
bash # reload shell
nvm install --lts #pin the current lts version node and npm provided by node distribution
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
wget "https://github.com/hashgraph/hedera-mirror-node/releases/download/${VERSION}/hedera-mirror-rest-${VERSION}.tgz"
tar --strip-components=1 -xvf hedera-mirror-rest-v*.tgz
pm2 start pm2.json
```

Please set `VERSION` to a release tag listed [here](https://github.com/hashgraph/hedera-mirror-node/releases).

You can use the following shell command to get the latest release tag:

```shell script
VERSION=$(curl -s https://github.com/hashgraph/hedera-mirror-node/releases/latest | sed -n -e 's/^.*\(v[0-9.]\+\).*$/\1/p')
```

### File Layout

- `/opt/restapi` - Binaries
- `/opt/restapi/application.yml` - Configuration

### Upgrading

Replace the `VERSION` below with the appropriate version:

```shell script
sudo su - restapi
cd /opt/restapi
pm2 stop all
wget "https://github.com/hashgraph/hedera-mirror-node/releases/download/${VERSION}/hedera-mirror-rest-${VERSION}.tgz"
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

To set up live monitoring, see [monitoring](/hedera-mirror-rest/monitoring/README.md) documentation.

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
