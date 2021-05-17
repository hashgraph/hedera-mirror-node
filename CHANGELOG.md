# Hedera Mirror Node Changelog

## 0.33.0 (2021-05-17)



### Enhancements

- Change default chart db back to PostgreSQL (0.33) [#1972](https://github.com/hashgraph/hedera-mirror-node/pull/1972)
- Adjust timescaledb params [#1922](https://github.com/hashgraph/hedera-mirror-node/pull/1922)
- Some timescaledb params set in chart values are suboptimal [#1921](https://github.com/hashgraph/hedera-mirror-node/issues/1921)
- Add multi node validation support to Acceptance tests [#1900](https://github.com/hashgraph/hedera-mirror-node/pull/1900)
- Ignore Sonar false positives [#1896](https://github.com/hashgraph/hedera-mirror-node/pull/1896)
- Change helm-gh-pages action to use GITHUB_TOKEN [#1893](https://github.com/hashgraph/hedera-mirror-node/pull/1893)
- Add a property to override every child chart's tag [#1885](https://github.com/hashgraph/hedera-mirror-node/pull/1885)
- Disabled scheduled crypto create in acceptance tests [#1876](https://github.com/hashgraph/hedera-mirror-node/pull/1876)
- Bump hedera-protobuf-java-api to 0.13.0-rc-1 w support for ServiceEndpoint [#1871](https://github.com/hashgraph/hedera-mirror-node/pull/1871)
- Automate release process [#1870](https://github.com/hashgraph/hedera-mirror-node/pull/1870)
- Auto generate chart passwords [#1862](https://github.com/hashgraph/hedera-mirror-node/pull/1862)
- Bump hedera-protobuf-java-api from 0.13.0-alpha.2 to 0.13.0-rc-1 [#1854](https://github.com/hashgraph/hedera-mirror-node/issues/1854)
- Acceptance tests should use validated network nodes [#1852](https://github.com/hashgraph/hedera-mirror-node/issues/1852)
- Update traefik service type [#1844](https://github.com/hashgraph/hedera-mirror-node/pull/1844)
- Kubernetes: TLS with DNS [#702](https://github.com/hashgraph/hedera-mirror-node/issues/702)
- Automate release process [#344](https://github.com/hashgraph/hedera-mirror-node/issues/344)

### Bug Fixes

- Always create redis secret [#1977](https://github.com/hashgraph/hedera-mirror-node/pull/1977)
- Fix service endpoint migration [#1974](https://github.com/hashgraph/hedera-mirror-node/pull/1974)
- V1.37.1__add_address_book_service_endpoints migration failing [#1969](https://github.com/hashgraph/hedera-mirror-node/issues/1969)
- Use PAT in automated release [#1930](https://github.com/hashgraph/hedera-mirror-node/pull/1930)
- Fix automated release workflow failure [#1928](https://github.com/hashgraph/hedera-mirror-node/pull/1928)
- Fix deadlock due to not cleaning up PostgreSQL CopyIn when there's an error (master) [#1895](https://github.com/hashgraph/hedera-mirror-node/pull/1895)
- Fix gRPC dashboard [#1877](https://github.com/hashgraph/hedera-mirror-node/pull/1877)
- Exclude fee-free transactions in monitor check [#1874](https://github.com/hashgraph/hedera-mirror-node/pull/1874)
- Separate chart version replacer [#1872](https://github.com/hashgraph/hedera-mirror-node/pull/1872)
- Monitor reports error "accNum is 0" [#1829](https://github.com/hashgraph/hedera-mirror-node/issues/1829)

### Dependency Upgrades

- Bump hosted-git-info from 2.8.8 to 2.8.9 in /hedera-mirror-rest/monitoring/monitor_apis [#1954](https://github.com/hashgraph/hedera-mirror-node/pull/1954)
- Bump hosted-git-info from 2.8.8 to 2.8.9 in /hedera-mirror-rest/check-state-proof [#1952](https://github.com/hashgraph/hedera-mirror-node/pull/1952)
- Bump hedera proto to 0.13.2 [#1949](https://github.com/hashgraph/hedera-mirror-node/pull/1949)
- Bump aws-sdk from 2.892.0 to 2.901.0 in /hedera-mirror-rest [#1943](https://github.com/hashgraph/hedera-mirror-node/pull/1943)
- Bump chalk from 4.1.0 to 4.1.1 in /hedera-mirror-rest/check-state-proof [#1890](https://github.com/hashgraph/hedera-mirror-node/pull/1890)
- Bump reporting-plugin from 4.0.101 to 4.0.102 [#1889](https://github.com/hashgraph/hedera-mirror-node/pull/1889)
- Bump testcontainers from 7.7.0 to 7.8.0 in /hedera-mirror-rest [#1888](https://github.com/hashgraph/hedera-mirror-node/pull/1888)
- Bump aws-sdk from 2.888.0 to 2.892.0 in /hedera-mirror-rest [#1887](https://github.com/hashgraph/hedera-mirror-node/pull/1887)
- Bump eslint-plugin-jest from 24.3.5 to 24.3.6 in /hedera-mirror-rest [#1886](https://github.com/hashgraph/hedera-mirror-node/pull/1886)
- Bump disruptor from 3.4.2 to 3.4.3 [#1884](https://github.com/hashgraph/hedera-mirror-node/pull/1884)
- Bump junit-jupiter from 1.15.2 to 1.15.3 [#1883](https://github.com/hashgraph/hedera-mirror-node/pull/1883)
- Bump spring-boot-starter-parent from 2.4.4 to 2.4.5 [#1882](https://github.com/hashgraph/hedera-mirror-node/pull/1882)
- Bump embedded.testcontainers.version from 2.0.5 to 2.0.6 [#1881](https://github.com/hashgraph/hedera-mirror-node/pull/1881)
- Bump software.amazon.awssdk:bom from 2.16.34 to 2.16.47 [#1879](https://github.com/hashgraph/hedera-mirror-node/pull/1879)
- Bump cucumber.version from 6.10.2 to 6.10.3 [#1865](https://github.com/hashgraph/hedera-mirror-node/pull/1865)
- Bump jib-maven-plugin from 2.8.0 to 3.0.0 [#1836](https://github.com/hashgraph/hedera-mirror-node/pull/1836)

### Contributors

We'd like to thank all the contributors who worked on this release!

- [@xin-hedera](https://github.com/xin-hedera)
- [@Nana-EC](https://github.com/Nana-EC)
- [@dependabot[bot]](https://github.com/apps/dependabot)
- [@steven-sheehy](https://github.com/steven-sheehy)

# Changelog

Besides bug fixes, some features may have changed with this release which need your attention, these will be listed here.

### Addded `valid_duration_seconds` and `max_fee` columns on `t_transactions and REST-API transaction response`

`valid_duration_seconds` represents the seconds for which a submitted transaction is to be deemed valid beyond the start time. The transaction is invalid if consensusTimestamp > transactionValidStart + `valid_duration_seconds`
`max_fee`represents the maximum transaction fee the client is willing to pay, which is split between the network and the node
Both values are set to null if a valid value is not received.
Both `valid_duration_seconds` and `max_fee` are exposed in the REST-API transactions response. 

### New account_balances schema

  Added tables `account_balances` and `account_balance_sets`

### Updates to config.json

  Added `accountBalancesInsertBatchSize`
  Added `accountBalancesFileBufferSize`
  Added `accountBalancesUseTransaction`
  Added `systemShardNum`

### Removal of records.json, balance.json, events.json and loggerStatus.json

These files are no longer used by mirror node, instead the data they used to store is persisted in the database. The table keeping this data is `t_application_status`.

### Flyway database management in java code

Database upgrades are now performed automatically when the mirror node components start. Note the following additions to `config.json` to support this process.

```
  "dbName":"postgres" // if environment variable HEDERA_MIRROR_DB_NAME is set, it will take precedence
  "apiUsername":"api" // if environment variable DB_USER is set, it will take precedence
  "apiPassword":"mysecretpassword" // if environment variable DB_PASS is set, it will take precedence
```

### Set "stopLoggingIfRecordHashMismatch" to "X" 

This is to ease onboarding on the integration test network, there are a few gaps in the file hash history as a result of testing which result in the parser stopping, setting this value to "X" in the `config.json` file will ensure parsing continues regardless of the hash history. This should not be set to "X" in production of course.

### Addition of event parsing

Event files from the network may now be parsed with the mirror node.

### Updates to config.json

  Renamed `stopLoggingIfHashMismatch` to `stopLoggingIfRecordHashMismatch`.
  Added `stopLoggingIfRecordHashMismatch`. Behaves in the same manner as `stopLoggingIfRecordHashMismatch` for events.
  Added `balanceVerifySigs=false`. This is temporary while we have networks which don't generate signatures for balance files. If the network you are using does generate signatures for balance files, you should change this to `true`.

### Deletion of the database version table

This table is no longer necessary due to the change to flyway for database schema management.

### Switched to Flyway for database schema management

Now using https://flywaydb.org/getstarted/ for schema management. This is fully integrated in docker images. 
Starting the `mirror-node-flyway` container will automatically patch the database

### t_transactions relation to t_record_files

The `t_transactions.fk_rec_file_id` column contains the `id` of the record file that contained this transaction from the `t_record_files` table.

### valid files moved to nested folder structure

In order to avoid the risk of the number of files in a single folder exceeding operating system limits, parsed record files are moved to a nested folder structure such as `MirrorNodeData/recordstreams/parsedRecordFiles/2019/07/18`.

### buildimages.sh now prompts for project compilation

Prompts to compile either with

- A docker container
- Your local maven
- Skip

### Moved buildimages.sh script back to root of project

Makes it easier to find

### Removed unnecessary defaultParseDir parameter from config.json

This parameter was not necessary.

### Added prompt to update 0.0.102 file from network during docker build

./buildimages.sh will prompt whether you want to download the 0.0.102 file from the network (it is recommended you do so the first time).
If you answer 2 (no), the file will not be downloaded, if you answer 1 (yes), you will be prompted for the following information:

-Node address in the format of `ip:port` or `host:port`. (e.g. 192.168.0.2:50211)
-Node ID, the Hedera account for the node (e.g. 0.0.3).
-Operator ID, your account (e.g. 0.0.2031)
-Operator key, the private key for your account

### Added class to download and parse record files in one step

This class will download record files and parse the newly downloaded files immediately, then loop back to downloading available record files.

### Increased logging granularity

Each class outputs its category in the common log for ease of debugging.

### Removal of `downloadPeriodSec` parameter from config.json

Download and processing (logging) activities now run in a continuous loop, restarting as soon as they finished to lower the latency for data availability in the database as much as possible.
To gracefully stop a running process, create a file called `stop` in the folder where the application was launched from.
For example in Unix systems
```
touch stop
```

Remember to remove this file once you are ready to restart the processes.

### All balance logging is now done in a single class

Logging latest balance and balance history is now done sequentially from a single class.

### Performance optimisations to balance tables

Balance tables (t_account_balances and t_balance_history) were using the same t_entities table as other tables for referential integrity.
This lead to contention and deadlocks that could impact latency on delivery of transaction data.
The balance tables are now independent to avoid this resource contention.

### The Address book file automatically refreshes

Changes to the address book file (0.0.102) through fileUpdate transactions now update the 0.0.102 file with the new contents stipulated by the transaction.

### `stopLoggingIfHashMismatch` change

This field was a boolean, it's now a string. See details on configuration files for additional information.

### Database transaction control

Database transactions are now used to ensure a file cannot be partially saved to the database. If an error occurs during file processing, all changes are rolled back.

### Database storage optimisation

The database schema has been changed to maximise denormalisation in order to optimise storage and data integrity.

### Addded `deleted` column on `t_entities`

This column defaults to false on creation, and is set to true when a `delete` transaction is processed. It is unset when an `undelete` transaction is processed.

### Added address book download capability

The address book may be downloaded from the network using this software.

### Addition of a REST api

A REST api written in `node` is now available in this project.

### Switched from individual docker containers to `docker-compose`

See below for instructions

### Creation of a database version table

Added version numbering to the database to make upgrades easier.
The `t_version` table contains the current version of the database.
The upgrade scripts only apply changes that pertain to the current version of the database and update it when complete.

### Additional columns in `t_entities`

Added columns to t_entities to record expiry time, autorenew period, keys and proxy account.

### Database URL as environment parameter

The connection to the database can be specified as an environment variable `HEDERA_MIRROR_DB_URL` or specified via a `.env.` file.

### Added optional persistence of crypto transfers, file contents, contract creation and call results and claim data

See section on configuration for additional details.

### Addition of `stopLoggingIfHashMismatch` configuration item

When processing files after they have been downloaded, this value will determine if a hash mismatch should result in processing stopping. If the currently processed file name is greater than the value stored, processing will stop. Insert the name of the file which failed the hash check in this field in order to allow processing to continue (data loss will result).

### `node-log` has been removed from `log4.xml`

All logging now goes to a single recordStream-log

### Addition of `maxDownloadItems` parameter in config.json file

For testing purposes, this value may be set to a number other than 0. This will limit the number of downloaded files accordingly. If set to 0, all files are downloaded from S3 or GCP.

### Addition of a `postgresUpdate.sql` script

This script contains the incremental changes to the database in the event you need to upgrade an older version of the database to the current version.

### The `t_account_balance_history` table has a new `seconds` column

The `seconds` column contains the number of seconds since epoch in addition to the `snapshot_time` which is a `timestamp`, both contain the same value in different formats.

### Loggers now return true/false depending on success/failure to log

Before this change, failure to log wasn't detected by the class calling the logger which resulted in files being moved when they shouldn't be.

### Addition of cloud storage as an alternative to AWS

It is now possible to specify that files are downloaded from Google Compute Platform storage instead of Amazon S3.
The switch is enabled via the `config/config.json` file.

### Removal of command line arguments

All configuration parameters are now sourced from `./config/config.json` by default, it no longer needs to be specified on the command line.

### Inclusion of logging to PostgreSQL database

The `recordFileLogger`, `balanceFileHistoryLogger` and `balanceFileLogger` write transactions, records and balances to a PostgreSQL database. The scripts for creating the database are provided in the `postreSQL` folder.

### Amazon Hedera S3 access keys

Access and secret keys to Hedera's Amazon S3 bucket may now be specified via environment variables or a `./.env` file

### Configuration files

All configuration files now reside in the `./config` folder.

### new `Config.json` `downloadPeriodSec` parameter

If set to 0, the downloader will download available files and exit.
If set to another value, the downloader will download available files, wait `downloadPeriodSec` and start downloading new files again until it is stopped by the operator (`kill or ctrl-c`).

### Logging

By default, logging will output to the console and to `logs/hedera-mirror-node.log`.
Should you wish to change the logging, copy the `log4j2.xml` file from `src/main/resources` or `target` to the same location as the `mirror-node.jar` file, edit accordingly and include the following in your `java` command:

```shell
    -Dlog4j.configurationFile=./log4j2.xml
```

for example

```shell
    java -Dlog4j.configurationFile=./log4j2.xml -cp mirror-node.jar com.hedera.parser.RecordFileParser
```

This will ensure that the `log4j2.xml` file is used to direct logging accordingly.
