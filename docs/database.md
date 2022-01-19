# Database

## Indexes

The table below documents the database indexes with the usage in APIs / services.

| Table        | Indexed Columns                              | Component   | Service                    | Description                                                                       |
|--------------|----------------------------------------------|-------------|----------------------------|-----------------------------------------------------------------------------------|
| nft_transfer | consensus_timestamp                          | REST API    | `/api/v1/transactions/:id` | Used to join `nft_transfer` and the `tlist` CTE on `consensus_timestamp` equality |
| nft_transfer | token_id, serial_number, consensus_timestamp | REST API    | `/api/v1/tokens/:id/nfts/:serialNumber/transactions` | Used to query the transfer consensus timestamps of a NFT (token_id, serial_number) with optional timestamp filter |
| nft_transfer | consensus_timestamp                          | Rosetta API | `/account/balance`         | Used to calculate an account's nft token balance including serial numbers at a block |
| nft_transfer | consensus_timestamp                          | Rosetta API | `/block`                   | Used to join `nft_transfer` and `transaction` on `consensus_timestamp` equality   |
| nft_transfer | consensus_timestamp                          | Rosetta API | `/block/transaction`       | Used to join `nft_transfer` and `transaction` on `consensus_timestamp` equality   |

## Data Migration Between PostgreSQL Major Releases

Data needs to be migrated for PostgreSQL major release upgrade. This section documents the steps to dump the existing
data, configure the new PostgreSQL instance, and restore the data.

### Prerequisites

- Importer for the old PostgreSQL database instance is stopped
- The new PostgreSQL database instance at the same schema version
- No data in the new PostgreSQL database instance, if not sure, run the
  [cleanup script](/hedera-mirror-importer/src/main/resources/db/scripts/cleanup.sql) to clear the data
- An ubuntu virtual machine with fast network speed to both PostgreSQL database instances. The instance should also have
  enough free disk space for the database dump

### Dump Data

To dump data from the old PostgreSQL database instance, run the following commands:

```shell
mkdir -p data_dump
pg_dump -h $OLD_POSTGRESQL_DB_IP -U mirror_node \
  --format=directory \
  --no-owner \
  --no-acl \
  -j 6 \
  -a \
  -f data_dump \
  -T 'flyway*' \
  mirror_node
```

The flag `-j` sets the number of parallel dumping jobs. The value should be at least the number of cpu cores of the
PostgreSQL server and the recommended value is 1.5 times of that.

The tables specified by the flag `-T` will be excluded from the dump. Adjust the table patterns if needed.

The time to dump the whole database usually depends on the size of the largest table.

### New PostgreSQL Database Instance Configuration

The following configuration needs to be applied to the new PostgreSQL database instance to improve the write speed.

```
checkpoint_timeout = 30m
max_wal_size = 512GB
temp_file_limit = 2147483647kB
```

### Retore Data

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

Note `-j` works the same way as for `pg_dump`. The single transaction mode can't be used together with the parallel
mode. As a result, if the command is interrupted, clear the partially restored data before retry.
