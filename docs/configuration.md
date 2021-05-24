# Configuration

The four components of the Hedera Mirror Node (Importer, Monitor, REST API, and gRPC API) all support loading
configuration from an `application.yml` file or via the environment.

Most configuration settings have appropriate defaults and can be left unchanged. One of the important settings that
should be changed is `hedera.mirror.importer.network` as it controls which of the Hedera networks to mirror.
Additionally, the password properties have a default, but it is **strongly recommended passwords be changed from the
default**.

## Importer

The Importer component uses [Spring Boot](https://spring.io/projects/spring-boot) properties to configure the
application. As a result, property files, YAML files, environment variables and command-line arguments can all be used
to configure the application. See the Spring
Boot [documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/spring-boot-features.html#boot-features-external-config)
for the location and order it loads configuration.

The following table lists the available properties along with their default values. Unless you need to set a non-default
value, it is recommended to only populate overridden properties in the custom `application.yml`.

| Name                                                                 | Default                 | Description                                                                                    |
| -------------------------------------------------------------------- | ----------------------- | ---------------------------------------------------------------------------------------------- |
| `hedera.mirror.importer.dataPath`                                    | ./data                  | The data directory used to store downloaded files and other application state                  |
| `hedera.mirror.importer.db.restPassword`                             | mirror_api_pass         | The database password the API uses to connect.                                                 |
| `hedera.mirror.importer.db.restUsername`                             | mirror_api              | The username the API uses to connect to the database                                           |
| `hedera.mirror.importer.db.host`                                     | 127.0.0.1               | The IP or hostname used to connect to the database                                             |
| `hedera.mirror.importer.db.loadBalance`                              | true                    | Whether to enable pgpool load balancing. If false, it sends all reads to the primary db backend instead of load balancing them across the primary and replicas. |
| `hedera.mirror.importer.db.name`                                     | mirror_node             | The name of the database                                                                       |
| `hedera.mirror.importer.db.owner`                                    | mirror_node             | The username of the db user with owner permissions to create and modify the schema             |
| `hedera.mirror.importer.db.ownerPassword`                            | mirror_node_pass        | The password for the owner user the processor uses to connect.                                 |
| `hedera.mirror.importer.db.password`                                 | mirror_node_pass        | The database password for the Importer user the processor uses to connect.                     |
| `hedera.mirror.importer.db.port`                                     | 5432                    | The port used to connect to the database                                                       |
| `hedera.mirror.importer.db.schema`                                   | public                  | The name of the custom schema database objects will be created in. This is applicable from v2 of the data schema |
| `hedera.mirror.importer.db.username`                                 | mirror_node             | The Importer username the processor uses to connect to the database                            |
| `hedera.mirror.importer.downloader.accessKey`                        | ""                      | The cloud storage access key                                                                   |
| `hedera.mirror.importer.downloader.allowAnonymousAccess`             |                         | Whether the cloud storage bucket allows for anonymous access.                                  |
| `hedera.mirror.importer.downloader.balance.batchSize`                | 30                      | The number of signature files to download per node before downloading the signed files         |
| `hedera.mirror.importer.downloader.balance.enabled`                  | true                    | Whether to enable balance file downloads                                                       |
| `hedera.mirror.importer.downloader.balance.frequency`                | 30s                     | The fixed period between invocations. Can accept duration units like `10s`, `2m`, etc.          |
| `hedera.mirror.importer.downloader.balance.keepSignatures`           | false                   | Whether to keep balance signature files after successful verification. If false, files are deleted. |
| `hedera.mirror.importer.downloader.balance.prefix`                   | accountBalances/balance | The prefix to search cloud storage for balance files                                           |
| `hedera.mirror.importer.downloader.balance.threads`                  | 15                      | The number of threads to search for new files to download                                      |
| `hedera.mirror.importer.downloader.bucketName`                       |                         | The cloud storage bucket name to download streamed files. This value takes priority over network hardcoded bucket names regardless of `hedera.mirror.importer.network` value.|
| `hedera.mirror.importer.downloader.cloudProvider`                    | S3                      | The cloud provider to download files from. Either `S3` or `GCP`                                |
| `hedera.mirror.importer.downloader.consensusRatio`                   | 0.333                   | The ratio of verified nodes (nodes used to come to consensus on the signature file hash) to total number of nodes available |
| `hedera.mirror.importer.downloader.endpointOverride`                 |                         | Can be specified to download streams from a source other than S3 and GCP. Should be S3 compatible |
| `hedera.mirror.importer.downloader.event.batchSize`                  | 100                     | The number of signature files to download per node before downloading the signed files         |
| `hedera.mirror.importer.downloader.event.enabled`                    | false                   | Whether to enable event file downloads                                                         |
| `hedera.mirror.importer.downloader.event.frequency`                  | 5s                      | The fixed period between invocations. Can accept duration units like `10s`, `2m`, etc.          |
| `hedera.mirror.importer.downloader.event.keepSignatures`             | false                   | Whether to keep event signature files after successful verification. If false, files are deleted. |
| `hedera.mirror.importer.downloader.event.prefix`                     | eventsStreams/events\_  | The prefix to search cloud storage for event files                                             |
| `hedera.mirror.importer.downloader.event.threads`                    | 15                      | The number of threads to search for new files to download                                      |
| `hedera.mirror.importer.downloader.gcpProjectId`                     |                         | GCP project id to bill for requests to GCS bucket which has Requester Pays enabled.            |
| `hedera.mirror.importer.downloader.maxConcurrency`                   | 1000                    | The maximum number of allowed open HTTP connections. Used by AWS SDK directly.                 |
| `hedera.mirror.importer.downloader.record.batchSize`                 | 40                      | The number of signature files to download per node before downloading the signed files         |
| `hedera.mirror.importer.downloader.record.enabled`                   | true                    | Whether to enable record file downloads                                                        |
| `hedera.mirror.importer.downloader.record.frequency`                 | 500ms                   | The fixed period between invocations. Can accept duration units like `10s`, `2m`, etc.          |
| `hedera.mirror.importer.downloader.record.keepSignatures`            | false                   | Whether to keep record signature files after successful verification. If false, files are deleted. |
| `hedera.mirror.importer.downloader.record.prefix`                    | recordstreams/record    | The prefix to search cloud storage for record files                                            |
| `hedera.mirror.importer.downloader.record.threads`                   | 15                      | The number of threads to search for new files to download                                      |
| `hedera.mirror.importer.downloader.region`                           | us-east-1               | The region associated with the bucket                                                          |
| `hedera.mirror.importer.downloader.secretKey`                        | ""                      | The cloud storage secret key                                                                   |
| `hedera.mirror.importer.endDate`                                     | 2262-04-11T23:47:16.854775807Z | The end date (inclusive) of the data to import. Items after this date will be ignored. Format: YYYY-MM-ddTHH:mm:ss.nnnnnnnnnZ |
| `hedera.mirror.importer.importHistoricalAccountInfo`                 | true                    | Import historical account information that occurred before the last stream reset. Skipped if `startDate` is unset or after 2019-09-14T00:00:10Z. |
| `hedera.mirror.importer.initialAddressBook`                          | ""                      | The path to the bootstrap address book used to override the built-in address book              |
| `hedera.mirror.importer.network`                                     | DEMO                    | Which Hedera network to use. Can be either `DEMO`, `MAINNET`, `TESTNET`, `PREVIEWNET` or `OTHER` |
| `hedera.mirror.importer.parser.balance.batchSize`                    | 2000                    | The number of balances to insert before committing                                             |
| `hedera.mirror.importer.parser.balance.bufferSize`                   | 32768                   | The size of the byte buffer to allocate for each batch                                         |
| `hedera.mirror.importer.parser.balance.enabled`                      | true                    | Whether to enable balance file parsing                                                         |
| `hedera.mirror.importer.parser.balance.fileBufferSize`               | 200000                  | The size of the buffer to use when reading in the balance file                                 |
| `hedera.mirror.importer.parser.balance.frequency`                    | 100ms                   | How often to poll for new messages. Can accept duration units like `10s`, `2m` etc.            |
| `hedera.mirror.importer.parser.balance.keepFiles`                    | false                   | Whether to keep parsed files after successful parsing.                                         |
| `hedera.mirror.importer.parser.balance.persistBytes`                 | false                   | Whether to persist the balance file bytes to the database after successful parsing.            |
| `hedera.mirror.importer.parser.balance.queueCapacity`                | 10                      | How many balance files to queue in memory while waiting to be persisted by the parser          |
| `hedera.mirror.importer.parser.balance.retry.maxAttempts`            | 3                       | How many attempts should be made to retry file parsing errors                                  |
| `hedera.mirror.importer.parser.balance.retry.maxBackoff`             | 10s                     | The maximum amount of time to wait between retries                                             |
| `hedera.mirror.importer.parser.balance.retry.minBackoff`             | 250ms                   | The minimum amount of time to wait between retries                                             |
| `hedera.mirror.importer.parser.balance.retry.multiplier`             | 2                       | Used to generate the next delay for backoff                                                    |
| `hedera.mirror.importer.parser.event.bufferSize`                     | 32768                   | The size of the byte buffer to allocate for each batch                                         |
| `hedera.mirror.importer.parser.event.enabled`                        | false                   | Whether to enable event file parsing                                                           |
| `hedera.mirror.importer.parser.event.frequency`                      | 100ms                   | How often to poll for new messages                                                             |
| `hedera.mirror.importer.parser.event.keepFiles`                      | false                   | Whether to keep parsed files after successful parsing.                                         |
| `hedera.mirror.importer.parser.event.persistBytes`                   | false                   | Whether to persist the event file bytes to the database after successful parsing.              |
| `hedera.mirror.importer.parser.event.queueCapacity`                  | 10                      | How many event files to queue in memory while waiting to be persisted by the parser            |
| `hedera.mirror.importer.parser.event.retry.maxAttempts`              | Integer.MAX_VALUE       | How many attempts should be made to retry file parsing errors                                  |
| `hedera.mirror.importer.parser.event.retry.maxBackoff`               | 10s                     | The maximum amount of time to wait between retries                                             |
| `hedera.mirror.importer.parser.event.retry.minBackoff`               | 250ms                   | The minimum amount of time to wait between retries                                             |
| `hedera.mirror.importer.parser.event.retry.multiplier`               | 2                       | Used to generate the next delay for backoff                                                    |
| `hedera.mirror.importer.parser.exclude`                              | []                      | A list of filters that determine which transactions are ignored. Takes precedence over include |
| `hedera.mirror.importer.parser.exclude.entity`                       | []                      | A list of entity IDs to ignore in shard.realm.num (e.g. 0.0.3) format                          |
| `hedera.mirror.importer.parser.exclude.transaction`                  | []                      | A list of transaction types to ignore. See `TransactionTypeEnum.java` for possible values      |
| `hedera.mirror.importer.parser.include`                              | []                      | A list of filters that determine which transactions are stored                                 |
| `hedera.mirror.importer.parser.include.entity`                       | []                      | A list of entity IDs to store in shard.realm.num (e.g. 0.0.3) format                           |
| `hedera.mirror.importer.parser.include.transaction`                  | []                      | A list of transaction types to store. See `TransactionTypeEnum.java` for possible values       |
| `hedera.mirror.importer.parser.record.bufferSize`                    | 32768                   | The size of the byte buffer to allocate for each batch                                         |
| `hedera.mirror.importer.parser.record.enabled`                       | true                    | Whether to enable record file parsing                                                          |
| `hedera.mirror.importer.parser.record.entity.notify.enabled`                | false                   | Whether to use PostgreSQL Notify to send topic messages to the gRPC process                    |
| `hedera.mirror.importer.parser.record.entity.notify.maxJsonPayloadSize`     | 8000                    | Max number of bytes for json payload used in pg_notify of db inserts                           |
| `hedera.mirror.importer.parser.record.entity.persist.claims`                | false                   | Persist claim data to the database                                                             |
| `hedera.mirror.importer.parser.record.entity.persist.contracts`             | true                    | Persist contract data to the database                                                          |
| `hedera.mirror.importer.parser.record.entity.persist.cryptoTransferAmounts` | true                    | Persist crypto transfer amounts to the database                                                |
| `hedera.mirror.importer.parser.record.entity.persist.files`                 | true                    | Persist all file data to the database                                                          |
| `hedera.mirror.importer.parser.record.entity.persist.nonFeeTransfers`       | false                   | Persist non-fee transfers for transactions that explicitly request hbar transfers              |
| `hedera.mirror.importer.parser.record.entity.persist.schedules`             | true                    | Persist schedule transactions to the database                                                  |
| `hedera.mirror.importer.parser.record.entity.persist.systemFiles`           | true                    | Persist only system files (number lower than `1000`) to the database                           |
| `hedera.mirror.importer.parser.record.entity.persist.tokens`                | true                    | Persist token data to the database                                                  |
| `hedera.mirror.importer.parser.record.entity.persist.transactionBytes`      | false                   | Persist raw transaction bytes to the database                                                  |
| `hedera.mirror.importer.parser.record.entity.persist.transactionSignatures` | SCHEDULECREATE, SCHEDULESIGN | A list of transaction types whose transaction signatures will be stored                   |
| `hedera.mirror.importer.parser.record.entity.redis.enabled`                 | true                    | Whether to use Redis to send messages to the gRPC process. Requires `spring.redis.*` [properties](https://docs.spring.io/spring-boot/docs/current/reference/html/appendix-application-properties.html#data-properties) |
| `hedera.mirror.importer.parser.record.entity.redis.queueCapacity`           | 8                       | The size of the queue used to buffer topic messages between parser and redis publisher threads |
| `hedera.mirror.importer.parser.record.entity.repository.enabled`            | false                   | Whether to use Spring Data JPA repositories to insert into the database (experimental)         |
| `hedera.mirror.importer.parser.record.entity.sql.batchSize`                 | 20_000                  | When inserting transactions into db, executeBatches() is called every these many transactions  |
| `hedera.mirror.importer.parser.record.entity.sql.enabled`                   | true                    | Whether to use PostgreSQL Copy mechanism to insert into the database                           |
| `hedera.mirror.importer.parser.record.frequency`                            | 100ms                   | How often to poll for new messages. Can accept duration units like `10s`, `2m` etc.            |
| `hedera.mirror.importer.parser.record.keepFiles`                            | false                   | Whether to keep parsed files after successful parsing.                                         |
| `hedera.mirror.importer.parser.record.persistBytes`                         | false                   | Whether to persist the record file bytes to the database after successful parsing.             |
| `hedera.mirror.importer.parser.record.pubsub.topicName`                     |                         | Pubsub topic to publish transactions to                                                        |
| `hedera.mirror.importer.parser.record.pubsub.maxSendAttempts`               | 5                       | Number of attempts when sending messages to PubSub (only for retryable errors)                 |
| `hedera.mirror.importer.parser.record.queueCapacity`                        | 10                      | How many record files to queue in memory while waiting to be persisted by the parser           |
| `hedera.mirror.importer.parser.record.retry.maxAttempts`                    | Integer.MAX_VALUE       | How many attempts should be made to retry file parsing errors                                  |
| `hedera.mirror.importer.parser.record.retry.maxBackoff`                     | 10s                     | The maximum amount of time to wait between retries                                             |
| `hedera.mirror.importer.parser.record.retry.minBackoff`                     | 250ms                   | The minimum amount of time to wait between retries                                             |
| `hedera.mirror.importer.parser.record.retry.multiplier`                     | 2                       | Used to generate the next delay for backoff                                                    |
| `hedera.mirror.importer.topicRunningHashV2AddedTimestamp`            | Network-based  | Unix timestamp (in nanos) of first topic message with v2 as running hash version. Use this config to override the default network based value |
| `hedera.mirror.importer.shard`                                       | 0                       | The default shard number that the component participates in                                    |
| `hedera.mirror.importer.startDate`                                   |                         | The start date (inclusive) of the data to import. It takes effect 1) if it's set and the date is after the last downloaded file or the database is empty; 2) if it's not set and the database is empty, it defaults to now. Format: YYYY-MM-ddTHH:mm:ss.nnnnnnnnnZ |
| `hedera.mirror.importer.verifyHashAfter`                             | 1970-01-01T00:00:00Z  | Skip hash verification for stream files linked by hash until after (and not including) this point of time. Format: YYYY-MM-ddTHH:mm:ss.nnnnnnnnnZ |

#### Export transactions to PubSub

Importer can be configured to publish transactions (in json format) to a Pubsub topic using following properties:

- `spring.cloud.gcp.pubsub.enabled`
- `spring.cloud.gcp.pubsub.project-id`
- `hedera.mirror.importer.parser.record.pubsub.topicName`
- `spring.cloud.gcp.pubsub.credentials.*`
- `hedera.mirror.importer.parser.record.entity.enabled` (Importer can not export to both database and pubsub
  simultaneously)

See [Spring Cloud documentation](https://cloud.spring.io/spring-cloud-static/spring-cloud-gcp/1.2.2.RELEASE/reference/html/#pubsub-configuration)
for more info about `spring.cloud.gcp.*` properties.

#### Connect to S3 with the Default Credentials Provider

When connecting to an AWS S3 bucket that requires authentication (such as a requester pays bucket), you can opt to allow
the AWS Default Credentials Provider Chain to handle the authentication for you, instead of providing your static access
and secret keys in the config. This will also allow you to take advantage of alternative authorization modes such as
[AssumeRole](https://docs.aws.amazon.com/STS/latest/APIReference/API_AssumeRole.html). If the mirror node is configured
to connect to an S3 bucket that requires authenticaion, and the static credentials are not provided in the config, the
mirror node will default to using this provider. For more information and to see how you can set up your environment to
take advantage of this, see
[the AWS Credentials Documentation](https://docs.aws.amazon.com/sdk-for-java/v2/developer-guide/credentials.html)
When running in Docker or Kubernetes, credentials can be attached in a variety of ways, including by using volumes and
secrets to directly add static credentials or an existing AWS credentials file, by using other tools such as Vault or
AWS Secrets Manager, and many more.

`Docker-compose.yml`

```yaml
volumes:
  - ~/.aws/:/root/.aws:ro
```

## GRPC API

Similar to the [Importer](#importer), the gRPC API uses [Spring Boot](https://spring.io/projects/spring-boot) properties
to configure the application.

The following table lists the available properties along with their default values. Unless you need to set a non-default
value, it is recommended to only populate overridden properties in the custom `application.yml`.

| Name                                                        | Default          | Description                                                                                    |
| ------------------------------------------------------------| -----------------| -----------------------------------------------------------------------------------------------|
| `hedera.mirror.grpc.checkTopicExists`                       | true             | Whether to throw an error when the topic doesn't exist                                         |
| `hedera.mirror.grpc.db.host`                                | 127.0.0.1        | The IP or hostname used to connect to the database                                             |
| `hedera.mirror.grpc.db.name`                                | mirror_node      | The name of the database                                                                       |
| `hedera.mirror.grpc.db.password`                            | mirror_grpc_pass | The database password the GRPC API uses to connect.                                            |
| `hedera.mirror.grpc.db.port`                                | 5432             | The port used to connect to the database                                                       |
| `hedera.mirror.grpc.db.username`                            | mirror_grpc      | The username the GRPC API uses to connect to the database                                      |
| `hedera.mirror.grpc.endTimeInterval`                        | 30s              | How often we should check if a subscription has gone past the end time                         |
| `hedera.mirror.grpc.entityCacheSize`                        | 50000            | The maximum size of the cache to store entities used for existence check                       |
| `hedera.mirror.grpc.listener.enabled`                       | true             | Whether to listen for incoming massages or not                                                 |
| `hedera.mirror.grpc.listener.interval`                      | 500ms            | How often to poll or retry errors (varies by type). Can accept duration units like `50ms`, `10s`, etc. |
| `hedera.mirror.grpc.listener.maxBufferSize`                 | 16384            | The maximum number of messages the notifying listener or the shared polling listener buffers before sending an error to a client |
| `hedera.mirror.grpc.listener.maxPageSize`                   | 5000             | The maximum number of messages the listener can return in a single call to the database        |
| `hedera.mirror.grpc.listener.prefetch`                      | 48               | The prefetch queue size for shared listeners                                                   |
| `hedera.mirror.grpc.listener.type`                          | REDIS            | The type of listener to use for incoming messages. Accepts either NOTIFY, POLL, REDIS or SHARED_POLL |
| `hedera.mirror.grpc.netty.executorCoreThreadCount`          | 10               | The number of core threads                                                                     |
| `hedera.mirror.grpc.netty.executorMaxThreadCount`           | 1000             | The maximum allowed number of threads                                                          |
| `hedera.mirror.grpc.netty.maxConnectionIdle`                | 10m              | The max amount of time a connection can be idle before it will be gracefully terminated        |
| `hedera.mirror.grpc.netty.maxConcurrentCallsPerConnection`  | 5                | The maximum number of concurrent calls permitted for each incoming connection                  |
| `hedera.mirror.grpc.netty.maxInboundMessageSize`            | 1024             | The maximum message size allowed to be received on the server                                  |
| `hedera.mirror.grpc.netty.maxInboundMetadataSize`           | 1024             | The maximum size of metadata allowed to be received                                            |
| `hedera.mirror.grpc.netty.threadKeepAliveTime`              | 1m               | The amount of time for which threads may remain idle before being terminated                   |
| `hedera.mirror.grpc.port`                                   | 5600             | The GRPC API port                                                                              |
| `hedera.mirror.grpc.retriever.enabled`                      | true             | Whether to retrieve historical massages or not                                                 |
| `hedera.mirror.grpc.retriever.maxPageSize`                  | 1000             | The maximum number of messages the retriever can return in a single call to the database       |
| `hedera.mirror.grpc.retriever.pollingFrequency`             | 2s               | How often to poll for historical messages. Can accept duration units like `50ms`, `10s` etc    |
| `hedera.mirror.grpc.retriever.threadMultiplier`             | 4                | Multiplied by the CPU count to calculate the number of retriever threads                       |
| `hedera.mirror.grpc.retriever.timeout`                      | 60s              | How long to wait between emission of messages before returning an error                        |
| `hedera.mirror.grpc.retriever.unthrottled.maxPageSize`      | 5000             | The maximum number of messages the retriever can return in a single call to the database when unthrottled |
| `hedera.mirror.grpc.retriever.unthrottled.maxPolls`         | 12               | The max number of polls when unthrottled                                                       |
| `hedera.mirror.grpc.retriever.unthrottled.pollingFrequency` | 20ms             | How often to poll for messages when unthrottled. Can accept duration units like `50ms`, `10s` etc |
| `hedera.mirror.grpc.shard`                                  | 0                | The default shard number that the GRPC component participates in                               |

## Monitor

Similar to the [Importer](#importer), the monitor uses [Spring Boot](https://spring.io/projects/spring-boot) properties
to configure the application.

The following table lists the available properties along with their default values. Unless you need to set a non-default
value, it is recommended to only populate overridden properties in the custom `application.yml`.

See the monitor [documentation](monitor.md) for more general information about configuring and using the monitor.

Name                                                        | Default    | Description
------------------------------------------------------------| -----------| ---------------------------------------
`hedera.mirror.monitor.mirrorNode.grpc.host`                | ""         | The hostname of the mirror node's gRPC API
`hedera.mirror.monitor.mirrorNode.grpc.port`                | 5600       | The port of the mirror node's gRPC API
`hedera.mirror.monitor.mirrorNode.rest.host`                | ""         | The hostname of the mirror node's REST API
`hedera.mirror.monitor.mirrorNode.rest.port`                | 443        | The port of the mirror node's REST API
`hedera.mirror.monitor.network`                             | TESTNET    | Which network to connect to. Automatically populates the main node & mirror node endpoints. Can be `MAINNET`, `PREVIEWNET`, `TESTNET` or `OTHER`
`hedera.mirror.monitor.nodes[].accountId`                   | ""         | The main node's account ID
`hedera.mirror.monitor.nodes[].host`                        | ""         | The main node's hostname
`hedera.mirror.monitor.nodes[].port`                        | 50211      | The main node's port
`hedera.mirror.monitor.operator.accountId`                  | ""         | Operator account ID used to pay for transactions
`hedera.mirror.monitor.operator.privateKey`                 | ""         | Operator ED25519 private key used to sign transactions in hex encoded DER format
`hedera.mirror.monitor.publish.batchDivisor`                | 100        | The divisor used to calculate batch size when generating transactions
`hedera.mirror.monitor.publish.clients`                     | 4          | How many total SDK clients to publish transactions. Clients will be used in a round-robin fashion
`hedera.mirror.monitor.publish.enabled`                     | true       | Whether to enable transaction publishing
`hedera.mirror.monitor.publish.scenarios[].duration`        |            | How long this scenario should publish transactions. Leave empty for infinite
`hedera.mirror.monitor.publish.scenarios[].enabled`         | true       | Whether this publish scenario is enabled
`hedera.mirror.monitor.publish.scenarios[].limit`           | 0          | How many transactions to publish before halting. 0 for unlimited
`hedera.mirror.monitor.publish.scenarios[].logResponse`     | false      | Whether to log the response from HAPI
`hedera.mirror.monitor.publish.scenarios[].name`            | ""         | The publish scenario name. Used to tag logs and metrics
`hedera.mirror.monitor.publish.scenarios[].maxAttempts`     | 1          | The maximum number of times a scenario transaction will be attempted
`hedera.mirror.monitor.publish.scenarios[].properties`      | {}         | Key/value pairs used to configure the [`TransactionSupplier`](/hedera-mirror-datagenerator/src/main/java/com/hedera/datagenerator/sdk/supplier) associated with this scenario type
`hedera.mirror.monitor.publish.scenarios[].receiptPercent`  | 0.0        | The percentage of receipts to retrieve from HAPI. Accepts values between 0-1
`hedera.mirror.monitor.publish.scenarios[].recordPercent`   | 0.0        | The percentage of records to retrieve from HAPI. Accepts values between 0-1
`hedera.mirror.monitor.publish.scenarios[].timeout`         | 10s        | How long to wait for the transaction result
`hedera.mirror.monitor.publish.scenarios[].tps`             | 1.0        | The rate at which transactions will publish
`hedera.mirror.monitor.publish.scenarios[].type`            |            | The type of transaction to publish. See the [`TransactionType`](/hedera-mirror-datagenerator/src/main/java/com/hedera/datagenerator/sdk/supplier/TransactionType.java) enum for a list of possible values
`hedera.mirror.monitor.publish.statusFrequency`             | 10s        | How often to log publishing statistics
`hedera.mirror.monitor.publish.threads`                     | 5          | How many threads to use to resolve the asynchronous responses
`hedera.mirror.monitor.publish.warmupPeriod`                | 30s        | The amount of time the publisher should ramp up its rate before reaching its stable (maximum) rate
`hedera.mirror.monitor.subscribe.clients`                   | 1          | How many SDK clients should be created to subscribe to mirror node APIs. Clients will be used in a round-robin fashion
`hedera.mirror.monitor.subscribe.enabled`                   | true       | Whether to enable subscribing to mirror node APIs to verify published transactions
`hedera.mirror.monitor.subscribe.grpc[].duration`           |            | How long to stay subscribed to the gRPC API
`hedera.mirror.monitor.subscribe.grpc[].enabled`            | true       | Whether this subscribe scenario is enabled
`hedera.mirror.monitor.subscribe.grpc[].limit`              | 0          | How many transactions to receive before halting. 0 for unlimited
`hedera.mirror.monitor.subscribe.grpc[].name`               | ""         | The subscribe scenario name. Used to tag logs and metrics
`hedera.mirror.monitor.subscribe.grpc[].retry.maxAttempts`  | 16         | How many consecutive retry attempts before giving up connecting to the mirror gRPC API
`hedera.mirror.monitor.subscribe.grpc[].retry.maxBackoff`   | 8s         | The maximum amount of time to wait between retry attempts
`hedera.mirror.monitor.subscribe.grpc[].retry.minBackoff`   | 250ms      | The initial amount of time to wait between retry attempts
`hedera.mirror.monitor.subscribe.grpc[].startTime`          |            | The start time passed to the gRPC API. Defaults to current time if not set
`hedera.mirror.monitor.subscribe.grpc[].subscribers`        | 1          | How many concurrent subscribers should be instantiated for this scenario
`hedera.mirror.monitor.subscribe.grpc[].topicId`            |            | Which topic to subscribe to
`hedera.mirror.monitor.subscribe.rest[].duration`           |            | How long to stay subscribed to the gRPC API
`hedera.mirror.monitor.subscribe.rest[].enabled`            | true       | Whether this subscribe scenario is enabled
`hedera.mirror.monitor.subscribe.rest[].limit`              | 0          | How many transactions to receive before halting. 0 for unlimited
`hedera.mirror.monitor.subscribe.rest[].name`               | ""         | The subscribe scenario name. Used to tag logs and metrics
`hedera.mirror.monitor.subscribe.rest[].retry.maxAttempts`  | 16         | How many consecutive retry attempts before giving up connecting to the mirror gRPC API
`hedera.mirror.monitor.subscribe.rest[].retry.maxBackoff`   | 8s         | The maximum amount of time to wait between retry attempts
`hedera.mirror.monitor.subscribe.rest[].retry.minBackoff`   | 250ms      | The initial amount of time to wait between retry attempts
`hedera.mirror.monitor.subscribe.rest[].samplePercent`      | 1.0        | The percentage of transactions to verify against the REST API. Accepts values between 0-1
`hedera.mirror.monitor.subscribe.rest[].statusFrequency`    | 10s        | How often to log subscription statistics
`hedera.mirror.monitor.subscribe.rest[].timeout`            | 2s         | Maximum amount of time to wait for a REST API call to retrieve data
`hedera.mirror.monitor.subscribe.statusFrequency`           | 10s        | How often to log subscription statistics
`hedera.mirror.monitor.validateNodes`                       | true       | Whether to validate and remove invalid or down nodes permanently before publishing

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

| Name                                                     | Default                 | Description                                                                                    |
| -------------------------------------------------------- | ----------------------- | ---------------------------------------------------------------------------------------------- |
| `hedera.mirror.rest.db.host`                             | 127.0.0.1               | The IP or hostname used to connect to the database                                             |
| `hedera.mirror.rest.db.name`                             | mirror_node             | The name of the database                                                                       |
| `hedera.mirror.rest.db.password`                         | mirror_api_pass         | The database password the processor uses to connect.                                           |
| `hedera.mirror.rest.db.pool.connectionTimeout`           | 20000                   | The number of milliseconds to wait before timing out when connecting a new database client     |
| `hedera.mirror.rest.db.pool.maxConnections`              | 10                      | The maximum number of clients the database pool can contain                                    |
| `hedera.mirror.rest.db.pool.statementTimeout`            | 20000                   | The number of milliseconds to wait before timing out a query statement                         |
| `hedera.mirror.rest.db.port`                             | 5432                    | The port used to connect to the database                                                       |
| `hedera.mirror.rest.db.username`                         | mirror_api              | The username the processor uses to connect to the database                                     |
| `hedera.mirror.rest.includeHostInLink`                   | false                   | Whether to include the hostname and port in the next link in the response                      |
| `hedera.mirror.rest.maxLimit`                            | 1000                    | The maximum size the limit parameter can be that controls the REST API response size           |
| `hedera.mirror.rest.maxRepeatedQueryParameters`          | 100                     | The maximum number of times any query parameter can be repeated in the uri                     |
| `hedera.mirror.rest.log.level`                           | debug                   | The logging level. Can be trace, debug, info, warn, error or fatal.                            |
| `hedera.mirror.rest.port`                                | 5551                    | The REST API port                                                                              |
| `hedera.mirror.rest.metrics.enabled`                     | true                    | Whether metrics are enabled for the REST API                                                   |
| `hedera.mirror.rest.metrics.config.authentication`       | true                    | Whether access to metrics for the REST API is authenticated                                    |
| `hedera.mirror.rest.metrics.config.username`             | mirror_api_metrics      | The REST API metrics username to access the dashboard                                          |
| `hedera.mirror.rest.metrics.config.password`             | mirror_api_metrics_pass | The REST API metrics password to access the dashboard                                          |
| `hedera.mirror.rest.metrics.config.uriPath`              | '/swagger'              | The REST API metrics uri path                                                                  |
| `hedera.mirror.rest.openapi.specFileName`                | 'openapi'               | The file name of the OpenAPI spec file                                                         |
| `hedera.mirror.rest.openapi.swaggerUIPath`               | '/docs'                 | Swagger UI path for your REST API                                                              |
| `hedera.mirror.rest.shard`                               | 0                       | The default shard number that this mirror node participates in                                 |
| `hedera.mirror.rest.stateproof.enabled`                  | false                   | Whether to enable stateproof REST API or not                                                   |
| `hedera.mirror.rest.stateproof.streams.accessKey`        | ""                      | The cloud storage access key                                                                   |
| `hedera.mirror.rest.stateproof.streams.bucketName`       |                         | The cloud storage bucket name to download streamed files. This value takes priority over network hardcoded bucket names regardless of `hedera.mirror.rest.stateproof.streams.network` |
| `hedera.mirror.rest.stateproof.streams.cloudProvider`    | S3                      | The cloud provider to download files from. Either `S3` or `GCP`                                |
| `hedera.mirror.rest.stateproof.streams.endpointOverride` |                         | Can be specified to download streams from a source other than S3 and GCP. Should be S3 compatible |
| `hedera.mirror.rest.stateproof.streams.gcpProjectId`     |                         | GCP project id to bill for requests to GCS bucket which has Requester Pays enabled.            |
| `hedera.mirror.rest.stateproof.streams.network`          | DEMO                    | Which Hedera network to use. Can be either `DEMO`, `MAINNET`, `TESTNET`, `PREVIEWNET` or `OTHER` |
| `hedera.mirror.rest.stateproof.streams.region`           | us-east-1               | The region associated with the bucket                                                          |
| `hedera.mirror.rest.stateproof.streams.secretKey`        | ""                      | The cloud storage secret key                                                                   |
| `hedera.mirror.rest.shutdown.timeout`                    | 20000                   | The amount of time (in ms) to give the process to gracefully shut down                         |

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

1. `./config/application.yml`
2. `./application.yml`
3. `${HEDERA_MIRROR_ROSETTA_API_CONFIG}` environment variable to custom values file (
   e.g. `HEDERA_MIRROR_ROSETTA_API_CONFIG=/Users/Downloads/hedera-mirror-rosetta/application.yml`)
4. Environment variables that start with `HEDERA_MIRROR_ROSETTA_` (e.g. `HEDERA_MIRROR_ROSETTA_API_VERSION=1.4.2`)

The following table lists the available properties along with their default values.

Name                                                    | Default                 | Description
------------------------------------------------------- | ----------------------- | ----------------------------------------------------------------------------------------------
`hedera.mirror.rosetta.apiVersion`                      | 1.4.10                  | The version of the Rosetta interface the implementation adheres to
`hedera.mirror.rosetta.db.host`                         | 127.0.0.1               | The IP or hostname used to connect to the database
`hedera.mirror.rosetta.db.name`                         | mirror_node             | The name of the database
`hedera.mirror.rosetta.db.password`                     | mirror_rosetta_pass     | The database password the processor uses to connect
`hedera.mirror.rosetta.db.pool.maxIdleConnections`      | 20                      | The maximum number of idle database connections
`hedera.mirror.rosetta.db.pool.maxLifetime`             | 30                      | The maximum lifetime of a database connection in minutes
`hedera.mirror.rosetta.db.pool.maxOpenConnections`      | 100                     | The maximum number of open database connections
`hedera.mirror.rosetta.db.port`                         | 5432                    | The port used to connect to the database
`hedera.mirror.rosetta.db.username`                     | mirror_rosetta          | The username the processor uses to connect to the database
`hedera.mirror.rosetta.log.level`                       | info                    | The log level
`hedera.mirror.rosetta.network`                         | DEMO                    | Which Hedera network to use. Can be either `DEMO`, `MAINNET`, `PREVIEWNET`, `TESTNET` or `OTHER`
`hedera.mirror.rosetta.nodeVersion`                     | 0                       | The default canonical version of the node runtime
`hedera.mirror.rosetta.online`                          | true                    | The default online mode of the Rosetta interface
`hedera.mirror.rosetta.port`                            | 5700                    | The REST API port
`hedera.mirror.rosetta.shard`                           | 0                       | The default shard number that this mirror node participates in
`hedera.mirror.rosetta.realm`                           | 0                       | The default realm number within the shard
`hedera.mirror.rosetta.version`                         | Varies per release      | The version of the Hedera Mirror Node used to adhere to the Rosetta interface
