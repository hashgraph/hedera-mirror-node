# Database

## Indexes

The table below documents the database indexes with the usage in APIs / services.

| Table           | Indexed Columns                                    | Component   | Service                                              | Description                                                                                                                            |
|-----------------|----------------------------------------------------|-------------|------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------|
| contract_result | consensus_timestamp                                | REST API    | `/api/v1/contracts/results`                          | Used to query contract results with timestamp filter                                                                                   |
| contract_result | contract_id, payer_account_id, consensus_timestamp | REST API    | `/api/v1/contracts/:idOrAddress/results?from=:from`  | Used to query a specific contract's results with from filter                                                                           |
| contract_result | contract_id, consensus_timestamp                   | REST API    | `/api/v1/contracts/:idOrAddress/results`             | Used to query a specific contract's results with optional timestamp filter                                                             |
| contract_result | payer_account_id, consensus_timestamp              | REST API    | `/api/v1/contracts/results?from=:from`               | Used to query contract results with from address filter                                                                                |
| nft             | account_id, spender, token_id, serial_number       | REST API    | `/api/v1/accounts/:accountIdOrAlias/nfts`            | Used to query nft allowance granted by the owner account, optionally filtered by spender, token ID and/or serial number                |
| nft_transfer    | consensus_timestamp                                | REST API    | `/api/v1/transactions/:id`                           | Used to join `nft_transfer` and the `tlist` CTE on `consensus_timestamp` equality                                                      |
| nft_transfer    | token_id, serial_number, consensus_timestamp       | REST API    | `/api/v1/tokens/:id/nfts/:serialNumber/transactions` | Used to query the transfer consensus timestamps of a NFT (token_id, serial_number) with optional timestamp filter                      |
| nft_transfer    | consensus_timestamp                                | Rosetta API | `/account/balance`                                   | Used to calculate an account's nft token balance including serial numbers at a block                                                   |
| nft_transfer    | consensus_timestamp                                | Rosetta API | `/block`                                             | Used to join `nft_transfer` and `transaction` on `consensus_timestamp` equality                                                        |
| nft_transfer    | consensus_timestamp                                | Rosetta API | `/block/transaction`                                 | Used to join `nft_transfer` and `transaction` on `consensus_timestamp` equality                                                        |
| transaction     | type, consensus_timestamp                          | REST API    | `/api/v1/transactions?type=:type&order=:order`       | Used to retrieve transactions filtered by `type` and sorted by `consensus_timestamp` to facilitate faster by-type transaction requests |

## Reset

Some Hedera environments get [reset](https://docs.hedera.com/hedera/testnet#test-network-resets) periodically and mirror
nodes connected to those environments will need to be reset to remain functional.

1. Stop the [Importer](/docs/importer/README.md) process.
2. Run [cleanup.sql](/hedera-mirror-importer/src/main/resources/db/scripts/cleanup.sql) as the database owner to
   truncate the tables.

   ```bash
   psql -h ${DB_HOST} -d mirror_node -U mirror_node -f cleanup.sql
   ```

3. Update the Importer [configuration](/docs/configuration.md) to set it to the new bucket name and adjust its start
   date appropriately. For testnet, the bucket name will be in the format `hedera-testnet-streams-YYYY-MM` where
   `YYYY-MM` will change depending upon the month in which it is reset. Since testnet will be reset quarterly with
   a new bucket, the importer start date can be set to 1970 to ensure no data is missed.

   ```properties
   hedera.mirror.importer.downloader.bucketName=hedera-testnet-streams-2023-01
   hedera.mirror.importer.startDate=1970-01-01T00:00:00Z
   ```

4. Start the Importer process and ensure it prints the new bucket on startup and successfully starts syncing.
5. Restart the remaining mirror node components to clear any cached information.

## Retention

On public networks, mirror nodes can generate tens of gigabytes worth of data every day and this rate is only projected
to increase. Mirror nodes support an optional data retention period that is disabled by default. When enabled, the
retention job purges historical data beyond a configured time period. By reducing the overall amount of data in the
database it will reduce operational costs and improve read/write performance. Only data associated with balance
or transaction data is deleted. Cumulative entity information like accounts, contracts, etc. are not deleted.

To enable retention, set the `hedera.mirror.importer.retention.enabled=true` property on the importer. A job will run
every `hedera.mirror.importer.retention.frequency` with a default of one day to prune older data. To control how far
back to remove data set the `hedera.mirror.importer.retention.period` appropriately. Keep in mind this retention period
is relative to the timestamp of the last transaction in the database and not to the current wall-clock time. Data is
deleted atomically one or more blocks at a time starting from the earliest block and increasing, so data should be
consistent even when querying the earliest data. There are also `hedera.mirror.importer.retention.exclude/include`
properties that can be used to filter which tables are included or excluded from retention, defaulting to include all.

The first time the job is run it may take a long time to complete due to the potentially terabytes worth of data to
purge. Subsequent runs should be much faster as it will only have to purge the data accumulated between the last run.
The importer database user denoted by the `hedera.mirror.importer.db.username` property will need to be altered to have
delete permission if it does not already have it.

## Upgrade

Data needs to be migrated for PostgreSQL major release upgrade. This section documents the steps to dump the existing
data, configure the new PostgreSQL instance, and restore the data.

### Prerequisites

- Importer for the old PostgreSQL database instance is stopped
- The new PostgreSQL database instance
- An ubuntu virtual machine with fast network speed connections to both PostgreSQL database instances. The instance
  should also have enough free disk space for the database dump

### Backup

To dump data from the old PostgreSQL database instance, run the following commands:

```shell
mkdir -p data_dump
pg_dump -h $OLD_POSTGRESQL_DB_IP -U mirror_node \
  --format=directory \
  --no-owner \
  --no-acl \
  -j 6 \
  -f data_dump \
  mirror_node
```

The flag `-j` sets the number of parallel dumping jobs. The value should be at least the number of cpu cores of the
PostgreSQL server and the recommended value is 1.5 times of that.

The time to dump the whole database usually depends on the size of the largest table.

### New PostgreSQL Database Instance Configuration

Run [init.sh](/hedera-mirror-importer/src/main/resources/db/scripts/init.sh) or the equivalent SQL statements to create
required database objects including the `mirror_node` database, the roles, the schema, and access privileges.

The following configuration needs to be applied to the database instance to improve the write speed.

```
checkpoint_timeout = 30min
maintenance_work_mem = 2GB
max_parallel_maintenance_workers = 4
max_wal_size = 512GB
temp_file_limit = 2147483647kB
```

Note:

- Not all flags are available in managed database services. For example, `max_parallel_maintenance_workers` is not
  available in Google Cloud SQL.
- Once the data is restored, revert the values back for normal operation.

### Restore

Before restoring the data, take a database snapshot.

Use the following command to restore the data dump to the new PostgreSQL database instance:

```shell
pg_restore -h $NEW_POSTGRESQL_DB_IP -U mirror_node \
  --exit-on-error \
  --format=directory \
  --no-owner \
  --no-acl \
  -j 6 \
  -d mirror_node \
  data_dump
```

Note: `-j` works the same way as for `pg_dump`. The single transaction mode can't be used together with the parallel
mode. As a result, if the command is interrupted, the database will have partial data, and it needs to be restored using
the saved snapshot before retry.

## Errata

Some tables may contain errata information to workaround known issues with the stream files. The state of the consensus
nodes was never impacted, only the externalization of these changes to the stream files that the mirror node consumes.
There were three instances of bugs in the node software that misrepresented the side-effects of certain user
transactions in the balance and record streams. These issues should only appear in mainnet.

### Account Balance File Skew

* Period: 2019-09-13 to 2020-09-08
* Scope: 6949 account balance files
* Problem: Early account balances file did not respect the invariant that all transfers less than or equal to the
  timestamp of the file are reflected within that file.
* Solution: Fixed in Hedera Services in Sept 2020. Fixed in Mirror Node v0.53.0 by adding
  a `account_balance_file.time_offset` field with a value of `-1` that is used as an adjustment to the balance file's
  consensus timestamp for use when querying transfers.

### Failed Transfers in Record

* Period: 2019-09-14 to 2019-10-03
* Scope: Affected the records of 1177 transactions.
* Problem: When a crypto transfer failed due to an insufficient account balance, the attempted transfers were
  nonetheless listed in the record.
* Solution: Fixed in Hedera Services v0.4.0 late 2019. Fixed in Mirror Node in v0.53.0 by adding an `errata` field to
  the `crypto_transfer` table and setting the spurious transfers' `errata` field to `DELETE` to indicate they should be
  omitted.

### Record Missing for Insufficient Fee Funding

* Period: 2019-09-14 to 2019-09-18
* Scope: Affected the records of 31 transactions
* Problem: When a transaction over-bid the balance of its payer account as a fee payment, its record was omitted from
  the stream. When a transaction’s payer account could not afford the network fee, its record was omitted.
* Solution: Fixed in Hedera Services v0.4.0 late 2019. Fixed in Mirror Node in v0.53.0 by adding an `errata` field
  to `crypto_transfer` and `transaction` tables and inserting the missing rows with the `errata` field set to `INSERT`.

### Record Missing for FAIL_INVALID NFT transfers

* Period: 2022-07-31 to 2022-08-09
* Scope: Affected the records of 70 transactions.
* Problem: Any ledger that will grow to billions of entities must have an efficient way to remove expired entities. In
  the Hedera network, this means keeping a list of NFTs owned by an account, so that when an account expires, we can
  return its NFTs to their respective treasury accounts.
  Under certain conditions in the 0.27.5 release, a bug in the logic maintaining these lists could cause NFT transfers
  to fail, without refunding fees. This would manifest itself as a `FAIL_INVALID` transaction that does not get written
  to the record stream.
* Solution: Fixed in Hedera Services [v0.27.7](https://docs.hedera.com/guides/docs/release-notes/services#v0.27.7) on
  August 9th 2022. Fixed in Mirror Node in v0.64.0 by a migration that adds the missing transactions and transfers.

### Record Missing for FAIL_INVALID NFT transfers

* Period: 2023-02-10 to 2023-02-14
* Scope: Affected the records of 12 transactions.
* Problem: Fixes a bug in bookkeeping for NFT TokenWipe and TokenBurn operations with redundant serial numbers. The bug
  makes an account appear to own fewer NFTs than it actually does. This can subsequently prevent an NFT owner from being
  changed as part of an atomic operation. When the atomic operation fails, an errata record is required for the missing
  transactions.
* Solution: Fixed in Hedera Services [v0.34.2](https://github.com/hashgraph/hedera-services/releases/tag/v0.34.2) on
  February 17, 2023. Fixed in Mirror Node in v0.74.3 by a migration that adds the missing transactions.
  
## Database migration from V1 to V2

[Citus](https://github.com/citusdata/citus) is the database engine to use with the V2 schema.

The following table is the recommended configuration for a production Citus cluster. Note the storage size is an
estimation for all mainnet data since OA till 04/2023.

| Node Type   | Count | vCPU | Memory | Disk Storage |
|-------------|-------|------|--------|--------------|
| Coordinator | 3     | 4    | 26 GB  | 256 GB       |
| Worker      | 3     | 4    | 26 GB  | 5 TB         |

Following are the prerequisites and steps for migrating V1 data to V2.

1. Create a Citus cluster with enough resources (Disk, CPU and memory).
2. Populate correct values for OLD_DB config in the 
   [migration.config](/hedera-mirror-importer/src/main/resources/db/scripts/v2/migration.config) to point to the 
   existing database instance.
3. Populate correct values for NEW_DB config in the migration.config to point to the new citus DB.
4. Get the correct version of [flyway](https://flywaydb.org/documentation/usage/commandline/) based on your OS and
   update it in the FLYWAY_URL field in the migration.config file. The default is set to the macosx version.
5. Update the flyway.url field in the flyway.conf file to point to the citus db location.
6. Run the [migration.sh](/hedera-mirror-importer/src/main/resources/db/scripts/v2/migration.sh) script.
7. Stop the [Importer](/docs/importer/README.md) process.
8. Update the Importer to point to the new Citus DB and start it.