# Configuration

The two components of the Hedera Mirror Node, the parser and REST API, both support loading configuration from
an `application.yml` file or via the environment. Some of the configuration properties overlap between the two, so
to simplify configuration it is recommended to create a single `application.yml` for use by both.

Most configuration settings have appropriate defaults and can be left unchanged. One of the exceptions is
`hedera.mirror.downloader.bucketName` as it is a required field but does not have a default. This is because the bucket
is not publicly available at this time. Another important property that should be adjusted is `hedera.mirror.network` as
it controls which Hedera network to mirror. Additionally, the password properties have a default, but it is recommended
they be changed from the default.

## Parser

The Hedera Mirror Node uses [Spring Boot](https://spring.io/projects/spring-boot) properties to configure the application. 
As as a result, you can use properties files, YAML files, environment variables and command-line arguments
to supply configuration. See the Spring Boot [documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/spring-boot-features.html#boot-features-external-config)
for the location and order it loads configuration.

## REST API

The REST API supports loading configuration from YAML or environment variables. By default it loads a file named
`application.yml` or `application.yaml` in each of the search paths (see below). The file name can be changed by setting
the `CONFIG_NAME` environment variable. A custom location can be loaded by setting the `CONFIG_PATH` environment variable.
The configuration is loaded in the following order with the latter configuration overwriting (technically recursively
merged into) the current configuration:

1. `./config/application.yml`
2. `./application.yml`
3. `${CONFIG_PATH}/application.yml`
4. Environment variables that start with `HEDERA_MIRROR_` (e.g. `HEDERA_MIRROR_API_MAXLIMIT=100`)

## Properties

The following table lists the available properties along with their default values. Unless you need to set a non-default
value, it is recommended to only populate overridden properties in the custom `application.yml`.

Name | Default | Description
---|---|---
`hedera.mirror.addressBookPath` | "" | The path to an address book used to override the built-in address book
`hedera.mirror.api.includeHostInLink` | false | Whether to include the hostname and port in the next link in the response
`hedera.mirror.api.maxLimit` | 1000 | The maximum size the limit parameter can be that controls the REST API response size
`hedera.mirror.api.log.level` | debug | The logging level. Can be trace, debug, info, warn, error or fatal.
`hedera.mirror.api.ttl.accounts` | 60 | The time to live in seconds that accounts should stay in cache
`hedera.mirror.api.ttl.balances` | 60 | The time to live in seconds that balances should stay in cache
`hedera.mirror.api.ttl.events` | 10 | The time to live in seconds that events should stay in cache
`hedera.mirror.api.ttl.transactions` | 10 | The time to live in seconds that transactions should stay in cache
`hedera.mirror.api.port` | 5551 | The REST API port
`hedera.mirror.api.includeHostInLink` | false | Whether to include the host:port in the next links returned by the REST API
`hedera.mirror.dataPath` | ./data | The data directory used to store downloaded files and other application state
`hedera.mirror.db.apiPassword` | mirror_api_pass | The database password the API uses to connect. **Should be changed from default**
`hedera.mirror.db.apiUsername` | mirror_api | The username the API uses to connect to the database
`hedera.mirror.db.host` | 127.0.0.1 | The IP or hostname used to connect to the database
`hedera.mirror.db.name` | mirror_node | The name of the database
`hedera.mirror.db.password` | mirror_node_pass | The database password the processor uses to connect. **Should be changed from default**
`hedera.mirror.db.port` | 5432 | The port used to connect to the database
`hedera.mirror.db.username` | mirror_node | The username the processor uses to connect to the database
`hedera.mirror.downloader.accessKey` | "" | The cloud storage access key
`hedera.mirror.downloader.balance.batchSize` | 15 | The number of signature files to download per node before downloading the signed files
`hedera.mirror.downloader.balance.enabled` | true | Whether to enable balance file downloads
`hedera.mirror.downloader.balance.frequency` | 500ms | The fixed period between invocations. Can accept duration units like `10s`, `2m` etc.
`hedera.mirror.downloader.balance.prefix` | accountBalances/balance | The prefix to search cloud storage for balance files
`hedera.mirror.downloader.balance.threads` | 13 | The number of threads to search for new files to download
`hedera.mirror.downloader.bucketName` | "" | The cloud storage bucket name to download streamed files. **Required, but not publicly available**
`hedera.mirror.downloader.cloudProvider` | S3 | The cloud provider to download files from. Either `S3`, `GCP` or `LOCAL`
`hedera.mirror.downloader.event.batchSize` | 15 | The number of signature files to download per node before downloading the signed files
`hedera.mirror.downloader.event.enabled` | false | Whether to enable event file downloads
`hedera.mirror.downloader.event.frequency` | 1m | The fixed period between invocations. Can accept duration units like `50ms`, `10s` etc.
`hedera.mirror.downloader.event.prefix` | eventsStreams/events_ | The prefix to search cloud storage for event files
`hedera.mirror.downloader.event.threads` | 13 | The number of threads to search for new files to download
`hedera.mirror.downloader.maxConcurrency` | 1000 | The maximum number of allowed open HTTP connections. Used by AWS SDK directly.
`hedera.mirror.downloader.record.batchSize` | 40 | The number of signature files to download per node before downloading the signed files
`hedera.mirror.downloader.record.enabled` | true | Whether to enable record file downloads
`hedera.mirror.downloader.record.frequency` | 500ms | The fixed period between invocations. Can accept duration units like `10s`, `2m` etc.
`hedera.mirror.downloader.record.prefix` | recordstreams/record | The prefix to search cloud storage for record files
`hedera.mirror.downloader.record.threads` | 13 | The number of threads to search for new files to download
`hedera.mirror.downloader.region` | us-east-1 | The region associated with the bucket
`hedera.mirror.downloader.secretKey` | "" | The cloud storage secret key
`hedera.mirror.network` | MAINNET | Which Hedera network to use. Can be either `MAINNET` or `TESTNET`
`hedera.mirror.parser.balance.batchSize` | 2000 | The number of balances to insert before committing
`hedera.mirror.parser.balance.enabled` | true | Whether to enable balance file parsing
`hedera.mirror.parser.balance.fileBufferSize` | 200000 | The size of the buffer to use when reading in the balance file
`hedera.mirror.parser.event.enabled` | false | Whether to enable balance file parsing
`hedera.mirror.parser.event.frequency` | 1m | The fixed period between invocations. Can accept duration units like `50ms`, `10s` etc.
`hedera.mirror.parser.record.enabled` | true | Whether to enable balance file parsing
`hedera.mirror.parser.record.frequency` | 500ms | The fixed period between invocations. Can accept duration units like `10s`, `2m` etc.
`hedera.mirror.parser.record.persistClaims` | false | Persist claim data to the database
`hedera.mirror.parser.record.persistContracts` | true | Persist contract data to the database
`hedera.mirror.parser.record.persistCryptoTransferAmounts` | true | Persist crypto transfer amounts to the database
`hedera.mirror.parser.record.persistFiles` | true | Persist all file data to the database
`hedera.mirror.parser.record.persistSystemFiles` | true | Persist only system files (number lower than `1000`) to the database
`hedera.mirror.shard` | 0 | The default shard number that this mirror node participates in
