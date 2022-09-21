# Configuration

The four components of the Hedera Mirror Node (Importer, Monitor, REST API, and gRPC API) all support loading
configuration from an `application.yml` file or via the environment.

## Default Values

The default configuration allows users to quickly get up and running without having to configure anything. This provides
ease of use at the trade-off of some insecure default configuration. Most configuration settings have appropriate
defaults and can be left unchanged. It is recommended to browse the properties below and adjust to your needs.

One of the important settings that should be changed for all components is the `network` property as it controls which
of the Hedera networks to mirror. Additionally, the password properties have a default, but it is **strongly recommended
passwords be changed from the default**.

Depending upon your deployment tool, the process to modify this configuration may vary. For our Helm charts, we do
support automatic generation of random [passwords](/charts/README.md#passwords).

## Importer

The configuration section has been moved!  Please visit the [Importer configuration](/docs/importer/configuration.md) for specific information about configuring and using the Importer.

## GRPC API

Similar to the [Importer](#importer), the gRPC API uses [Spring Boot](https://spring.io/projects/spring-boot) properties
to configure the application.

The following table lists the available properties along with their default values. Unless you need to set a non-default
value, it is recommended to only populate overridden properties in the custom `application.yml`.

| Name                                                        | Default          | Description                                                                                                                                                                                   |
|-------------------------------------------------------------|------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `hedera.mirror.grpc.addressbook.cacheExpiry`                | 5s               | The amount of time to cache address book entries                                                                                                                                              |
| `hedera.mirror.grpc.addressbook.cacheSize`                  | 50               | The maximum number of address book pages to cache                                                                                                                                             |
| `hedera.mirror.grpc.addressbook.maxPageDelay`               | 250ms            | The maximum amount of time to sleep between paging for address book entries                                                                                                                   |
| `hedera.mirror.grpc.addressbook.minPageDelay`               | 100ms            | The minimum amount of time to sleep between paging for address book entries                                                                                                                   |
| `hedera.mirror.grpc.addressbook.pageSize`                   | 10               | The maximum number of address book entries to return in a single page                                                                                                                         |
| `hedera.mirror.grpc.checkTopicExists`                       | true             | Whether to throw an error when the topic doesn't exist                                                                                                                                        |
| `hedera.mirror.grpc.db.host`                                | 127.0.0.1        | The IP or hostname used to connect to the database                                                                                                                                            |
| `hedera.mirror.grpc.db.name`                                | mirror_node      | The name of the database                                                                                                                                                                      |
| `hedera.mirror.grpc.db.password`                            | mirror_grpc_pass | The database password used to connect to the database.                                                                                                                                        |
| `hedera.mirror.grpc.db.port`                                | 5432             | The port used to connect to the database                                                                                                                                                      |
| `hedera.mirror.grpc.db.sslMode`                             | DISABLE          | The ssl level of protection against Eavesdropping, Man-in-the-middle (MITM) and Impersonation on the db connection. Accepts either DISABLE, ALLOW, PREFER, REQUIRE, VERIFY_CA or VERIFY_FULL. |
| `hedera.mirror.grpc.db.username`                            | mirror_grpc      | The username used to connect to the database                                                                                                                                                  |
| `hedera.mirror.grpc.endTimeInterval`                        | 30s              | How often we should check if a subscription has gone past the end time                                                                                                                        |
| `hedera.mirror.grpc.entityCacheSize`                        | 50000            | The maximum size of the cache to store entities used for existence check                                                                                                                      |
| `hedera.mirror.grpc.listener.enabled`                       | true             | Whether to listen for incoming massages or not                                                                                                                                                |
| `hedera.mirror.grpc.listener.interval`                      | 500ms            | How often to poll or retry errors (varies by type). Can accept duration units like `50ms`, `10s`, etc.                                                                                        |
| `hedera.mirror.grpc.listener.maxBufferSize`                 | 16384            | The maximum number of messages the notifying listener or the shared polling listener buffers before sending an error to a client                                                              |
| `hedera.mirror.grpc.listener.maxPageSize`                   | 5000             | The maximum number of messages the listener can return in a single call to the database                                                                                                       |
| `hedera.mirror.grpc.listener.prefetch`                      | 48               | The prefetch queue size for shared listeners                                                                                                                                                  |
| `hedera.mirror.grpc.listener.type`                          | REDIS            | The type of listener to use for incoming messages. Accepts either NOTIFY, POLL, REDIS or SHARED_POLL                                                                                          |
| `hedera.mirror.grpc.netty.executorCoreThreadCount`          | 10               | The number of core threads                                                                                                                                                                    |
| `hedera.mirror.grpc.netty.executorMaxThreadCount`           | 1000             | The maximum allowed number of threads                                                                                                                                                         |
| `hedera.mirror.grpc.netty.maxConnectionIdle`                | 10m              | The max amount of time a connection can be idle before it will be gracefully terminated                                                                                                       |
| `hedera.mirror.grpc.netty.maxConcurrentCallsPerConnection`  | 5                | The maximum number of concurrent calls permitted for each incoming connection                                                                                                                 |
| `hedera.mirror.grpc.netty.maxInboundMessageSize`            | 1024             | The maximum message size allowed to be received on the server                                                                                                                                 |
| `hedera.mirror.grpc.netty.maxInboundMetadataSize`           | 1024             | The maximum size of metadata allowed to be received                                                                                                                                           |
| `hedera.mirror.grpc.netty.threadKeepAliveTime`              | 1m               | The amount of time for which threads may remain idle before being terminated                                                                                                                  |
| `hedera.mirror.grpc.port`                                   | 5600             | The GRPC API port                                                                                                                                                                             |
| `hedera.mirror.grpc.retriever.enabled`                      | true             | Whether to retrieve historical massages or not                                                                                                                                                |
| `hedera.mirror.grpc.retriever.maxPageSize`                  | 1000             | The maximum number of messages the retriever can return in a single call to the database                                                                                                      |
| `hedera.mirror.grpc.retriever.pollingFrequency`             | 2s               | How often to poll for historical messages. Can accept duration units like `50ms`, `10s` etc                                                                                                   |
| `hedera.mirror.grpc.retriever.threadMultiplier`             | 4                | Multiplied by the CPU count to calculate the number of retriever threads                                                                                                                      |
| `hedera.mirror.grpc.retriever.timeout`                      | 60s              | How long to wait between emission of messages before returning an error                                                                                                                       |
| `hedera.mirror.grpc.retriever.unthrottled.maxPageSize`      | 5000             | The maximum number of messages the retriever can return in a single call to the database when unthrottled                                                                                     |
| `hedera.mirror.grpc.retriever.unthrottled.maxPolls`         | 12               | The max number of polls when unthrottled                                                                                                                                                      |
| `hedera.mirror.grpc.retriever.unthrottled.pollingFrequency` | 20ms             | How often to poll for messages when unthrottled. Can accept duration units like `50ms`, `10s` etc                                                                                             |

## Monitor

Similar to the [Importer](#importer), the monitor uses [Spring Boot](https://spring.io/projects/spring-boot) properties
to configure the application.

The following table lists the available properties along with their default values. Unless you need to set a non-default
value, it is recommended to only populate overridden properties in the custom `application.yml`.

See the monitor [documentation](/docs/monitor/README.md) for more general information about configuring and using the
monitor.

Name                                                            | Default | Description
----------------------------------------------------------------|---------| ---------------------------------------
`hedera.mirror.monitor.mirrorNode.grpc.host`                    | ""      | The hostname of the mirror node's gRPC API
`hedera.mirror.monitor.mirrorNode.grpc.port`                    | 5600    | The port of the mirror node's gRPC API
`hedera.mirror.monitor.mirrorNode.rest.host`                    | ""      | The hostname of the mirror node's REST API
`hedera.mirror.monitor.mirrorNode.rest.port`                    | 443     | The port of the mirror node's REST API
`hedera.mirror.monitor.network`                                 | TESTNET | Which network to connect to. Automatically populates the main node & mirror node endpoints. Can be `MAINNET`, `PREVIEWNET`, `TESTNET` or `OTHER`
`hedera.mirror.monitor.nodes[].accountId`                       | ""      | The main node's account ID
`hedera.mirror.monitor.nodes[].host`                            | ""      | The main node's hostname
`hedera.mirror.monitor.nodes[].port`                            | 50211   | The main node's port
`hedera.mirror.monitor.nodeValidation.enabled`                  | true    | Whether to validate and remove invalid or down nodes permanently before publishing
`hedera.mirror.monitor.nodeValidation.frequency`                | 1d      | The amount of time between validations of the network.
`hedera.mirror.monitor.nodeValidation.maxAttempts`              | 8       | The number of times the monitor should attempt to receive a healthy response from a node before marking it as unhealthy.
`hedera.mirror.monitor.nodeValidation.maxBackoff`               | 2s      | The maximum amount of time to wait in between attempts when trying to validate a node
`hedera.mirror.monitor.nodeValidation.maxThreads`               | 25      | The maximum number of threads to use for node validation
`hedera.mirror.monitor.nodeValidation.minBackoff`               | 500ms   | The minimum amount of time to wait in between attempts when trying to validate a node
`hedera.mirror.monitor.nodeValidation.requestTimeout`           | 15s     | The amount of time to wait for a validation request before timing out
`hedera.mirror.monitor.nodeValidation.retryBackoff`             | 2m      | The fixed amount of time to wait in between unsuccessful node validations that result in no valid nodes
`hedera.mirror.monitor.operator.accountId`                      | ""      | Operator account ID used to pay for transactions
`hedera.mirror.monitor.operator.privateKey`                     | ""      | Operator ED25519 private key used to sign transactions in hex encoded DER format
`hedera.mirror.monitor.publish.async`                           | true    | Whether to use the SDK's asynchronous execution or synchronous. Synchronous requires more monitor responseThreads.
`hedera.mirror.monitor.publish.batchDivisor`                    | 100     | The divisor used to calculate batch size when generating transactions
`hedera.mirror.monitor.publish.clients`                         | 4       | How many total SDK clients to publish transactions. Clients will be used in a round-robin fashion
`hedera.mirror.monitor.publish.enabled`                         | true    | Whether to enable transaction publishing
`hedera.mirror.monitor.publish.nodeMaxBackoff`                  | 1m      | The maximum backoff time for any node in the network
`hedera.mirror.monitor.publish.responseThreads`                 | 40      | How many threads to use to resolve the asynchronous responses
`hedera.mirror.monitor.publish.scenarios`                       |         | A map of scenario name to publish scenarios. The name is used as a unique identifier in logs, metrics, and the REST API
`hedera.mirror.monitor.publish.scenarios.<name>.duration`       |         | How long this scenario should publish transactions. Leave empty for infinite
`hedera.mirror.monitor.publish.scenarios.<name>.enabled`        | true    | Whether this publish scenario is enabled
`hedera.mirror.monitor.publish.scenarios.<name>.limit`          | 0       | How many transactions to publish before halting. 0 for unlimited
`hedera.mirror.monitor.publish.scenarios.<name>.logResponse`    | false   | Whether to log the response from HAPI
`hedera.mirror.monitor.publish.scenarios.<name>.properties`     | {}      | Key/value pairs used to configure the [`TransactionSupplier`](/hedera-mirror-monitor/src/main/java/com/hedera/mirror/monitor/publish/transaction) associated with this scenario type
`hedera.mirror.monitor.publish.scenarios.<name>.receiptPercent` | 0.0     | The percentage of receipts to retrieve from HAPI. Accepts values between 0-1
`hedera.mirror.monitor.publish.scenarios.<name>.recordPercent`  | 0.0     | The percentage of records to retrieve from HAPI. Accepts values between 0-1
`hedera.mirror.monitor.publish.scenarios.<name>.retry.maxAttempts` | 1       | The maximum number of times a scenario transaction will be attempted
`hedera.mirror.monitor.publish.scenarios.<name>.timeout`        | 12s     | How long to wait for the transaction result
`hedera.mirror.monitor.publish.scenarios.<name>.tps`            | 1.0     | The rate at which transactions will publish
`hedera.mirror.monitor.publish.scenarios.<name>.type`           |         | The type of transaction to publish. See the [`TransactionType`](/hedera-mirror-monitor/src/main/java/com/hedera/mirror/monitor/publish/transaction/TransactionType.java) enum for a list of possible values
`hedera.mirror.monitor.publish.statusFrequency`                 | 10s     | How often to log publishing statistics
`hedera.mirror.monitor.publish.warmupPeriod`                    | 30s     | The amount of time the publisher should ramp up its rate before reaching its stable (maximum) rate
`hedera.mirror.monitor.nodeValidation.retrieveAddressBook`      | true    | Whether to download the address book from the mirror node and use those nodes to publish transactions
`hedera.mirror.monitor.subscribe.clients`                       | 1       | How many SDK clients should be created to subscribe to mirror node APIs. Clients will be used in a round-robin fashion
`hedera.mirror.monitor.subscribe.enabled`                       | true    | Whether to enable subscribing to mirror node APIs to verify published transactions
`hedera.mirror.monitor.subscribe.grpc`                          |         | A map of scenario name to gRPC subscriber scenarios. The name is used as a unique identifier in logs, metrics, and the REST API
`hedera.mirror.monitor.subscribe.grpc.<name>.duration`          |         | How long to stay subscribed to the API
`hedera.mirror.monitor.subscribe.grpc.<name>.enabled`           | true    | Whether this subscribe scenario is enabled
`hedera.mirror.monitor.subscribe.grpc.<name>.limit`             | 0       | How many transactions to receive before halting. 0 for unlimited
`hedera.mirror.monitor.subscribe.grpc.<name>.retry.maxAttempts` | 2^63 - 1 | How many consecutive retry attempts before giving up connecting to the API
`hedera.mirror.monitor.subscribe.grpc.<name>.retry.maxBackoff`  | 8s      | The maximum amount of time to wait between retry attempts
`hedera.mirror.monitor.subscribe.grpc.<name>.retry.minBackoff`  | 500ms   | The initial amount of time to wait between retry attempts
`hedera.mirror.monitor.subscribe.grpc.<name>.startTime`         |         | The start time passed to the gRPC API. Defaults to current time if not set
`hedera.mirror.monitor.subscribe.grpc.<name>.subscribers`       | 1       | How many concurrent subscribers should be instantiated for this scenario
`hedera.mirror.monitor.subscribe.grpc.<name>.topicId`           |         | Which topic to subscribe to
`hedera.mirror.monitor.subscribe.rest`                          |         | A map of scenario name to REST subscriber scenarios. The name is used as a unique identifier in logs, metrics, and the REST API
`hedera.mirror.monitor.subscribe.rest.<name>.duration`          |         | How long to stay subscribed to the API
`hedera.mirror.monitor.subscribe.rest.<name>.enabled`           | true    | Whether this subscribe scenario is enabled
`hedera.mirror.monitor.subscribe.rest.<name>.limit`             | 0       | How many transactions to receive before halting. 0 for unlimited
`hedera.mirror.monitor.subscribe.rest.<name>.publishers`        | []      | A list of publisher scenario names to consider for sampling
`hedera.mirror.monitor.subscribe.rest.<name>.retry.maxAttempts` | 16      | How many consecutive retry attempts before giving up connecting to the API
`hedera.mirror.monitor.subscribe.rest.<name>.retry.maxBackoff`  | 1s      | The maximum amount of time to wait between retry attempts
`hedera.mirror.monitor.subscribe.rest.<name>.retry.minBackoff`  | 500ms   | The initial amount of time to wait between retry attempts
`hedera.mirror.monitor.subscribe.rest.<name>.samplePercent`     | 1.0     | The percentage of transactions to verify against the API. Accepts values between 0-1
`hedera.mirror.monitor.subscribe.rest.<name>.timeout`           | 5s      | Maximum amount of time to wait for a API call to retrieve data
`hedera.mirror.monitor.subscribe.statusFrequency`               | 10s     | How often to log subscription statistics

## REST API

The REST API supports loading configuration from YAML or environment variables. By default, it loads a file named
`application.yml` or `application.yaml` in each of the search paths (see below). The file name can be changed by setting
the `CONFIG_NAME` environment variable. A custom location can be loaded by setting the `CONFIG_PATH` environment
variable. The configuration is loaded in the following order with the latter configuration overwriting (technically
recursively merged into) the current configuration:

1. `./config/application.yml`
2. `./application.yml`
3. `${CONFIG_PATH}/application.yml`
4. Environment variables that start with `HEDERA_MIRROR_REST_` (e.g. `HEDERA_MIRROR_REST_MAXLIMIT=100`)

The following table lists the available properties along with their default values. Unless you need to set a non-default
value, it is recommended to only populate overridden properties in the custom `application.yml`.

| Name                                                               | Default                 | Description                                                                                                                                                                                   |
|--------------------------------------------------------------------|-------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `hedera.mirror.rest.cache.entityId.maxAge`                         | 1800                    | The number of seconds until the entityId cache entry expires                                                                                                                                  |
| `hedera.mirror.rest.cache.entityId.maxSize`                        | 100000                  | The maximum number of entries in the entityId cache                                                                                                                                           |
| `hedera.mirror.rest.db.host`                                       | 127.0.0.1               | The IP or hostname used to connect to the database                                                                                                                                            |
| `hedera.mirror.rest.db.name`                                       | mirror_node             | The name of the database                                                                                                                                                                      |
| `hedera.mirror.rest.db.password`                                   | mirror_api_pass         | The database password the processor uses to connect.                                                                                                                                          |
| `hedera.mirror.rest.db.pool.connectionTimeout`                     | 20000                   | The number of milliseconds to wait before timing out when connecting a new database client                                                                                                    |
| `hedera.mirror.rest.db.pool.maxConnections`                        | 10                      | The maximum number of clients the database pool can contain                                                                                                                                   |
| `hedera.mirror.rest.db.pool.statementTimeout`                      | 20000                   | The number of milliseconds to wait before timing out a query statement                                                                                                                        |
| `hedera.mirror.rest.db.port`                                       | 5432                    | The port used to connect to the database                                                                                                                                                      |
| `hedera.mirror.rest.db.sslMode`                                    | DISABLE                 | The ssl level of protection against Eavesdropping, Man-in-the-middle (MITM) and Impersonation on the db connection. Accepts either DISABLE, ALLOW, PREFER, REQUIRE, VERIFY_CA or VERIFY_FULL. |
| `hedera.mirror.rest.db.tls.ca`                                     | ""                      | The path to the certificate authority used by the database for secure connections                                                                                                             |
| `hedera.mirror.rest.db.tls.cert`                                   | ""                      | The path to the public key the client should use to securely connect to the database                                                                                                          |
| `hedera.mirror.rest.db.tls.enabled`                                | false                   | Whether TLS should be used for the database connection                                                                                                                                        |
| `hedera.mirror.rest.db.tls.key`                                    | ""                      | The path to the private key the client should use to securely connect to the database                                                                                                         |
| `hedera.mirror.rest.db.username`                                   | mirror_api              | The username the processor uses to connect to the database                                                                                                                                    |
| `hedera.mirror.rest.log.level`                                     | info                    | The logging level. Can be trace, debug, info, warn, error or fatal.                                                                                                                           |
| `hedera.mirror.rest.maxRepeatedQueryParameters`                    | 100                     | The maximum number of times any query parameter can be repeated in the uri                                                                                                                    |
| `hedera.mirror.rest.maxTimestampRange`                             | 7d                      | The maximum amount of time a timestamp range query param can span for some APIs.                                                                                                              |
| `hedera.mirror.rest.metrics.enabled`                               | true                    | Whether metrics should be collected and exposed for scraping                                                                                                                                  |
| `hedera.mirror.rest.metrics.config`                                | See application.yml     | The configuration to pass to Swagger stats (https://swaggerstats.io/guide/conf.html#options)                                                                                                  |
| `hedera.mirror.rest.metrics.ipMetrics`                             | false                   | Whether metrics should be associated with a masked client IP label                                                                                                                            |
| `hedera.mirror.rest.network.unreleasedSupplyAccounts`              | [0.0.2, 0.0.42, ...]    | An array of account IDs whose aggregated balance subtracted from the total supply is the released supply                                                                                      |
| `hedera.mirror.rest.port`                                          | 5551                    | The REST API port                                                                                                                                                                             |
| `hedera.mirror.rest.metrics.enabled`                               | true                    | Whether metrics are enabled for the REST API                                                                                                                                                  |
| `hedera.mirror.rest.metrics.config.authentication`                 | true                    | Whether access to metrics for the REST API is authenticated                                                                                                                                   |
| `hedera.mirror.rest.metrics.config.username`                       | mirror_api_metrics      | The REST API metrics username to access the dashboard                                                                                                                                         |
| `hedera.mirror.rest.metrics.config.password`                       | mirror_api_metrics_pass | The REST API metrics password to access the dashboard                                                                                                                                         |
| `hedera.mirror.rest.metrics.config.uriPath`                        | '/swagger'              | The REST API metrics uri path                                                                                                                                                                 |
| `hedera.mirror.rest.openapi.specFileName`                          | 'openapi'               | The file name of the OpenAPI spec file                                                                                                                                                        |
| `hedera.mirror.rest.openapi.swaggerUIPath`                         | '/docs'                 | Swagger UI path for your REST API                                                                                                                                                             |
| `hedera.mirror.rest.response.compression`                          | true                    | Whether content negotiation should occur to compress response bodies if requested                                                                                                             |
| `hedera.mirror.rest.response.headers.default`                      | See application.yml     | The default headers to add to every response.                                                                                                                                                 |
| `hedera.mirror.rest.response.headers.path`                         | See application.yml     | The per path headers to add to every response. The key is the route name and the value is a header map.                                                                                       |
| `hedera.mirror.rest.response.includeHostInLink`                    | false                   | Whether to include the hostname and port in the next link in the response                                                                                                                     |
| `hedera.mirror.rest.response.limit.default`                        | 25                      | The default value for the limit parameter that controls the REST API response size                                                                                                            |
| `hedera.mirror.rest.response.limit.max`                            | 100                     | The maximum size the limit parameter can be that controls the REST API response size                                                                                                          |
| `hedera.mirror.rest.response.limit.tokenBalance.multipleAccounts`  | 1000                    | The maximum number of token balances per account for endpoints which return such info for multiple accounts                                                                                   |
| `hedera.mirror.rest.response.limit.tokenBalance.singleAccount`     | 2000                    | The maximum number of token balances per account for endpoints which return such info for a single account                                                                                    |
| `hedera.mirror.rest.shard`                                         | 0                       | The default shard number that this mirror node participates in                                                                                                                                |
| `hedera.mirror.rest.stateproof.enabled`                            | false                   | Whether to enable stateproof REST API or not                                                                                                                                                  |
| `hedera.mirror.rest.stateproof.streams.accessKey`                  | ""                      | The cloud storage access key                                                                                                                                                                  |
| `hedera.mirror.rest.stateproof.streams.bucketName`                 |                         | The cloud storage bucket name to download streamed files. This value takes priority over network hardcoded bucket names regardless of `hedera.mirror.rest.stateproof.streams.network`         |
| `hedera.mirror.rest.stateproof.streams.cloudProvider`              | S3                      | The cloud provider to download files from. Either `S3` or `GCP`                                                                                                                               |
| `hedera.mirror.rest.stateproof.streams.endpointOverride`           |                         | Can be specified to download streams from a source other than S3 and GCP. Should be S3 compatible                                                                                             |
| `hedera.mirror.rest.stateproof.streams.gcpProjectId`               |                         | GCP project id to bill for requests to GCS bucket which has Requester Pays enabled.                                                                                                           |
| `hedera.mirror.rest.stateproof.streams.httpOptions.connectTimeout` | 2000                    | The number of milliseconds to wait to establish a connection                                                                                                                                  |
| `hedera.mirror.rest.stateproof.streams.httpOptions.timeout`        | 5000                    | The number of milliseconds a request can take before automatically being terminated                                                                                                           |
| `hedera.mirror.rest.stateproof.streams.maxRetries`                 | 3                       | The maximum amount of retries to perform for a cloud storage download request.                                                                                                                |
| `hedera.mirror.rest.stateproof.streams.network`                    | DEMO                    | Which Hedera network to use. Can be either `DEMO`, `MAINNET`, `TESTNET`, `PREVIEWNET` or `OTHER`                                                                                              |
| `hedera.mirror.rest.stateproof.streams.region`                     | us-east-1               | The region associated with the bucket                                                                                                                                                         |
| `hedera.mirror.rest.stateproof.streams.secretKey`                  | ""                      | The cloud storage secret key                                                                                                                                                                  |

### Enable State Proof Alpha

To enable State Proof logic the REST API configurations must updated to allow for communication with cloud buckets to
pull down the necessary files (address book, signatures files and record file). The process involves setting the
properties under `hedera.mirror.rest.stateproof` as documented above [REST API Config](#rest-api).

An example configuration is provided below

```yaml
hedera:
  mirror:
    rest:
      stateproof:
        enabled: true
        streams:
          network: 'TESTNET'
          cloudProvider: 'GCP'
          region: 'us-east-1'
          accessKey: <accessKey>
          secretKey: <secretKey>
          bucketName: 'hedera-stable-testnet-streams-2020-08-27'
```

## Rosetta API

The Rosetta API supports loading configuration from YAML. By default, it loads a file named
`application.yml` in each of the search paths (see below). The configuration is loaded in the following order with the
latter configuration overwriting (technically recursively merged into) the current configuration:

1. Hard coded configuration embedded in the code
2. `./application.yml`
3. `${HEDERA_MIRROR_ROSETTA_API_CONFIG}` environment variable to custom values file (
   e.g. `HEDERA_MIRROR_ROSETTA_API_CONFIG=/Users/Downloads/hedera-mirror-rosetta/application.yml`)
4. Environment variables that start with `HEDERA_MIRROR_ROSETTA_` (e.g. `HEDERA_MIRROR_ROSETTA_API_VERSION=1.4.2`)

The following table lists the available properties along with their default values.

Name                                                 | Default             | Description
---------------------------------------------------- |---------------------| ----------------------------------------------------------------------------------------------
`hedera.mirror.rosetta.cache.entity.maxSize`         | 524288              | The max number of entities to cache
`hedera.mirror.rosetta.db.host`                      | 127.0.0.1           | The IP or hostname used to connect to the database
`hedera.mirror.rosetta.db.name`                      | mirror_node         | The name of the database
`hedera.mirror.rosetta.db.password`                  | mirror_rosetta_pass | The database password the processor uses to connect
`hedera.mirror.rosetta.db.pool.maxIdleConnections`   | 20                  | The maximum number of idle database connections
`hedera.mirror.rosetta.db.pool.maxLifetime`          | 30                  | The maximum lifetime of a database connection in minutes
`hedera.mirror.rosetta.db.pool.maxOpenConnections`   | 100                 | The maximum number of open database connections
`hedera.mirror.rosetta.db.port`                      | 5432                | The port used to connect to the database
`hedera.mirror.rosetta.db.statementTimeout`          | 20                  | The number of seconds to wait before timing out a query statement
`hedera.mirror.rosetta.db.username`                  | mirror_rosetta      | The username the processor uses to connect to the database
`hedera.mirror.rosetta.http.idleTimeout`             | 10000000000         | The maximum amount of time in nanoseconds to wait for the next request when keep-alives are enabled
`hedera.mirror.rosetta.http.readHeaderTimeout`       | 3000000000          | The maximum amount of time in nanoseconds to read request headers
`hedera.mirror.rosetta.http.readTimeout`             | 5000000000          | The maximum duration in nanoseconds for reading the entire request, including the body
`hedera.mirror.rosetta.http.writeTimeout`            | 10000000000         | The maximum duration in nanoseconds before timing out writes of the response
`hedera.mirror.rosetta.log.level`                    | info                | The log level
`hedera.mirror.rosetta.network`                      | DEMO                | Which Hedera network to use. Can be either `DEMO`, `MAINNET`, `PREVIEWNET`, `TESTNET` or `OTHER`
`hedera.mirror.rosetta.nodes`                        | {}                  | A map of main nodes with its service endpoint as the key and the node account id as its value
`hedera.mirror.rosetta.nodeVersion`                  | 0                   | The default canonical version of the node runtime
`hedera.mirror.rosetta.online`                       | true                | The default online mode of the Rosetta interface
`hedera.mirror.rosetta.port`                         | 5700                | The REST API port
`hedera.mirror.rosetta.shard`                        | 0                   | The default shard number that this mirror node participates in
`hedera.mirror.rosetta.realm`                        | 0                   | The default realm number within the shard

## Web3 API

Similar to the [Importer](#importer), the web3 API uses [Spring Boot](https://spring.io/projects/spring-boot) properties
to configure the application.

The following table lists the available properties along with their default values. Unless you need to set a non-default
value, it is recommended to only populate overridden properties in the custom `application.yml`.

Name                                                        | Default          | Description
------------------------------------------------------------| -----------------| ---------------------------------------
`hedera.mirror.web3.db.host`                                | 127.0.0.1        | The IP or hostname used to connect to the database
`hedera.mirror.web3.db.name`                                | mirror_node      | The name of the database
`hedera.mirror.web3.db.password`                            | mirror_web3_pass | The database password used to connect to the database
`hedera.mirror.web3.db.port`                                | 5432             | The port used to connect to the database
`hedera.mirror.web3.db.sslMode`                             | DISABLE          | The ssl level of protection against eavesdropping, man-in-the-middle (MITM) and impersonation on the db connection. Accepts either DISABLE, ALLOW, PREFER, REQUIRE, VERIFY_CA or VERIFY_FULL.
`hedera.mirror.web3.db.username`                            | mirror_web3      | The username used to connect to the database
