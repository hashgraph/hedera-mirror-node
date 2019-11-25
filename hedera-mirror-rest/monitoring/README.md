# Monitoring a live deployment of Hedera Mirror Node

This code runs on an external server outside of the Hedera Beta MirrorNode deployment, and periodically polls the REST APIs exposed by the Hedera mirror node to ensure that the deployed APIs are working.
It also provides a simple dashboard to monitor the status.

## Overview

Hedera mirror nodes REST APIs expose /transactions, /balances and /accounts endpoints.
To monitor a live deployment of a Hedera mirror node, this code consists of monitoring APIs and monitoring dashboard as described below.

#### Monitoring APIs:

A process runs that periodically polls the APIs exposed by the deployment of a Hedera mirror node.
It then checks the responses using a few simple checks for /transactions, /balances and /accounts APIs.
The results of these checks are exposed as a set of REST APIs exposed by this monitoring service as follows:

| API                 | HTTP return code | Description                                                                                                                                          |
| ------------------- | ---------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| /api/v1/status      | 200(OK)          | Provides a list of results of all tests run on all servers                                                                                           |
| /api/v1/status/{id} | 200 (OK)         | If all tests pass for a server, then it returns the results                                                                                          |
|                     | 4xx              | If any tests fail for a server, or if the server is not running, then it returns a 4xx error code to make it easy to integrate with alerting systems |

#### Monitoring dashboard:

A dashboard polls the above-mentioned APIs and displays the results.

---

## Quickstart

### Requirements

-   [ ] List of addresses of Hedera mirror nodes that you want to monitor
-   [ ] An external server where you want to run this code to monitor the mirror node. You will need two TCP ports on the server.
-   [ ] npm and pm2

```
git clone git@github.com:hashgraph/hedera-mirror-node.git
cd hedera-mirror-node/hedera-mirror-rest/monitoring
```

To run the monitor_apis backend:

```
cd monitor_apis
cp config/sample.serverlist.json config/serverlist.json // Start with the sample configuration file
nano config/serverlist.json // Insert the mirror node deployments you want to monitor
npm install  // Install npm dependencies
PORT=xxxx npm start  // To start the monitoring server on port xxxx (Note: please enter a number for xxxx)
```

The server will start polling Hedera mirror nodes specified in the config/serverlist.json file.
The default timeout to populate the data is 2 minutes. After 2 minutes, you can verify the output using `curl <ip-address-where-you-run-monitoring-service>:<port>/api/v1/status` command.

To run the dashboard (from `hedera-mirror-rest/monitoring` directory):

```
cd monitor_dashboard
nano js/main.js // Change the server: 'localhost:3000' line to point to the ip-address/name and port of the server where you are running the monitoring backed as described in the above tests.
pm2 serve . yyyy // Serve the dashboard html pages on another port...(Note: please enter a number for yyyy)
```

Using your browser, connect to `http:<ip-address-where-you-run-monitoring-service>:<yyyy>/index.html`

---

## Contributing

Refer to [CONTRIBUTING.md](CONTRIBUTING.md)

## License

Apache License 2.0, see [LICENSE](LICENSE).
