# Database

## Indexes

The table below documents the database indexes with the usage in APIs / services.

| Table        | Indexed Columns                              | Component   | Service                    | Description                                                                       |
|--------------|----------------------------------------------|-------------|----------------------------|-----------------------------------------------------------------------------------|
| nft          | account_id, spender, token_id, serial_number | REST API    | TBD                        | Used to query nft allowance                                                       |
| nft_transfer | consensus_timestamp                          | REST API    | `/api/v1/transactions/:id` | Used to join `nft_transfer` and the `tlist` CTE on `consensus_timestamp` equality |
| nft_transfer | token_id, serial_number, consensus_timestamp | REST API    | `/api/v1/tokens/:id/nfts/:serialNumber/transactions` | Used to query the transfer consensus timestamps of a NFT (token_id, serial_number) with optional timestamp filter |
| nft_transfer | consensus_timestamp                          | Rosetta API | `/account/balance`         | Used to calculate an account's nft token balance including serial numbers at a block |
| nft_transfer | consensus_timestamp                          | Rosetta API | `/block`                   | Used to join `nft_transfer` and `transaction` on `consensus_timestamp` equality   |
| nft_transfer | consensus_timestamp                          | Rosetta API | `/block/transaction`       | Used to join `nft_transfer` and `transaction` on `consensus_timestamp` equality   |
| transaction  | type, consensus_timestamp                    | REST API    | `/api/v1/transactions?type=:type&order=:order` | Used to retrieve transactions filtered by `type` and sorted by `consensus_timestamp` to facilitate faster by-type transaction requests |

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

* Period: September 13, 2019 to September 08, 2020
* Scope: 6949 account balance files
* Problem: Early account balances file did not respect the invariant that all transfers less than or equal to the
  timestamp of the file are reflected within that file.
* Solution: Fixed in Hedera Services in Sept 2020. Fixed in Mirror Node v0.53.0 by adding
  a `account_balance_file.time_offset` field with a value of `-1` that is used as an adjustment to the balance file's
  consensus timestamp for use when querying transfers.

### Failed Transfers in Record

* Period: September 14, 2019 to October 3, 2019
* Scope: Affected the records of 1177 transactions.
* Problem: When a crypto transfer failed due to an insufficient account balance, the attempted transfers were
  nonetheless listed in the record.
* Solution: Fixed in Hedera Services v0.4.0 late 2019. Fixed in Mirror Node in v0.53.0 by adding an `errata` field to
  the `crypto_transfer` table and setting the spurious transfers' `errata` field to `DELETE` to indicate they should be
  omitted.

### Record Missing for Insufficient Fee Funding

* Period: September 14, 2019 to September 18, 2019
* Scope: Affected the records of 31 transactions
* Problem: When a transaction over-bid the balance of its payer account as a fee payment, its record was omitted from
  the stream. When a transaction’s payer account could not afford the network fee, its record was omitted.
* Solution: Fixed in Hedera Services v0.4.0 late 2019. Fixed in Mirror Node in v0.53.0 by adding an `errata` field
  to `crypto_transfer` and `transaction` tables and inserting the missing rows with the `errata` field set to `INSERT`.
