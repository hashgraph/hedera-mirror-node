# Database migration from V1 to V2

## Steps for migration
Following are the prerequisites and steps for migrating V1 data to V2.
1. Create a citus cluster with enough resources(Disk, CPU and memory). Refer to the [Helm chart](/charts/hedera-mirror/ci)
2. Populate correct values for OLD_DB config in the [migration.config](/hedera-mirror-importer/src/main/resources/db/scripts/v2/migration.config) to point to the existing cloudsql instance.
3. Populate correct values for NEW_DB config in the migration.config to point to the new citus DB.
4. Run the [migration.sh](/hedera-mirror-importer/src/main/resources/db/scripts/v2/migration.sh) script.
5. Stop the [Importer](/docs/importer/README.md) process.
6. Update the Importer to point to the new citus DB and start it.

