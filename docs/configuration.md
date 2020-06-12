# Configuration

The three components of the Hedera Mirror Node, Importer, REST API and GRPC API, all support loading configuration
from an `application.yml` file or via the environment.

Most configuration settings have appropriate defaults and can be left unchanged. One of the important settings that
should be changed is `hedera.mirror.importer.downloader.bucketName` since it refers to a demo bucket. The real bucket name
is not publicly available at this time. Another important property that should be adjusted is `hedera.mirror.importer.network` as
it controls which Hedera network to mirror. Additionally, the password properties have a default, but it is recommended
they be changed from the default.

## Importer

The Importer component uses [Spring Boot](https://spring.io/projects/spring-boot) properties to configure the application.
As as a result, propertiy files, YAML files, environment variables and command-line arguments can all be use to configure
the application. See the Spring Boot [documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/spring-boot-features.html#boot-features-external-config)
for the location and order it loads configuration.

The following table lists the available properties along with their default values. Unless you need to set a non-default
value, it is recommended to only populate overridden properties in the custom `application.yml`.

| Name                                                                 | Default                 | Description                                                                                    |
| -------------------------------------------------------------------- | ----------------------- | ---------------------------------------------------------------------------------------------- |
| `hedera.mirror.importer.dataPath`                                    | ./data                  | The data directory used to store downloaded files and other application state                  |
| `hedera.mirror.importer.db.restPassword`                             | mirror_api_pass         | The database password the API uses to connect. **Should be changed from default**              |
| `hedera.mirror.importer.db.restUsername`                             | mirror_api              | The username the API uses to connect to the database                                           |
| `hedera.mirror.importer.db.host`                                     | 127.0.0.1               | The IP or hostname used to connect to the database                                             |
| `hedera.mirror.importer.db.name`                                     | mirror_node             | The name of the database                                                                       |
| `hedera.mirror.importer.db.password`                                 | mirror_node_pass        | The database password the processor uses to connect. **Should be changed from default**        |
| `hedera.mirror.importer.db.port`                                     | 5432                    | The port used to connect to the database                                                       |
| `hedera.mirror.importer.db.username`                                 | mirror_node             | The username the processor uses to connect to the database                                     |
| `hedera.mirror.importer.downloader.accessKey`                        | ""                      | The cloud storage access key                                                                   |
| `hedera.mirror.importer.downloader.balance.batchSize`                | 15                      | The number of signature files to download per node before downloading the signed files         |
| `hedera.mirror.importer.downloader.balance.enabled`                  | true                    | Whether to enable balance file downloads                                                       |
| `hedera.mirror.importer.downloader.balance.frequency`                | 30s                     | The fixed period between invocations. Can accept duration units like `10s`, `2m` etc.          |
| `hedera.mirror.importer.downloader.balance.keepSignatures`           | false                   | Whether to keep balance signature files after successful verification. If false, files are deleted. |
| `hedera.mirror.importer.downloader.balance.prefix`                   | accountBalances/balance | The prefix to search cloud storage for balance files                                           |
| `hedera.mirror.importer.downloader.balance.threads`                  | 13                      | The number of threads to search for new files to download                                      |
| `hedera.mirror.importer.downloader.bucketName`                       | "hedera-demo-streams"   | The cloud storage bucket name to download streamed files                                       |
| `hedera.mirror.importer.downloader.cloudProvider`                    | S3                      | The cloud provider to download files from. Either `S3` or `GCP`                       |
| `hedera.mirror.importer.downloader.endpointOverride`                 |                         | Can be specified to download streams from a source other than S3 and GCP. Should be S3 compatible |
| `hedera.mirror.importer.downloader.event.batchSize`                  | 15                      | The number of signature files to download per node before downloading the signed files         |
| `hedera.mirror.importer.downloader.event.enabled`                    | false                   | Whether to enable event file downloads                                                         |
| `hedera.mirror.importer.downloader.event.frequency`                  | 1m                      | The fixed period between invocations. Can accept duration units like `50ms`, `10s` etc.        |
| `hedera.mirror.importer.downloader.event.keepSignatures`             | false                   | Whether to keep event signature files after successful verification. If false, files are deleted. |
| `hedera.mirror.importer.downloader.event.prefix`                     | eventsStreams/events\_  | The prefix to search cloud storage for event files                                             |
| `hedera.mirror.importer.downloader.event.threads`                    | 13                      | The number of threads to search for new files to download
| `hedera.mirror.importer.downloader.gcpProjectId`                     |                         | GCP project id to bill for requests to GCS bucket which has Requester Pays enabled.            |
| `hedera.mirror.importer.downloader.maxConcurrency`                   | 1000                    | The maximum number of allowed open HTTP connections. Used by AWS SDK directly.                 |
| `hedera.mirror.importer.downloader.record.batchSize`                 | 40                      | The number of signature files to download per node before downloading the signed files         |
| `hedera.mirror.importer.downloader.record.enabled`                   | true                    | Whether to enable record file downloads                                                        |
| `hedera.mirror.importer.downloader.record.frequency`                 | 500ms                   | The fixed period between invocations. Can accept duration units like `10s`, `2m` etc.          |
| `hedera.mirror.importer.downloader.record.keepSignatures`            | false                   | Whether to keep record signature files after successful verification. If false, files are deleted. |
| `hedera.mirror.importer.downloader.record.prefix`                    | recordstreams/record    | The prefix to search cloud storage for record files                                            |
| `hedera.mirror.importer.downloader.record.threads`                   | 13                      | The number of threads to search for new files to download                                      |
| `hedera.mirror.importer.downloader.region`                           | us-east-1               | The region associated with the bucket                                                          |
| `hedera.mirror.importer.downloader.secretKey`                        | ""                      | The cloud storage secret key                                                                   |
| `hedera.mirror.importer.initialAddressBook`                          | ""                      | The path to the bootstrap address book used to override the built-in address book              |
| `hedera.mirror.importer.network`                                     | DEMO                    | Which Hedera network to use. Can be either `DEMO`, `MAINNET` or `TESTNET`                      |
| `hedera.mirror.importer.parser.balance.batchSize`                    | 2000                    | The number of balances to insert before committing                                             |
| `hedera.mirror.importer.parser.balance.enabled`                      | true                    | Whether to enable balance file parsing                                                         |
| `hedera.mirror.importer.parser.balance.fileBufferSize`               | 200000                  | The size of the buffer to use when reading in the balance file                                 |
| `hedera.mirror.importer.parser.balance.keepFiles`                    | false                   | Whether to keep parsed files after successful parsing. If false, files are deleted.            |
| `hedera.mirror.importer.parser.exclude`                              | []                      | A list of filters that determine which transactions are ignored. Takes precedence over include |
| `hedera.mirror.importer.parser.exclude.entity`                       | []                      | A list of entity IDs to ignore in shard.realm.num (e.g. 0.0.3) format                          |
| `hedera.mirror.importer.parser.exclude.transaction`                  | []                      | A list of transaction types to ignore. See `TransactionTypeEnum.java` for possible values      |
| `hedera.mirror.importer.parser.include`                              | []                      | A list of filters that determine which transactions are stored                                 |
| `hedera.mirror.importer.parser.include.entity`                       | []                      | A list of entity IDs to store in shard.realm.num (e.g. 0.0.3) format                           |
| `hedera.mirror.importer.parser.include.transaction`                  | []                      | A list of transaction types to store. See `TransactionTypeEnum.java` for possible values       |
| `hedera.mirror.importer.parser.record.enabled`                       | true                    | Whether to enable record file parsing                                                          |
| `hedera.mirror.importer.parser.record.frequency`                     | 500ms                   | The fixed period between invocations. Can accept duration units like `10s`, `2m` etc.          |
| `hedera.mirror.importer.parser.record.keepFiles`                     | false                   | Whether to keep parsed files after successful parsing. If false, files are deleted.            |
| `hedera.mirror.importer.parser.record.entity.persist.claims`                | false                   | Persist claim data to the database                                                             |
| `hedera.mirror.importer.parser.record.entity.persist.contracts`             | true                    | Persist contract data to the database                                                          |
| `hedera.mirror.importer.parser.record.entity.persist.cryptoTransferAmounts` | true                    | Persist crypto transfer amounts to the database                                                |
| `hedera.mirror.importer.parser.record.entity.persist.files`                 | true                    | Persist all file data to the database                                                          |
| `hedera.mirror.importer.parser.record.entity.persist.nonFeeTransfers`       | false                   | Persist non-fee transfers for transactions that explicitly request hbar transfers              |
| `hedera.mirror.importer.parser.record.entity.persist.systemFiles`           | true                    | Persist only system files (number lower than `1000`) to the database                           |
| `hedera.mirror.importer.parser.record.entity.persist.transactionBytes`      | false                   | Persist raw transaction bytes to the database                                                  |
| `hedera.mirror.importer.parser.record.entity.sql.batchSize`                 | 2000                    | When inserting transactions into db, executeBatches() is called every these many transactions  |
| `hedera.mirror.importer.parser.record.pubsub.topicName`                     |                         | Pubsub topic to publish transactions to                                                        |
| `hedera.mirror.importer.parser.record.pubsub.maxSendAttempts`               | 5                       | Number of attempts when sending messages to PubSub (only for retryable errors)                 |
| `hedera.mirror.importer.topicRunningHashV2AddedTimestamp`            | Network-based  | Unix timestamp (in nanos) of first topic message with v2 as running hash version. Use this config to override the default network based value |
| `hedera.mirror.importer.shard`                                       | 0                       | The default shard number that the component participates in                                    |
| `hedera.mirror.importer.verfiyHashAfter`                             | 1970-01-01T00:00:00Z  | Streams in which files are linked using hashes (prevHash) to ensure file ordering, the check would be skipped until after (and not including) this point of time. Value should be a string parsable by Instant. For instance, YYYY-MM-DDTHH:MM:SS.fffffffffZ |

#### Export transactions to PubSub

Importer can be configured to publish transactions (in json format) to a Pubsub topic using following properties:

-   `spring.cloud.gcp.pubsub.enabled`
-   `spring.cloud.gcp.pubsub.project-id`
-   `hedera.mirror.importer.parser.record.pubsub.topicName`
-   `spring.cloud.gcp.pubsub.credentials.*`
-   `hedera.mirror.importer.parser.record.entity.enabled` (Importer can not export to both database and pubsub simultaneously)

See [Spring Cloud documentation](https://cloud.spring.io/spring-cloud-static/spring-cloud-gcp/1.2.2.RELEASE/reference/html/#pubsub-configuration)
for more info about `spring.cloud.gcp.*` properties.

## GRPC API

Similar to the [Importer](#importer), the GRPC API uses [Spring Boot](https://spring.io/projects/spring-boot) properties to configure the application.

The following table lists the available properties along with their default values. Unless you need to set a non-default
value, it is recommended to only populate overridden properties in the custom `application.yml`.

| Name                                                        | Default          | Description                                                                                    |
| ------------------------------------------------------------| -----------------| -----------------------------------------------------------------------------------------------|
| `hedera.mirror.grpc.checkTopicExists`                       | true             | Whether to throw an error when the topic doesn't exist                                         |
| `hedera.mirror.grpc.db.host`                                | 127.0.0.1        | The IP or hostname used to connect to the database                                             |
| `hedera.mirror.grpc.db.name`                                | mirror_node      | The name of the database                                                                       |
| `hedera.mirror.grpc.db.password`                            | mirror_grpc_pass | The database password the GRPC API uses to connect. **Should be changed from default**         |
| `hedera.mirror.grpc.db.port`                                | 5432             | The port used to connect to the database                                                       |
| `hedera.mirror.grpc.db.username`                            | mirror_grpc      | The username the GRPC API uses to connect to the database                                      |
| `hedera.mirror.grpc.endTimeInterval`                        | 30s              | How often we should check if a subscription has gone past the end time                         |
| `hedera.mirror.grpc.entityCacheSize`                        | 50000            | The maximum size of the cache to store entities used for existence check                       |
| `hedera.mirror.grpc.listener.bufferInitial`                 | 5s               | The amount of time to backfill the listening buffer                                            |
| `hedera.mirror.grpc.listener.bufferSize`                    | 50000            | The number of messages to store in the listening buffer                                        |
| `hedera.mirror.grpc.listener.enabled`                       | true             | Whether to listen for incoming massages or not                                                 |
| `hedera.mirror.grpc.listener.maxPageSize`                   | 10000            | The maximum number of messages the listener can return in a single call to the database        |
| `hedera.mirror.grpc.listener.pollingFrequency`              | 1s               | How often to polling for new topic messages. Can accept duration units like `50ms`, `10s` etc. |
| `hedera.mirror.grpc.listener.type`                          | SHARED_POLL      | The type of listener to use for incoming messages. Accepts either POLL or SHARED_POLL          |
| `hedera.mirror.grpc.netty.executorCoreThreadCount`          | 10               | The number of core threads                                                                     |
| `hedera.mirror.grpc.netty.executorMaxThreadCount`           | 1000             | The maximum allowed number of threads                                                          |
| `hedera.mirror.grpc.netty.flowControlWindow`                | 64 \* 1024       | The HTTP/2 flow control window                                                                 |
| `hedera.mirror.grpc.netty.keepAliveTime`                    | 60               | The seconds limit for which threads may remain idle before being terminated                    |
| `hedera.mirror.grpc.netty.maxConcurrentCallsPerConnection`  | 5                | The maximum number of concurrent calls permitted for each incoming connection                  |
| `hedera.mirror.grpc.netty.maxInboundMessageSize`            | 6 \* 1024        | The maximum message size allowed to be received on the server                                  |
| `hedera.mirror.grpc.netty.maxInboundMetadataSize`           | 1024             | The maximum size of metadata allowed to be received                                            |
| `hedera.mirror.grpc.port`                                   | 5600             | The GRPC API port                                                                              |
| `hedera.mirror.grpc.retriever.enabled`                      | true             | Whether to retrieve historical massages or not                                                 |
| `hedera.mirror.grpc.retriever.maxPageSize`                  | 1000             | The maximum number of messages the retriever can return in a single call to the database       |
| `hedera.mirror.grpc.retriever.pollingFrequency`             | 2s               | How often to polling for historical messages. Can accept duration units like `50ms`, `10s` etc |
| `hedera.mirror.grpc.retriever.threadMultiplier`             | 4                | Multiplied by the CPU count to calculate the number of retriever threads                       |
| `hedera.mirror.grpc.retriever.timeout`                      | 60s              | How long to wait between emission of messages before returning an error                        |
| `hedera.mirror.grpc.shard`                                  | 0                | The default shard number that the GRPC component participates in                               |

## REST API

The REST API supports loading configuration from YAML or environment variables. By default it loads a file named
`application.yml` or `application.yaml` in each of the search paths (see below). The file name can be changed by setting
the `CONFIG_NAME` environment variable. A custom location can be loaded by setting the `CONFIG_PATH` environment variable.
The configuration is loaded in the following order with the latter configuration overwriting (technically recursively
merged into) the current configuration:

1. `./config/application.yml`
2. `./application.yml`
3. `${CONFIG_PATH}/application.yml`
4. Environment variables that start with `HEDERA_MIRROR_REST_` (e.g. `HEDERA_MIRROR_REST_MAXLIMIT=100`)

The following table lists the available properties along with their default values. Unless you need to set a non-default
value, it is recommended to only populate overridden properties in the custom `application.yml`.

| Name                                                 | Default                 | Description                                                                                    |
| ---------------------------------------------------- | ----------------------- | ---------------------------------------------------------------------------------------------- |
| `hedera.mirror.rest.db.host`                         | 127.0.0.1               | The IP or hostname used to connect to the database                                             |
| `hedera.mirror.rest.db.name`                         | mirror_node             | The name of the database                                                                       |
| `hedera.mirror.rest.db.password`                     | mirror_api_pass         | The database password the processor uses to connect. **Should be changed from default**        |
| `hedera.mirror.rest.db.port`                         | 5432                    | The port used to connect to the database                                                       |
| `hedera.mirror.rest.db.username`                     | mirror_api              | The username the processor uses to connect to the database                                     |
| `hedera.mirror.rest.includeHostInLink`               | false                   | Whether to include the hostname and port in the next link in the response                      |
| `hedera.mirror.rest.maxLimit`                        | 1000                    | The maximum size the limit parameter can be that controls the REST API response size           |
| `hedera.mirror.rest.log.level`                       | debug                   | The logging level. Can be trace, debug, info, warn, error or fatal.                            |
| `hedera.mirror.rest.port`                            | 5551                    | The REST API port                                                                              |
| `hedera.mirror.rest.includeHostInLink`               | false                   | Whether to include the host:port in the next links returned by the REST API                    |
| `hedera.mirror.rest.metrics.enabled`                 | true                    | Whether metrics are enabled for the REST API                                                   |
| `hedera.mirror.rest.metrics.config.authentication`   | true                    | Whether access to metrics for the REST API is authenticated                                    |
| `hedera.mirror.rest.metrics.config.username`         | mirror_api_metrics      | The REST API metrics username to access the dashboard                                          |
| `hedera.mirror.rest.metrics.config.password`         | mirror_api_metrics_pass | The REST API metrics password to access the dashboard                                          |
| `hedera.mirror.rest.metrics.config.uriPath`          | '/swagger'              | The REST API metrics uri path                                                                  |
| `hedera.mirror.rest.shard`                           | 0                       | The default shard number that this mirror node participates in                                 |
