# Configuration

The components of the Hedera Mirror Node all support loading configuration from an `application.yml` file or via the
environment.

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

The Importer component uses [Spring Boot](https://spring.io/projects/spring-boot) properties to configure the
application. As a result, property files, YAML files, environment variables and command-line arguments can all be used
to configure the application. See the Spring
Boot [documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/spring-boot-features.html#boot-features-external-config)
for the location and order it loads configuration.

The following table lists the available properties along with their default values. Unless you need to set a non-default
value, it is recommended to only populate overridden properties in the custom `application.yml`.

| Name                                                                        | Default                        | Description                                                                                                                                                                                                                                                        |
|-----------------------------------------------------------------------------|--------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `hedera.mirror.importer.dataPath`                                           | ./data                         | The data directory used to store downloaded files and other application state                                                                                                                                                                                      |
| `hedera.mirror.importer.db.restPassword`                                    | mirror_api_pass                | The database password the API uses to connect.                                                                                                                                                                                                                     |
| `hedera.mirror.importer.db.restUsername`                                    | mirror_api                     | The username the API uses to connect to the database                                                                                                                                                                                                               |
| `hedera.mirror.importer.db.host`                                            | 127.0.0.1                      | The IP or hostname used to connect to the database                                                                                                                                                                                                                 |
| `hedera.mirror.importer.db.loadBalance`                                     | true                           | Whether to enable pgpool load balancing. If false, it sends all reads to the primary db backend instead of load balancing them across the primary and replicas.                                                                                                    |
| `hedera.mirror.importer.db.name`                                            | mirror_node                    | The name of the database                                                                                                                                                                                                                                           |
| `hedera.mirror.importer.db.owner`                                           | mirror_node                    | The username of the db user with owner permissions to create and modify the schema                                                                                                                                                                                 |
| `hedera.mirror.importer.db.ownerPassword`                                   | mirror_node_pass               | The password for the owner user the processor uses to connect.                                                                                                                                                                                                     |
| `hedera.mirror.importer.db.password`                                        | mirror_node_pass               | The database password for the Importer user the processor uses to connect.                                                                                                                                                                                         |
| `hedera.mirror.importer.db.port`                                            | 5432                           | The port used to connect to the database                                                                                                                                                                                                                           |
| `hedera.mirror.importer.db.schema`                                          | public                         | The name of the custom schema database objects will be created in. This is applicable from v2 of the data schema                                                                                                                                                   |
| `hedera.mirror.importer.db.username`                                        | mirror_node                    | The Importer username the processor uses to connect to the database                                                                                                                                                                                                |
| `hedera.mirror.importer.downloader.accessKey`                               | ""                             | The cloud storage access key                                                                                                                                                                                                                                       |
| `hedera.mirror.importer.downloader.allowAnonymousAccess`                    |                                | Whether the cloud storage bucket allows for anonymous access.                                                                                                                                                                                                      |
| `hedera.mirror.importer.downloader.balance.enabled`                         | true                           | Whether to enable balance file downloads                                                                                                                                                                                                                           |
| `hedera.mirror.importer.downloader.balance.frequency`                       | 30s                            | The fixed period between invocations. Can accept duration units like `10s`, `2m`, etc.                                                                                                                                                                             |
| `hedera.mirror.importer.downloader.balance.persistBytes`                    | false                          | Whether to persist the balance file bytes to the database.                                                                                                                                                                                                         |
| `hedera.mirror.importer.downloader.balance.writeFiles`                      | false                          | Whether to write verified stream files to the filesystem.                                                                                                                                                                                                          |
| `hedera.mirror.importer.downloader.balance.writeSignatures`                 | false                          | Whether to write verified signature files to the filesystem.                                                                                                                                                                                                       |
| `hedera.mirror.importer.downloader.batchSize`                               | 100                            | The number of signature files to download per node before downloading the signed files                                                                                                                                                                             |
| `hedera.mirror.importer.downloader.bucketName`                              |                                | The cloud storage bucket name to download streamed files. This value takes priority over network hardcoded bucket names regardless of `hedera.mirror.importer.network` value.                                                                                      |
| `hedera.mirror.importer.downloader.cloudProvider`                           | S3                             | The cloud provider to download files from. Either `GCP`, `LOCAL`, or `S3`.                                                                                                                                                                                         |
| `hedera.mirror.importer.downloader.consensusRatio`                          | 0.33333333333                  | The ratio of verified nodes (nodes used to come to consensus on the signature file hash) to total number of nodes available                                                                                                                                        |
| `hedera.mirror.importer.downloader.endpointOverride`                        |                                | Can be specified to download streams from a source other than S3 and GCP. Should be S3 compatible                                                                                                                                                                  |
| `hedera.mirror.importer.downloader.event.enabled`                           | false                          | Whether to enable event file downloads                                                                                                                                                                                                                             |
| `hedera.mirror.importer.downloader.event.frequency`                         | 5s                             | The fixed period between invocations. Can accept duration units like `10s`, `2m`, etc.                                                                                                                                                                             |
| `hedera.mirror.importer.downloader.event.persistBytes`                      | false                          | Whether to persist the event file bytes to the database.                                                                                                                                                                                                           |
| `hedera.mirror.importer.downloader.event.writeFiles`                        | false                          | Whether to write verified stream files to the filesystem.                                                                                                                                                                                                          |
| `hedera.mirror.importer.downloader.event.writeSignatures`                   | false                          | Whether to write verified signature files to the filesystem.                                                                                                                                                                                                       |
| `hedera.mirror.importer.downloader.gcpProjectId`                            |                                | GCP project id to bill for requests to GCS bucket which has Requester Pays enabled.                                                                                                                                                                                |
| `hedera.mirror.importer.downloader.record.enabled`                          | true                           | Whether to enable record file downloads                                                                                                                                                                                                                            |
| `hedera.mirror.importer.downloader.record.frequency`                        | 500ms                          | The fixed period between invocations. Can accept duration units like `10s`, `2m`, etc.                                                                                                                                                                             |
| `hedera.mirror.importer.downloader.record.persistBytes`                     | false                          | Whether to persist the record file bytes to the database.                                                                                                                                                                                                          |
| `hedera.mirror.importer.downloader.record.writeFiles`                       | false                          | Whether to write verified stream files to the filesystem.                                                                                                                                                                                                          |
| `hedera.mirror.importer.downloader.record.writeSignatures`                  | false                          | Whether to write verified signature files to the filesystem.                                                                                                                                                                                                       |
| `hedera.mirror.importer.downloader.region`                                  | us-east-1                      | The region associated with the bucket                                                                                                                                                                                                                              |
| `hedera.mirror.importer.downloader.secretKey`                               | ""                             | The cloud storage secret key                                                                                                                                                                                                                                       |
| `hedera.mirror.importer.downloader.sources`                                 | []                             | A list of download sources to use for stream files. The grandfathered `hedera.mirror.importer.downloader` will also be utilized as the first source in the list.                                                                                                   |
| `hedera.mirror.importer.downloader.sources.backoff`                         | 60s                            | The amount of time to wait before retrying a source after an exception                                                                                                                                                                                             |
| `hedera.mirror.importer.downloader.sources.connectionTimeout`               | 5s                             | The amount of time to wait for a connection before throwing an exception                                                                                                                                                                                           |
| `hedera.mirror.importer.downloader.sources.credentials.accessKey`           |                                | The cloud storage access key for the given source                                                                                                                                                                                                                  |
| `hedera.mirror.importer.downloader.sources.credentials.secretKey`           |                                | The cloud storage secret key for the given source                                                                                                                                                                                                                  |
| `hedera.mirror.importer.downloader.sources.maxConcurrency`                  | 1000                           | The maximum number of allowed open HTTP connections. Used by AWS SDK directly.                                                                                                                                                                                     |
| `hedera.mirror.importer.downloader.sources.projectId`                       |                                | The cloud project ID to bill for requests to the bucket which has requester pays enabled.                                                                                                                                                                          |
| `hedera.mirror.importer.downloader.region`                                  | us-east-1                      | The region associated with the bucket                                                                                                                                                                                                                              |
| `hedera.mirror.importer.downloader.sources.type`                            |                                | The source type to download files from. Either `GCP`, `LOCAL`, or `S3`.                                                                                                                                                                                            |
| `hedera.mirror.importer.downloader.sources.uri`                             |                                | The endpoint override URI to use as an alternate for the default URI provided by the source type.                                                                                                                                                                  |
| `hedera.mirror.importer.downloader.threads`                                 | 30                             | The number of threads to search for new files to download                                                                                                                                                                                                          |
| `hedera.mirror.importer.downloader.timeout`                                 | 30s                            | The amount of time to wait for a download before throwing an exception                                                                                                                                                                                             |
| `hedera.mirror.importer.endDate`                                            | 2262-04-11T23:47:16.854775807Z | The end date (inclusive) of the data to import. Items after this date will be ignored. Format: YYYY-MM-ddTHH:mm:ss.nnnnnnnnnZ                                                                                                                                      |
| `hedera.mirror.importer.importHistoricalAccountInfo`                        | true                           | Import historical account information that occurred before the last stream reset. Skipped if `startDate` is unset or after 2019-09-14T00:00:10Z.                                                                                                                   |
| `hedera.mirror.importer.initialAddressBook`                                 | ""                             | The path to the bootstrap address book used to override the built-in address book                                                                                                                                                                                  |
| `hedera.mirror.importer.leaderElection`                                     | false                          | Whether leader election should be used in Kubernetes environments to ensure only one replica processes data at a time                                                                                                                                              |
| `hedera.mirror.importer.migration.<migrationName>.checksum`                 | 1                              | The checksum of the repeatable migration. Change it to a different value to re-run the migration                                                                                                                                                                   |
| `hedera.mirror.importer.migration.<migrationName>.enabled`                  | true                           | Whether to enable the repeatable migration                                                                                                                                                                                                                         |
| `hedera.mirror.importer.network`                                            | DEMO                           | Which Hedera network to use. Can be either `DEMO`, `MAINNET`, `TESTNET`, `PREVIEWNET` or `OTHER`                                                                                                                                                                   |
| `hedera.mirror.importer.parser.balance.batchSize`                           | 200000                         | The number of balances to store in memory before saving to the database                                                                                                                                                                                            |
| `hedera.mirror.importer.parser.balance.enabled`                             | true                           | Whether to enable balance file parsing                                                                                                                                                                                                                             |
| `hedera.mirror.importer.parser.balance.fileBufferSize`                      | 200000                         | The size of the buffer to use when reading in the balance file                                                                                                                                                                                                     |
| `hedera.mirror.importer.parser.balance.frequency`                           | 100ms                          | How often to poll for new messages. Can accept duration units like `10s`, `2m` etc.                                                                                                                                                                                |
| `hedera.mirror.importer.parser.balance.processingTimeout`                   | 10s                            | The additional timeout to allow after the last balance stream file health check to verify that files are still being processed.                                                                                                                                    |
| `hedera.mirror.importer.parser.balance.queueCapacity`                       | 0                              | How many balance files to queue in memory while waiting to be persisted by the parser                                                                                                                                                                              |
| `hedera.mirror.importer.parser.balance.retry.maxAttempts`                   | 3                              | How many attempts should be made to retry file parsing errors                                                                                                                                                                                                      |
| `hedera.mirror.importer.parser.balance.retry.maxBackoff`                    | 10s                            | The maximum amount of time to wait between retries                                                                                                                                                                                                                 |
| `hedera.mirror.importer.parser.balance.retry.minBackoff`                    | 250ms                          | The minimum amount of time to wait between retries                                                                                                                                                                                                                 |
| `hedera.mirror.importer.parser.balance.retry.multiplier`                    | 2                              | Used to generate the next delay for backoff                                                                                                                                                                                                                        |
| `hedera.mirror.importer.parser.balance.transactionTimeout`                  | 300s                           | The timeout in seconds for a database transaction                                                                                                                                                                                                                  |
| `hedera.mirror.importer.parser.bufferSize`                                  | 32768                          | The size of the byte buffer to allocate for each batch                                                                                                                                                                                                             |
| `hedera.mirror.importer.parser.event.enabled`                               | false                          | Whether to enable event file parsing                                                                                                                                                                                                                               |
| `hedera.mirror.importer.parser.event.frequency`                             | 100ms                          | How often to poll for new messages                                                                                                                                                                                                                                 |
| `hedera.mirror.importer.parser.event.processingTimeout`                     | 10s                            | The additional timeout to allow after the last event stream file health check to verify that files are still being processed.                                                                                                                                      |
| `hedera.mirror.importer.parser.event.queueCapacity`                         | 10                             | How many event files to queue in memory while waiting to be persisted by the parser                                                                                                                                                                                |
| `hedera.mirror.importer.parser.event.retry.maxAttempts`                     | Integer.MAX_VALUE              | How many attempts should be made to retry file parsing errors                                                                                                                                                                                                      |
| `hedera.mirror.importer.parser.event.retry.maxBackoff`                      | 10s                            | The maximum amount of time to wait between retries                                                                                                                                                                                                                 |
| `hedera.mirror.importer.parser.event.retry.minBackoff`                      | 250ms                          | The minimum amount of time to wait between retries                                                                                                                                                                                                                 |
| `hedera.mirror.importer.parser.event.retry.multiplier`                      | 2                              | Used to generate the next delay for backoff                                                                                                                                                                                                                        |
| `hedera.mirror.importer.parser.event.transactionTimeout`                    | 30s                            | The timeout in seconds for a database transaction                                                                                                                                                                                                                  |
| `hedera.mirror.importer.parser.exclude`                                     | []                             | A list of filters that determine which transactions are ignored. Takes precedence over include                                                                                                                                                                     |
| `hedera.mirror.importer.parser.exclude.entity`                              | []                             | A list of entity IDs to ignore in shard.realm.num (e.g. 0.0.3) format                                                                                                                                                                                              |
| `hedera.mirror.importer.parser.exclude.transaction`                         | []                             | A list of transaction types to ignore. See `TransactionType.java` for possible values                                                                                                                                                                              |
| `hedera.mirror.importer.parser.include`                                     | []                             | A list of filters that determine which transactions are stored                                                                                                                                                                                                     |
| `hedera.mirror.importer.parser.include.entity`                              | []                             | A list of entity IDs to store in shard.realm.num (e.g. 0.0.3) format                                                                                                                                                                                               |
| `hedera.mirror.importer.parser.include.transaction`                         | []                             | A list of transaction types to store. See `TransactionType.java` for possible values                                                                                                                                                                               |
| `hedera.mirror.importer.parser.record.enabled`                              | true                           | Whether to enable record file parsing                                                                                                                                                                                                                              |
| `hedera.mirror.importer.parser.record.entity.notify.enabled`                | false                          | Whether to use PostgreSQL Notify to send topic messages to the gRPC process                                                                                                                                                                                        |
| `hedera.mirror.importer.parser.record.entity.notify.maxJsonPayloadSize`     | 8000                           | Max number of bytes for json payload used in pg_notify of db inserts                                                                                                                                                                                               |
| `hedera.mirror.importer.parser.record.entity.persist.claims`                | false                          | Persist claim data to the database                                                                                                                                                                                                                                 |
| `hedera.mirror.importer.parser.record.entity.persist.contracts`             | true                           | Persist contract data to the database                                                                                                                                                                                                                              |
| `hedera.mirror.importer.parser.record.entity.persist.contractResults`       | true                           | Persist contract results data to the database                                                                                                                                                                                                                      |
| `hedera.mirror.importer.parser.record.entity.persist.cryptoTransferAmounts` | true                           | Persist crypto transfer amounts to the database                                                                                                                                                                                                                    |
| `hedera.mirror.importer.parser.record.entity.persist.ethereumTransactions`  | true                           | Persist all ethereum transactions data to the database                                                                                                                                                                                                             |
| `hedera.mirror.importer.parser.record.entity.persist.files`                 | true                           | Persist all file data to the database                                                                                                                                                                                                                              |
| `hedera.mirror.importer.parser.record.entity.persist.nonFeeTransfers`       | false                          | Persist non-fee transfers for transactions that explicitly request hbar transfers                                                                                                                                                                                  |
| `hedera.mirror.importer.parser.record.entity.persist.pendingReward`         | true                           | Calculate pending reward and update entity stake state                                                                                                                                                                                                             |
| `hedera.mirror.importer.parser.record.entity.persist.schedules`             | true                           | Persist schedule transactions to the database                                                                                                                                                                                                                      |
| `hedera.mirror.importer.parser.record.entity.persist.systemFiles`           | true                           | Persist only system files (number lower than `1000`) to the database                                                                                                                                                                                               |
| `hedera.mirror.importer.parser.record.entity.persist.tokens`                | true                           | Persist token data to the database                                                                                                                                                                                                                                 |
| `hedera.mirror.importer.parser.record.entity.persist.topics`                | true                           | Persist topic messages to the database                                                                                                                                                                                                                             |
| `hedera.mirror.importer.parser.record.entity.persist.trackBalance`          | true                           | Track entity balance changes and persist to the database                                                                                                                                                                                                           |
| `hedera.mirror.importer.parser.record.entity.persist.transactionBytes`      | false                          | Persist raw transaction bytes to the database                                                                                                                                                                                                                      |
| `hedera.mirror.importer.parser.record.entity.persist.transactionSignatures` | SCHEDULECREATE, SCHEDULESIGN   | A list of transaction types whose transaction signatures will be stored                                                                                                                                                                                            |
| `hedera.mirror.importer.parser.record.entity.redis.enabled`                 | true                           | Whether to use Redis to send messages to the gRPC process. Requires `spring.redis.*` [properties](https://docs.spring.io/spring-boot/docs/current/reference/html/appendix-application-properties.html#data-properties)                                             |
| `hedera.mirror.importer.parser.record.entity.redis.queueCapacity`           | 8                              | The size of the queue used to buffer topic messages between parser and redis publisher threads                                                                                                                                                                     |
| `hedera.mirror.importer.parser.record.entity.sql.batchSize`                 | 20_000                         | When inserting transactions into db, executeBatches() is called every these many transactions                                                                                                                                                                      |
| `hedera.mirror.importer.parser.record.entity.sql.enabled`                   | true                           | Whether to use PostgreSQL Copy mechanism to insert into the database                                                                                                                                                                                               |
| `hedera.mirror.importer.parser.record.frequency`                            | 100ms                          | How often to poll for new messages. Can accept duration units like `10s`, `2m` etc.                                                                                                                                                                                |
| `hedera.mirror.importer.parser.record.partialDataAction`                    | ERROR                          | The action for partial data. Where `DEFAULT` will replace the unknown values with defaults appropriate for its data type, `ERROR` will throw exception, and `SKIP` will omit the data if possible or the entire transaction.                                       |
| `hedera.mirror.importer.parser.record.processingTimeout`                    | 10s                            | The additional timeout to allow after the last record stream file health check to verify that files are still being processed.                                                                                                                                     |
| `hedera.mirror.importer.parser.record.pubsub.topicName`                     |                                | Pubsub topic to publish transactions to                                                                                                                                                                                                                            |
| `hedera.mirror.importer.parser.record.pubsub.maxSendAttempts`               | 5                              | Number of attempts when sending messages to PubSub (only for retryable errors)                                                                                                                                                                                     |
| `hedera.mirror.importer.parser.record.queueCapacity`                        | 10                             | How many record files to queue in memory while waiting to be persisted by the parser                                                                                                                                                                               |
| `hedera.mirror.importer.parser.record.retry.maxAttempts`                    | Integer.MAX_VALUE              | How many attempts should be made to retry file parsing errors                                                                                                                                                                                                      |
| `hedera.mirror.importer.parser.record.retry.maxBackoff`                     | 10s                            | The maximum amount of time to wait between retries                                                                                                                                                                                                                 |
| `hedera.mirror.importer.parser.record.retry.minBackoff`                     | 250ms                          | The minimum amount of time to wait between retries                                                                                                                                                                                                                 |
| `hedera.mirror.importer.parser.record.retry.multiplier`                     | 2                              | Used to generate the next delay for backoff                                                                                                                                                                                                                        |
| `hedera.mirror.importer.parser.record.sidecar.enabled`                      | false                          | Whether to download and read sidecar record files                                                                                                                                                                                                                  |
| `hedera.mirror.importer.parser.record.sidecar.persistBytes`                 | false                          | Whether to persist the sidecar file bytes to the database                                                                                                                                                                                                          |
| `hedera.mirror.importer.parser.record.sidecar.types`                        | []                             | Which types of transaction sidecar records to process. By default it is empty to indicate all types                                                                                                                                                                |
| `hedera.mirror.importer.parser.record.transactionTimeout`                   | 30s                            | The timeout in seconds for a database transaction                                                                                                                                                                                                                  |
| `hedera.mirror.importer.parser.tempTableBufferSize`                         | 256                            | The size of the buffer in MB to use for temporary tables                                                                                                                                                                                                           |
| `hedera.mirror.importer.reconciliation.cron`                                | 0 0 0 * * *                    | When to run the balance reconciliation job. Defaults to once a day at midnight. See Spring [docs](https://docs.spring.io/spring-framework/docs/current/reference/html/integration.html#scheduling-cron-expression).                                                |
| `hedera.mirror.importer.reconciliation.delay`                               | 1s                             | How much time to wait in between balance files                                                                                                                                                                                                                     |
| `hedera.mirror.importer.reconciliation.enabled`                             | true                           | Whether the balance reconciliation job should periodically run to reconcile data.                                                                                                                                                                                  |
| `hedera.mirror.importer.reconciliation.endDate`                             | 2262-04-11T23:47:16.854775807Z | The consensus timestamp of the last balance file to reconcile.                                                                                                                                                                                                     |
| `hedera.mirror.importer.reconciliation.remediationStrategy`                 | FAIL                           | The strategy to use to handle errors. Can be ACCUMULATE, RESET, or FAIL. ACCUMULATE and RESET will both proceed after an error, but RESET will correct the balances while ACCUMULATE does not                                                                      |
| `hedera.mirror.importer.reconciliation.startDate`                           | 1970-01-01T00:00:00Z           | The consensus timestamp of the first balance file to reconcile.                                                                                                                                                                                                    |
| `hedera.mirror.importer.reconciliation.token`                               | false                          | Whether to reconcile token information.                                                                                                                                                                                                                            |
| `hedera.mirror.importer.retention.batchPeriod`                              | 1d                             | How often to commit deletions when pruning.                                                                                                                                                                                                                        |
| `hedera.mirror.importer.retention.enabled`                                  | false                          | Whether to data retention should be enabled to purge older data.                                                                                                                                                                                                   |
| `hedera.mirror.importer.retention.exclude`                                  | []                             | Which tables to exclude when pruning data. By default it is empty to indicate no tables will be excluded from retention.                                                                                                                                           |
| `hedera.mirror.importer.retention.frequency`                                | 1d                             | How often to run the retention job to purge older data. If it is already running from a previous period, skip execution.                                                                                                                                           |
| `hedera.mirror.importer.retention.include`                                  | []                             | Which tables to include when pruning data. By default it is empty to indicate all tables that can be pruned will be.                                                                                                                                               |
| `hedera.mirror.importer.retention.period`                                   | 90d                            | How far in the past to remove data. This value is relative to the timestamp of the last transaction in the database and not to the current time.                                                                                                                   |
| `hedera.mirror.importer.topicRunningHashV2AddedTimestamp`                   | Network-based                  | Unix timestamp (in nanos) of first topic message with v2 as running hash version. Use this config to override the default network based value                                                                                                                      |
| `hedera.mirror.importer.shard`                                              | 0                              | The default shard number that the component participates in                                                                                                                                                                                                        |
| `hedera.mirror.importer.startDate`                                          |                                | The start date (inclusive) of the data to import. It takes effect 1) if it's set and the date is after the last downloaded file or the database is empty; 2) if it's not set and the database is empty, it defaults to now. Format: YYYY-MM-ddTHH:mm:ss.nnnnnnnnnZ |
| `hedera.mirror.importer.startBlockNumber`                                   | null                           | The block number that will be set as the downloaded stream files starting index.                                                                                                                                                                                   |
| `hedera.mirror.importer.verifyHashAfter`                                    | 1970-01-01T00:00:00Z           | Skip hash verification for stream files linked by hash until after (and not including) this point of time. Format: YYYY-MM-ddTHH:mm:ss.nnnnnnnnnZ                                                                                                                  |

### Transaction and Entity Filtering

The mirror node may be configured to only store a subset of data for entities and/or transaction types of interest -- essentially, which rows of data to retain.
Note that the `exclude` properties take priority over the `include` properties - if you list the same value in both lists, it will be excluded.
In addition, the various boolean `hedera.mirror.importer.record.entity.persist` properties may be specified to control which additional fields get stored (which additional tables get recorded).
See the `hedera.mirror.importer.parser.include.*` and `hedera.mirror.importer.parser.exclude.*` properties listed in the table above for full details.

#### Filtering Example
The scenario we wish to model is the same for each of the three configuration formats.  Only choose one of the three ways to
configure your instance of the mirror node.

* We wish to omit all records (regardless of transaction type) that are associated with account **0.0.98**, which is the account representing the network (to which fees generally get paid to).
* We are interested in all **CRYPTOTRANSFER** transactions, for all accounts other than **0.0.98**.
* We are interested in accounts **0.0.1000** and **0.0.1001**, and wish to store all their transactions, regardless of transaction type.
* We are also interested in system files **0.0.101** and **0.0.102**, and wish to store all their **FILEAPPEND**, **FILECREATE**, **FILEDELETE**, and **FILEUPDATE** transactions.
* We do not wish to persist message topics for any transactions we do store.

#### application.yml

To configure the above scenario via `application.yml` file, include the following lines:

```yaml
hedera:
  mirror:
    importer:
      parser:
        exclude:
          - entity: [0.0.98]
        include:
          - transaction: [CRYPTOTRANSFER]
          - entity: [0.0.1000, 0.0.1001]
          - entity: [0.0.101, 0.0.102]
            transaction: [FILEAPPEND, FILECREATE, FILEDELETE, FILEUPDATE]
        record:
          entity:
            persist:
              topics: false
```

#### application.properties

To configure the above scenario via `application.properties` file, include the following lines:

```yaml
hedera.mirror.importer.parser.exclude[0].entity[0]=0.0.98
hedera.mirror.importer.parser.include[0].transaction[0]=CRYPTOTRANSFER
hedera.mirror.importer.parser.include[1].entity[0]=0.0.1000
hedera.mirror.importer.parser.include[1].entity[1]=0.0.1001
hedera.mirror.importer.parser.include[2].entity[0]=0.0.101
hedera.mirror.importer.parser.include[2].entity[1]=0.0.102
hedera.mirror.importer.parser.include[2].transaction[0]=FILEAPPEND
hedera.mirror.importer.parser.include[2].transaction[1]=FILECREATE
hedera.mirror.importer.parser.include[2].transaction[2]=FILEDELETE
hedera.mirror.importer.parser.include[2].transaction[3]=FILEUPDATE
hedera.mirror.importer.parser.record.entity.persist.topics=false
```

#### Environment variables

To configure the above scenario via environmental variables, set the following:

```yaml
HEDERA_MIRROR_IMPORTER_PARSER_EXCLUDE_0_ENTITY_0_: 0.0.98
HEDERA_MIRROR_IMPORTER_PARSER_INCLUDE_0_TRANSACTION_0_: CRYPTOTRANSFER
HEDERA_MIRROR_IMPORTER_PARSER_INCLUDE_1_ENTITY_0_: 0.0.1000
HEDERA_MIRROR_IMPORTER_PARSER_INCLUDE_1_ENTITY_1_: 0.0.1001
HEDERA_MIRROR_IMPORTER_PARSER_INCLUDE_2_ENTITY_0_: 0.0.101
HEDERA_MIRROR_IMPORTER_PARSER_INCLUDE_2_ENTITY_1_: 0.0.102
HEDERA_MIRROR_IMPORTER_PARSER_INCLUDE_2_TRANSACTION_0_: FILEAPPEND
HEDERA_MIRROR_IMPORTER_PARSER_INCLUDE_2_TRANSACTION_1_: FILECREATE
HEDERA_MIRROR_IMPORTER_PARSER_INCLUDE_2_TRANSACTION_2_: FILEDELETE
HEDERA_MIRROR_IMPORTER_PARSER_INCLUDE_2_TRANSACTION_3_: FILEUPDATE
HEDERA_MIRROR_IMPORTER_PARSER_RECORD_ENTITY_PERSIST_TOPICS: "false"
```

### Export transactions to PubSub

Importer can be configured to publish transactions (in json format) to a Pubsub topic using following properties:

- `spring.cloud.gcp.pubsub.enabled`
- `spring.cloud.gcp.pubsub.project-id`
- `hedera.mirror.importer.parser.record.pubsub.topicName`
- `spring.cloud.gcp.pubsub.credentials.*`
- `hedera.mirror.importer.parser.record.entity.enabled` (Importer can not export to both database and pubsub
  simultaneously)

See [Spring Cloud documentation](https://cloud.spring.io/spring-cloud-static/spring-cloud-gcp/1.2.2.RELEASE/reference/html/#pubsub-configuration)
for more info about `spring.cloud.gcp.*` properties.

### Connect to S3 with the Default Credentials Provider

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
`hedera.mirror.monitor.health.release.cacheExpiry`              | 30s     | The amount of time to cache cluster release health status
`hedera.mirror.monitor.health.release.enabled`                  | false   | Whether to enable cluster release health check
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
| `hedera.mirror.rest.metrics.config`                                | See application.yml     | The configuration to pass to Swagger stats (https://swaggerstats.io/guide/conf.html#options)                                                                                                  |
| `hedera.mirror.rest.metrics.config.authentication`                 | true                    | Whether access to metrics for the REST API is authenticated                                                                                                                                   |
| `hedera.mirror.rest.metrics.config.username`                       | mirror_api_metrics      | The REST API metrics username to access the dashboard                                                                                                                                         |
| `hedera.mirror.rest.metrics.config.password`                       | mirror_api_metrics_pass | The REST API metrics password to access the dashboard                                                                                                                                         |
| `hedera.mirror.rest.metrics.config.uriPath`                        | '/swagger'              | The REST API metrics uri path                                                                                                                                                                 |
| `hedera.mirror.rest.metrics.enabled`                               | true                    | Whether metrics should be collected and exposed for scraping                                                                                                                                  |
| `hedera.mirror.rest.metrics.ipMetrics`                             | false                   | Whether metrics should be associated with a masked client IP label                                                                                                                            |
| `hedera.mirror.rest.network.unreleasedSupplyAccounts`              | [0.0.2, 0.0.42, ...]    | An array of account IDs whose aggregated balance subtracted from the total supply is the released supply                                                                                      |
| `hedera.mirror.rest.openapi.specFileName`                          | 'openapi'               | The file name of the OpenAPI spec file                                                                                                                                                        |
| `hedera.mirror.rest.openapi.swaggerUIPath`                         | '/docs'                 | Swagger UI path for your REST API                                                                                                                                                             |
| `hedera.mirror.rest.port`                                          | 5551                    | The REST API port                                                                                                                                                                             |
| `hedera.mirror.rest.query.maxRepeatedQueryParameters`              | 100                     | The maximum number of times any query parameter can be repeated in the uri                                                                                                                    |
| `hedera.mirror.rest.query.maxTimestampRange`                       | 7d                      | The maximum amount of time a timestamp range query param can span for some APIs.                                                                                                              |
| `hedera.mirror.rest.query.maxTransactionConsensusTimestampRange`   | 35m                     | The maximum amount of time of a transaction's consensus timestamp from its valid start timestamp.                                                                                             |
| `hedera.mirror.rest.response.compression`                          | true                    | Whether content negotiation should occur to compress response bodies if requested                                                                                                             |
| `hedera.mirror.rest.response.headers.default`                      | See application.yml     | The default headers to add to every response.                                                                                                                                                 |
| `hedera.mirror.rest.response.headers.path`                         | See application.yml     | The per path headers to add to every response. The key is the route name and the value is a header map.                                                                                       |
| `hedera.mirror.rest.response.includeHostInLink`                    | false                   | Whether to include the hostname and port in the next link in the response                                                                                                                     |
| `hedera.mirror.rest.response.limit.default`                        | 25                      | The default value for the limit parameter that controls the REST API response size                                                                                                            |
| `hedera.mirror.rest.response.limit.max`                            | 100                     | The maximum size the limit parameter can be that controls the REST API response size                                                                                                          |
| `hedera.mirror.rest.response.limit.tokenBalance.multipleAccounts`  | 50                      | The maximum number of token balances per account for endpoints which return such info for multiple accounts                                                                                   |
| `hedera.mirror.rest.response.limit.tokenBalance.singleAccount`     | 1000                    | The maximum number of token balances per account for endpoints which return such info for a single account                                                                                    |
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
          accessKey: <accessKey>
          bucketName: 'hedera-mainnet-streams'
          cloudProvider: 'GCP'
          network: 'MAINNET'
          region: 'us-east-1'
          secretKey: <secretKey>
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
`hedera.mirror.rosetta.realm`                        | 0                   | The default realm number within the shard
`hedera.mirror.rosetta.shard`                        | 0                   | The default shard number that this mirror node participates in
`hedera.mirror.rosetta.shutdownTimeout`              | 10s                 | The time to wait for the server to shutdown gracefully

## Web3 API

Similar to the [Importer](#importer), the web3 API uses [Spring Boot](https://spring.io/projects/spring-boot) properties
to configure the application.

The following table lists the available properties along with their default values. Unless you need to set a non-default
value, it is recommended to only populate overridden properties in the custom `application.yml`.

Name                                                        | Default                                    | Description
------------------------------------------------------------|--------------------------------------------| ---------------------------------------
`hedera.mirror.web3.db.host`                                | 127.0.0.1                                  | The IP or hostname used to connect to the database
`hedera.mirror.web3.db.name`                                | mirror_node                                | The name of the database
`hedera.mirror.web3.db.password`                            | mirror_web3_pass                           | The database password used to connect to the database
`hedera.mirror.web3.db.port`                                | 5432                                       | The port used to connect to the database
`hedera.mirror.web3.db.sslMode`                             | DISABLE                                    | The ssl level of protection against eavesdropping, man-in-the-middle (MITM) and impersonation on the db connection. Accepts either DISABLE, ALLOW, PREFER, REQUIRE, VERIFY_CA or VERIFY_FULL.
`hedera.mirror.web3.db.username`                            | mirror_web3                                | The username used to connect to the database
`hedera.mirror.web3.evm.directTokenCall`                    | true                                       | Flag enabling contract like calls to tokens
`hedera.mirror.web3.evm.dynamicEvmVersion`                  | false                                      | Flag indicating whether a dynamic evm version to be used
`hedera.mirror.web3.evm.evmVersion`                         | v0.32                                      | The besu EVM version to be used as dynamic one
`hedera.mirror.web3.evm.fundingAccount`                     | 0x0000000000000000000000000000000000000062 | Default Hedera funding account
`hedera.mirror.web3.evm.maxGasRefundPercentage`             | 20%                                        | Maximal procent of gas refunding
`hedera.mirror.web3.evm.expirationCacheTime`                | 10m                                        | Maximum time for contract bytecode's caching
`hedera.mirror.web3.cache.contractState`                    | expireAfterWrite=5s,maximumSize=10000,recordStats | Cache configuration for contract state
`hedera.mirror.web3.cache.entity `                          | expireAfterWrite=30s,maximumSize=10000,recordStats | Cache configuration for entity
`hedera.mirror.web3.evm.rateLimit`                          | 100s                                       | Maximum RPS limit
