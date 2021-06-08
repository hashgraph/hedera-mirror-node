# Hedera Mirror Node Changelog

## 0.34.0 (2021-06-08)



### Enhancements

- Remove JMeter based performance tests [#1981](https://github.com/hashgraph/hedera-mirror-node/pull/1981)
- Change default chart db back to PostgreSQL [#1971](https://github.com/hashgraph/hedera-mirror-node/pull/1971)
- Signature consensus ratio config [#1950](https://github.com/hashgraph/hedera-mirror-node/pull/1950)
- Improve rosetta Dockerfile and workflow [#1946](https://github.com/hashgraph/hedera-mirror-node/pull/1946)
- Upgrade to hedera-sdk-go v2.1.5 in rosetta module [#1944](https://github.com/hashgraph/hedera-mirror-node/pull/1944)
- Add subscribe only flow to monitor [#1940](https://github.com/hashgraph/hedera-mirror-node/pull/1940)
- Add alertmanager inhibit rules [#1925](https://github.com/hashgraph/hedera-mirror-node/pull/1925)
- Provide configuration option to verify recordstream signatures for all nodes [#1869](https://github.com/hashgraph/hedera-mirror-node/issues/1869)
- Monitor feature parity with JMeter tests [#1850](https://github.com/hashgraph/hedera-mirror-node/issues/1850)
- Add NFT Design Doc [#1845](https://github.com/hashgraph/hedera-mirror-node/pull/1845)
- Alert dependencies [#1813](https://github.com/hashgraph/hedera-mirror-node/issues/1813)
- NFT design [#1809](https://github.com/hashgraph/hedera-mirror-node/issues/1809)
- Upgrade to hedera-sdk-go v2 in rosetta module [#1521](https://github.com/hashgraph/hedera-mirror-node/issues/1521)

### Bug Fixes

- Port fix TopicMessageServiceTest.startTimeAfterNow to release/0.34 [#2064](https://github.com/hashgraph/hedera-mirror-node/pull/2064)
- Port optimize rest account balance query to release/0.34 [#2063](https://github.com/hashgraph/hedera-mirror-node/pull/2063)
- Fix pgpool admin password [#1998](https://github.com/hashgraph/hedera-mirror-node/pull/1998)
- Fix using wrapper chart without redis chart enabled [#1980](https://github.com/hashgraph/hedera-mirror-node/pull/1980)
- Port fix service endpoint migration (#1974) [#1978](https://github.com/hashgraph/hedera-mirror-node/pull/1978)
- Fix hedera-mirror-test Dockerfile pointing to tag that does not exist [#1973](https://github.com/hashgraph/hedera-mirror-node/pull/1973)
- Disable AccountBalanceFileParserTest & RecordFileParserIntegrationTest [#1948](https://github.com/hashgraph/hedera-mirror-node/pull/1948)
- Fix rosetta docker build failure [#1942](https://github.com/hashgraph/hedera-mirror-node/pull/1942)
- Add transaction support to entity updater package [#1938](https://github.com/hashgraph/hedera-mirror-node/pull/1938)
- Use PAT to create PR in automated release process [#1932](https://github.com/hashgraph/hedera-mirror-node/pull/1932)
- Error running entity-updater:  index row size exceeds maximum for index "entity__public_key_natural_id"  error [#1905](https://github.com/hashgraph/hedera-mirror-node/issues/1905)

### Dependency Upgrades

- Bump jackson-dataformat-msgpack from 0.8.22 to 0.8.24 [#1995](https://github.com/hashgraph/hedera-mirror-node/pull/1995)
- Bump embedded.testcontainers.version from 2.0.7 to 2.0.8 [#1994](https://github.com/hashgraph/hedera-mirror-node/pull/1994)
- Bump protobuf-java from 3.16.0 to 3.17.0 [#1991](https://github.com/hashgraph/hedera-mirror-node/pull/1991)
- Bump software.amazon.awssdk:bom from 2.16.59 to 2.16.63 [#1990](https://github.com/hashgraph/hedera-mirror-node/pull/1990)
- Bump cucumber.version from 6.10.3 to 6.10.4 [#1989](https://github.com/hashgraph/hedera-mirror-node/pull/1989)
- Bump frontend-maven-plugin from 1.11.3 to 1.12.0 [#1988](https://github.com/hashgraph/hedera-mirror-node/pull/1988)
- Bump grpc.version from 1.37.0 to 1.37.1 [#1987](https://github.com/hashgraph/hedera-mirror-node/pull/1987)
- Bump mathjs from 9.3.2 to 9.4.0 in /hedera-mirror-rest [#1986](https://github.com/hashgraph/hedera-mirror-node/pull/1986)
- Bump aws-sdk from 2.904.0 to 2.907.0 in /hedera-mirror-rest [#1984](https://github.com/hashgraph/hedera-mirror-node/pull/1984)
- Bump eslint-plugin-import from 2.22.1 to 2.23.2 in /hedera-mirror-rest [#1983](https://github.com/hashgraph/hedera-mirror-node/pull/1983)
- Bump mathjs from 9.3.2 to 9.4.0 in /hedera-mirror-rest/monitoring/monitor_apis [#1982](https://github.com/hashgraph/hedera-mirror-node/pull/1982)
- Bump aws-sdk from 2.901.0 to 2.904.0 in /hedera-mirror-rest [#1970](https://github.com/hashgraph/hedera-mirror-node/pull/1970)
- Bump swagger-stats to 0.99.1 and add its peer dependency prom-client 13.1.0 [#1967](https://github.com/hashgraph/hedera-mirror-node/pull/1967)
- Bump yargs from 17.0.0 to 17.0.1 in /hedera-mirror-rest/check-state-proof [#1966](https://github.com/hashgraph/hedera-mirror-node/pull/1966)
- Bump glob from 7.1.6 to 7.1.7 in /hedera-mirror-rest/check-state-proof [#1965](https://github.com/hashgraph/hedera-mirror-node/pull/1965)
- Bump github.com/golang/protobuf from 1.5.1 to 1.5.2 in /hedera-mirror-rosetta [#1964](https://github.com/hashgraph/hedera-mirror-node/pull/1964)
- Bump github.com/sirupsen/logrus from 1.4.2 to 1.8.1 in /hedera-mirror-rosetta [#1963](https://github.com/hashgraph/hedera-mirror-node/pull/1963)
- Bump protobuf-java from 3.15.8 to 3.16.0 [#1962](https://github.com/hashgraph/hedera-mirror-node/pull/1962)
- Bump software.amazon.awssdk:bom from 2.16.47 to 2.16.59 [#1961](https://github.com/hashgraph/hedera-mirror-node/pull/1961)
- Bump jacoco-maven-plugin from 0.8.6 to 0.8.7 [#1960](https://github.com/hashgraph/hedera-mirror-node/pull/1960)
- Bump codecov from 3.8.1 to 3.8.2 in /hedera-mirror-rest [#1959](https://github.com/hashgraph/hedera-mirror-node/pull/1959)
- Bump `@godaddy`/terminus from 4.7.1 to 4.7.2 in /hedera-mirror-rest [#1958](https://github.com/hashgraph/hedera-mirror-node/pull/1958)
- Bump prettier from 2.2.1 to 2.3.0 in /hedera-mirror-rest [#1957](https://github.com/hashgraph/hedera-mirror-node/pull/1957)
- Bump testcontainers from 7.8.0 to 7.10.0 in /hedera-mirror-rest [#1956](https://github.com/hashgraph/hedera-mirror-node/pull/1956)
- Bump hosted-git-info from 2.8.8 to 2.8.9 in /hedera-mirror-rest [#1953](https://github.com/hashgraph/hedera-mirror-node/pull/1953)
- Bump hedera proto to 0.13.2 [#1951](https://github.com/hashgraph/hedera-mirror-node/pull/1951)
- Bump embedded.testcontainers.version from 2.0.6 to 2.0.7 [#1924](https://github.com/hashgraph/hedera-mirror-node/pull/1924)
- Bump download-maven-plugin from 1.6.2 to 1.6.3 [#1923](https://github.com/hashgraph/hedera-mirror-node/pull/1923)
- Bump `@hashgraph`/proto from 1.0.23 to 1.0.25 in /hedera-mirror-rest/check-state-proof [#1916](https://github.com/hashgraph/hedera-mirror-node/pull/1916)
- Bump yargs from 16.2.0 to 17.0.0 in /hedera-mirror-rest/check-state-proof [#1915](https://github.com/hashgraph/hedera-mirror-node/pull/1915)
- Bump dependency-check-maven from 6.1.5 to 6.1.6 [#1911](https://github.com/hashgraph/hedera-mirror-node/pull/1911)
- Bump disruptor from 3.4.3 to 3.4.4 [#1910](https://github.com/hashgraph/hedera-mirror-node/pull/1910)
- Bump `@hashgraph`/proto from 1.0.24 to 1.0.25 in /hedera-mirror-rest [#1908](https://github.com/hashgraph/hedera-mirror-node/pull/1908)

### Contributors

We'd like to thank all the contributors who worked on this release!

- [@xin-hedera](https://github.com/xin-hedera)
- [@Nana-EC](https://github.com/Nana-EC)
- [@github-actions[bot]](https://github.com/apps/github-actions)
- [@dependabot[bot]](https://github.com/apps/dependabot)
- [@ijungmann](https://github.com/ijungmann)
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
