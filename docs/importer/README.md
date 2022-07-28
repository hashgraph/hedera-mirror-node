# Importer

The importer component is responsible for downloading stream files uploaded by consensus nodes, verifying them, and
ingesting the normalized data into the database.

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

To run the performance test, use Maven to execute a specific JUnit test and activate the `performance` spring profile:

```console
./mvnw test -pl hedera-mirror-importer -Dspring.profiles.active=performance -Dtest=RecordFileParserPerformanceTest -Dsurefire.failIfNoSpecifiedTests=false
```

Alternatively, the below command can be used to execute the `performance` test group, but it will trigger a few
additional tests:

```console
./mvnw test -pl hedera-mirror-importer -Dspring.profiles.active=performance -Dgroups=performance
```

## Reconciliation Job

The reconciliation job verifies that the data within the stream files are in sync with each other and with the mirror
node database. This process runs once a day at midnight and produces logs, metrics, and alerts if reconciliation fails.

For each balance file, the job verifies it sums to 50 billion hbars. For every pair of balance files, it verifies the
aggregated hbar transfers in that period match what's expected in the next balance file. It also verifies the aggregated
token transfers match the token balance and that the NFT transfers match the expected NFT count in the balance file.
