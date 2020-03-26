# Operations

-   [GRPC API](#grpc-api)
-   [Importer](#importer)
-   [REST API](#rest-api)

## GRPC API

The GRPC process is a Java-based application and should be able to run on any platform that Java supports. That said,
we recommend Ubuntu 18.04 be used as the base operating system as that is the only OS we've tested against.

### File Layout

-   `/etc/systemd/system/hedera-mirror-grpc.service` - systemd service definitions
-   `/usr/etc/hedera-mirror-grpc/application.yml` - Configuration file
-   `/usr/lib/hedera-mirror-grpc` - Binaries

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

-   `/etc/systemd/system/hedera-mirror-importer.service` - systemd service definitions
-   `/usr/etc/hedera-mirror-importer/application.yml` - Configuration file
-   `/usr/lib/hedera-mirror-importer` - Binaries
-   `/var/lib/hedera-mirror-importer` - Data
    -   `addressbook.bin` - The current address book in use
    -   `accountBalances` - The downloaded balance and signature files
    -   `recordstreams` - The downloaded record and signature files

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
cd /opt/restapi
git clone https://github.com/hashgraph/hedera-mirror-node.git
cd hedera-mirror-node/hedera-mirror-rest
cat > application.yml <<EOF
hedera:
  mirror:
    db:
      apiPassword: mirror_api_pass
      host: dbhost
EOF
```

### File Layout

-   `/opt/restapi/logs` - Logs
-   `/opt/restapi/hedera-mirror-rest` - Binaries
-   `/opt/restapi/hedera-mirror-rest/application.yml` - Configuration

### Upgrading

```shell script
sudo su - restapi
cd /opt/restapi/hedera-mirror-rest
pm2 stop all
git fetch --all --tags --prune
git checkout tags/${version}
npm install
pm2 start pm2.json
```

### Starting

To start all 10 ports (6551-6560):

```shell script
pm2 start /opt/restapi/hedera-mirror-rest/pm2.json
```

To manually start on a specific port:

```shell script
HEDERA_MIRROR_API_PORT=8080 pm2 start server.js
```

### Stopping

```shell script
pm2 stop all
```

### Monitoring

```shell script
pm2 monit
pm2 status
tail -f /opt/restapi/logs/hedera_mirrornode_api_6551.log
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
