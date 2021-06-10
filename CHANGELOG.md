# Hedera Mirror Node Changelog

## 0.35.0 (2021-06-10)



### Enhancements

- Add a monitor service & change threads property [#2065](https://github.com/hashgraph/hedera-mirror-node/pull/2065)
- Support liveness probe delay flyway [#2051](https://github.com/hashgraph/hedera-mirror-node/pull/2051)
- Various production improvements [#2029](https://github.com/hashgraph/hedera-mirror-node/pull/2029)
- Show rosetta container log when validation fails [#2022](https://github.com/hashgraph/hedera-mirror-node/pull/2022)
- Simplify NodeSignatureVerifier statusMap [#2016](https://github.com/hashgraph/hedera-mirror-node/pull/2016)
- Improve automated release process [#2003](https://github.com/hashgraph/hedera-mirror-node/pull/2003)
- Helm acceptance tests [#2002](https://github.com/hashgraph/hedera-mirror-node/pull/2002)
- Bump versions for v0.35.0-SNAPSHOT [#1999](https://github.com/hashgraph/hedera-mirror-node/pull/1999)
- Streamfile parsing health check [#1993](https://github.com/hashgraph/hedera-mirror-node/pull/1993)
- Upgrade to latest rosetta sdk [#1975](https://github.com/hashgraph/hedera-mirror-node/pull/1975)
- Monitor Subscriber REST API [#1937](https://github.com/hashgraph/hedera-mirror-node/issues/1937)
- Upgrade to latest Rosetta SDK [#1918](https://github.com/hashgraph/hedera-mirror-node/issues/1918)
- Helm acceptance tests [#1917](https://github.com/hashgraph/hedera-mirror-node/issues/1917)
- Support using liveness probes for importer [#1898](https://github.com/hashgraph/hedera-mirror-node/issues/1898)
- Add file processing status to importer health checks [#1892](https://github.com/hashgraph/hedera-mirror-node/issues/1892)

### Bug Fixes

- Add shebang to fix husky pre-commit hook on windows [#2062](https://github.com/hashgraph/hedera-mirror-node/pull/2062)
- Optimize rest account balance query [#2060](https://github.com/hashgraph/hedera-mirror-node/pull/2060)
- fix semantic error for openapi.yml [#2053](https://github.com/hashgraph/hedera-mirror-node/pull/2053)
- Git pre-commit fails on windows [#2052](https://github.com/hashgraph/hedera-mirror-node/issues/2052)
- Rename windows-incompatible files [#2028](https://github.com/hashgraph/hedera-mirror-node/pull/2028)
- Git checkout fails on windows [#2027](https://github.com/hashgraph/hedera-mirror-node/issues/2027)
- Fix accounts REST API with DER encoded public key [#2026](https://github.com/hashgraph/hedera-mirror-node/pull/2026)
- Account REST API doesn't support ED25519 DER encoded prefix [#2025](https://github.com/hashgraph/hedera-mirror-node/issues/2025)
- Slow get single account balance and token balances query [#2020](https://github.com/hashgraph/hedera-mirror-node/issues/2020)
- Fix TopicMessageServiceTest.startTimeAfterNow [#2005](https://github.com/hashgraph/hedera-mirror-node/pull/2005)
- Workaround lack of TLS support in SDK & acceptance test cleanup [#2000](https://github.com/hashgraph/hedera-mirror-node/pull/2000)

### Dependency Upgrades

- Bump aws-sdk from 2.913.0 to 2.918.0 in /hedera-mirror-rest [#2050](https://github.com/hashgraph/hedera-mirror-node/pull/2050)
- Bump eslint-plugin-import from 2.23.3 to 2.23.4 in /hedera-mirror-rest [#2049](https://github.com/hashgraph/hedera-mirror-node/pull/2049)
- Bump mathjs from 9.4.0 to 9.4.1 in /hedera-mirror-rest [#2048](https://github.com/hashgraph/hedera-mirror-node/pull/2048)
- Bump github.com/caarlos0/env/v6 from 6.6.0 to 6.6.2 in /hedera-mirror-rosetta [#2047](https://github.com/hashgraph/hedera-mirror-node/pull/2047)
- Bump jest from 26.6.3 to 27.0.3 in /hedera-mirror-rest/check-state-proof [#2046](https://github.com/hashgraph/hedera-mirror-node/pull/2046)
- Bump jest-circus from 26.6.3 to 27.0.3 in /hedera-mirror-rest [#2045](https://github.com/hashgraph/hedera-mirror-node/pull/2045)
- Bump sinon from 10.0.0 to 11.1.1 in /hedera-mirror-rest [#2044](https://github.com/hashgraph/hedera-mirror-node/pull/2044)
- Bump jest-junit from 12.0.0 to 12.1.0 in /hedera-mirror-rest [#2043](https://github.com/hashgraph/hedera-mirror-node/pull/2043)
- Bump jest from 26.6.3 to 27.0.3 in /hedera-mirror-rest [#2042](https://github.com/hashgraph/hedera-mirror-node/pull/2042)
- Bump mathjs from 9.4.0 to 9.4.1 in /hedera-mirror-rest/monitoring/monitor_apis [#2041](https://github.com/hashgraph/hedera-mirror-node/pull/2041)
- Bump testcontainers from 7.11.0 to 7.11.1 in /hedera-mirror-rest [#2040](https://github.com/hashgraph/hedera-mirror-node/pull/2040)
- Bump dependency-check-maven from 6.1.6 to 6.2.0 [#2038](https://github.com/hashgraph/hedera-mirror-node/pull/2038)
- Bump software.amazon.awssdk:bom from 2.16.63 to 2.16.74 [#2037](https://github.com/hashgraph/hedera-mirror-node/pull/2037)
- Bump protobuf-java from 3.17.0 to 3.17.1 [#2036](https://github.com/hashgraph/hedera-mirror-node/pull/2036)
- Bump commons-io from 2.8.0 to 2.9.0 [#2035](https://github.com/hashgraph/hedera-mirror-node/pull/2035)
- Bump ws from 7.4.4 to 7.4.6 in /hedera-mirror-rest/check-state-proof [#2034](https://github.com/hashgraph/hedera-mirror-node/pull/2034)
- Bump spring-cloud-dependencies from 2020.0.2 to 2020.0.3 [#2033](https://github.com/hashgraph/hedera-mirror-node/pull/2033)
- Bump springdoc-openapi-webflux-ui from 1.5.8 to 1.5.9 [#2032](https://github.com/hashgraph/hedera-mirror-node/pull/2032)
- Bump ws from 7.4.0 to 7.4.6 in /hedera-mirror-rest/monitoring/monitor_apis [#2031](https://github.com/hashgraph/hedera-mirror-node/pull/2031)
- Bump ws from 7.4.0 to 7.4.6 in /hedera-mirror-rest [#2030](https://github.com/hashgraph/hedera-mirror-node/pull/2030)
- Bump aws-sdk from 2.907.0 to 2.913.0 in /hedera-mirror-rest [#2019](https://github.com/hashgraph/hedera-mirror-node/pull/2019)
- Bump browserslist from 4.16.3 to 4.16.6 in /hedera-mirror-rest/check-state-proof [#2018](https://github.com/hashgraph/hedera-mirror-node/pull/2018)
- Bump grpc.version from 1.37.1 to 1.38.0 [#2013](https://github.com/hashgraph/hedera-mirror-node/pull/2013)
- Bump reporting-plugin from 4.0.102 to 4.0.103 [#2012](https://github.com/hashgraph/hedera-mirror-node/pull/2012)
- Bump docker-maven-plugin from 0.35.0 to 0.36.0 [#2011](https://github.com/hashgraph/hedera-mirror-node/pull/2011)
- Bump `@godaddy`/terminus from 4.7.2 to 4.8.0 in /hedera-mirror-rest [#2010](https://github.com/hashgraph/hedera-mirror-node/pull/2010)
- Bump testcontainers from 7.10.0 to 7.11.0 in /hedera-mirror-rest [#2009](https://github.com/hashgraph/hedera-mirror-node/pull/2009)
- Bump eslint-plugin-import from 2.23.2 to 2.23.3 in /hedera-mirror-rest [#2007](https://github.com/hashgraph/hedera-mirror-node/pull/2007)
- Bump github.com/caarlos0/env/v6 from 6.5.0 to 6.6.0 in /hedera-mirror-rosetta [#2001](https://github.com/hashgraph/hedera-mirror-node/pull/2001)

### Contributors

We'd like to thank all the contributors who worked on this release!

- [@si618](https://github.com/si618)
- [@hedera-github-bot](https://github.com/hedera-github-bot)
- [@xin-hedera](https://github.com/xin-hedera)
- [@Nana-EC](https://github.com/Nana-EC)
- [@dependabot[bot]](https://github.com/apps/dependabot)
- [@ijungmann](https://github.com/ijungmann)
- [@steven-sheehy](https://github.com/steven-sheehy)
- [@safinbot](https://github.com/safinbot)

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
