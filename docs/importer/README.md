# Importer

The importer component is responsible for downloading stream files uploaded by consensus nodes, verifying them, and
ingesting the normalized data into the database.

## Initialize Entity Balance

The importer tracks up-to-date entity balance by applying balance changes from crypto transfers. This relies on the
[InitializeEntityBalanceMigration](/hedera-mirror-importer/src/main/java/com/hedera/mirror/importer/migration/InitializeEntityBalanceMigration.java)
to set the correct initial entity balance from the latest account balance snapshot relative to the last record stream
file the importer has ingested and balance changes from crypto transfers not accounted in the snapshot. If the importer
is started with a database which doesn't meet the prerequisite (e.g., an empty database) or the entity balance is
inaccurate due to bugs, follow the steps below to re-run the migration to fix it.

1. Stop importer

2. Get the latest checksum of `InitializeEntityBalanceMigration`

   ```shell
   $ psql -h db -U mirror_node -c "select checksum from flyway_schema_history \
     where script like '%InitializeEntityBalanceMigration' order by installed_rank desc limit 1"
   ```

3. Set a different checksum (e.g., 2) for the migration and start importer

   ```yaml
   hedera:
     mirror:
       importer:
         migration:
           initializeEntityBalanceMigration:
             checksum: 2
   ```

## Performance Tests

The `RecordFileParserPerformanceTest` can be used to declaratively generate a `RecordFile` with different performance
characteristics and test how fast the importer can ingest them. To configure the performance test, populate the remote
database information and the test scenarios in an `application.yml`. Use the standard `hedera.mirror.importer.db`
properties to target the remote database. The below config is generating a mix of crypto transfer and contract calls
transactions at a combined 300 transactions per second (TPS) sustained for 60 seconds:

```yaml
hedera.mirror.importer.parser.record:
  performance:
    duration: 60s
    transactions:
      - entities: 10
        tps: 100
        type: CRYPTOTRANSFER
      - entities: 5
        tps: 200
        type: CONTRACTCALL
```

To run performance tests, use Gradle to run the `performanceTest` task. To run all performance tests omit the `tests`
parameter.

```console
./gradlew :importer:performanceTest --tests 'RecordFileParserPerformanceTest' --info
```

## Reconciliation Job

The reconciliation job verifies that the data within the stream files are in sync with each other and with the mirror
node database. This process runs once a day at midnight and produces logs, metrics, and alerts if reconciliation fails.

For each balance file, the job verifies it sums to 50 billion hbars. For every pair of balance files, it verifies the
aggregated hbar transfers in that period match what's expected in the next balance file. It also verifies the aggregated
token transfers match the token balance and that the NFT transfers match the expected NFT count in the balance file.

## Running importer for v2

For local testing the importer can be run using the following command:

```console
./gradlew :importer:bootRun --args='--spring.profiles.active=v2'
```

## Building the citus docker image

DockerFile to build a custom image to be used in v2 is located in the following folder:
```hedera-mirror-node/hedera-mirror-importer/src/main/resources/db/scripts/v2/docker```
Depending on whether you need an alpine or debian image, the Docker files are located in the named folders
respectively.

The database name to be used by citus can be provided using an environment variable as follows:

```console
docker build --build-arg DB=mirror_node --platform linux/arm64 -t mirrornodeswirldslabs/citus:11.2.0-alpine .
```

## Publishing the citus docker image
The shared `mirrornode` 1Password vault contains the credentials for the Docker Hub `mirrornodeswirldslabs` user. This includes
an access token for the CLI as well as the TOTP configuration for MFA. You can use this to authenticate with
Docker Hub:

```console
docker login --username mirrornodeswirldslabs                                                                                                                  1 ↵
Password:
```
Copy and paste the token from 1Password.
```console
Login Succeeded

Logging in with your password grants your terminal complete access to your account.
For better security, log in with a limited-privilege personal access token. Learn more at https://docs.docker.com/go/access-tokens/
```
**NOTE:** Not sure why it still issues the above message when using the token instead of the password. It is a complete
access token though.

If you instead see this:
```console
Error saving credentials: error storing credentials - err: exit status 1, out: `ID hub token does not contain email`
```
You may need to restart Docker Desktop according to this [forum post](https://forums.docker.com/t/id-hub-token-does-not-contain-email/134608/2).

Push the build image to Docker Hub.  For alpine:
```console
docker push mirrornodeswirldslabs/citus:11.2.0-alpine
```
And debian:
```console
docker push mirrornodeswirldslabs/citus:11.2.0
```
You can then see that the images have been updated in [the repository](https://hub.docker.com/repository/docker/mirrornodeswirldslabs/citus/general).
