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
| /api/v1/status      | 200 (OK)         | Provides a list of results of all tests run on all servers                                                                                           |
| /api/v1/status/{id} | 200 (OK)         | If all tests pass for a server, then it returns the results                                                                                          |
|                     | 4xx              | If any tests fail for a server, or if the server is not running, then it returns a 4xx error code to make it easy to integrate with alerting systems |

#### Monitoring dashboard:

A dashboard polls the above-mentioned APIs and displays the results.

---

## Quickstart

### Requirements

-   [ ] List of addresses of Hedera mirror nodes that you want to monitor
-   [ ] An existing topic ID of the target Hedera Mirrornode environment if you want to run topic message tests
-   [ ] An external server where you want to run this code to monitor the mirror node. You will need two TCP ports on the server.
-   [ ] npm and pm2

```
git clone git@github.com:hashgraph/hedera-mirror-node.git
cd hedera-mirror-node/hedera-mirror-rest/monitoring
```

To install the dependencies and configure monitor_apis:

```
cd monitor_apis
npm install
cp config/sample.serverlist.json config/serverlist.json // Start with the sample configuration file
nano config/serverlist.json // Insert the mirror node deployments you want to monitor
```

To customize per-resource configuration:

- `freshnessThreshold` (in seconds) for `balance`, `transaction`, and `topic` can be adjusted independently if needed.
   Set to 0 to disable freshness check for a resource
- `intervalMultiplier` for all resources. The tests for a resource will run every `interval * intervalMultiplier` seconds.
   For `stateproof`, it defaults to 10, so the tests run at a lower frequency to reduce cost
- `limit` threshold for `account`, `balance`, `transatcion`, and `topic` can be adjusted independently if needed, e.g.,
  for environments with lower traffic volume
- set `topic.topicId` to an existing topic ID of the target environment. If not set, topic message tests will be skipped

```json
{
  "account": {
    "intervalMultiplier": 1,
    "limit": 1000
  },
  "balance": {
    "freshnessThreshold": 1000,
    "intervalMultiplier": 1,
    "limit": 1000
  },
  "stateproof": {
    "intervalMultiplier": 10
  },
  "transaction": {
    "freshnessThreshold": 50,
    "intervalMultiplier": 1,
    "limit": 1000
  },
  "topic": {
    "freshnessThreshold": 100,
    "intervalMultiplier": 1,
    "limit": 1000,
    "topicId": "0.0.1930"
  }
}
```

TO run the monitor_apis backend:

```
PORT=3000 pm2 start server.js
```

The server will start polling Hedera mirror nodes specified in the config/serverlist.json file.
The default interval to populate the data is 60 seconds. You can verify the output using `curl http://<host>:<port>/api/v1/status` command.

To run the dashboard (from `hedera-mirror-rest/monitoring` directory):

```
cd monitor_dashboard
echo '{"monitorAddress": "localhost:3000"}' > config.json
pm2 serve . 3001 // Serve the dashboard html pages on another port
```

Using your browser, connect to `http://<host>:<port>/index.html`
