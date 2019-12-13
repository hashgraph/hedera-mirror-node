# Faker : Fake Data Generator

Faker is a tool for generating fake data for Mirror Node.
Currently, it can generate following kinds of data:

-   Entities
-   Transactions
    -   Crypto: CRYPTOCREATEACCOUNT, CRYPTOUPDATEACCOUNT, CRYPTOTRANSFER, CRYPTODELETE
    -   Files: FILECREATE, FILEAPPEND, FILEUPDATE, FILEDELETE
-   Account balances

Generating and loading data into PostgresSQL is a two step process.

1. Generate and write data to CSV files.
2. Load CSV files into Postgres using COPY command.

![Faker: Generating and loading data](faker.png)

## Running

```bash
./faker/run.sh
```

Using custom Postgres:

```bash
./faker/run.sh -pgh HOST -pgp PORT -pgu USERNAME -pgw PASSWORD -pgd DBNAME
```

**Golden data** \
To reuse an existing fake data, add `--golden DIR` arg.

## Configuration

Faker is highly configurable. It exposes many configurations which can be tweaked to control fake data's
generation and characteristics.

Faker uses [Spring Boot](https://spring.io/projects/spring-boot) properties. As as a result, you can use properties
files, YAML files, environment variables and command-line arguments to supply configuration. See the Spring Boot
[documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/spring-boot-features.html#boot-features-external-config)
for the location and order it loads configuration.

Base configuration file is `src/main/java/resources/application.yml`.

##### Types of configuration

Configurations will be of one of the following types:

-   Java primitives: int, long, string, boolean, etc
-   NumberDistribution: This configuration allows specifying a numerical distribution rather than a fixed value.
    Supported distribution types are:

    -   Constant: Configure using \
        `<configuration name>:constant: value` \
        When collecting samples, all values will be the given `value`.
    -   Range: Configure using \
        `<configuration name>:rangeMin: minValue` \
        `<configuration name>:rangeMax: maxValue` \
        When collecting samples from this distribution, a random value between `[minValue, maxValue)` will be returned.
    -   Frequency: Configure using \
        `<configuration name>:frequence[n1]: f1` \
        `<configuration name>:frequence[n2]: f2` \
        `.....` \
        When collecting samples from this distribution, `n1` will be returned roughly `f1/(f1+f2...)` times, `n2` will be
        returned roughly `f2/(f1+f2+...)` times, and so on. \
        It's important to note that the sum of frequencies should add upto either 1000, 10000, or million. So the values
        basically represent either parts-per-thousand, parts-per-ten-thousand, or parts-per-million respectively.

#### Database configurations

| Name                           | Default          | Description                                            |
| ------------------------------ | ---------------- | ------------------------------------------------------ |
| `hedera.mirror.db.host`        | 127.0.0.1        | The IP or hostname used to connect to the database     |
| `hedera.mirror.db.port`        | 5432             | The port used to connect to the database               |
| `hedera.mirror.db.name`        | mirror_node      | The name of the database                               |
| `hedera.mirror.db.username`    | mirror_node      | The username the faker uses to connect to the database |
| `hedera.mirror.db.password`    | mirror_node_pass | The database password the faker uses to connect        |
| `hedera.mirror.db.apiPassword` | mirror_api_pass  | The database password the API uses to connect          |
| `hedera.mirror.db.apiUsername` | mirror_api       | The username the API uses to connect to the database   |

#### Faker configurations

| Name                                              | Type               | Default                    | Description                                                                                                                                                                                                                         |
| ------------------------------------------------- | ------------------ | -------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `faker.totalTimeSec`                              | long               | 86400                      | Transactions for given time period are generated. Default: 1 day                                                                                                                                                                    |
| `faker.transactionsPerSecond`                     | NumberDistribution | rangeMin: 3 rangeMax: 8    | Number of transactions to generate per second                                                                                                                                                                                       |
|                                                   |                    |                            |                                                                                                                                                                                                                                     |
| Configure distribution of transactions            |                    |                            | _Values for `faker.transaction.<type>TransactionsPerThousand` should add upto 1000_                                                                                                                                                 |
| `faker.transaction.cryptoTransactionsPerThousand` | int                | 990                        | Number of crypto transactions generated per 1000 transactions                                                                                                                                                                       |
| `faker.transaction.fileTransactionsPerThousand`   | int                | 10                         | Number of file transactions generated per 1000 transactions                                                                                                                                                                         |
|                                                   |                    |                            |                                                                                                                                                                                                                                     |
| **Configure Crypto transactions**                 |                    |                            |                                                                                                                                                                                                                                     |
| `faker.transaction.crypto.numSeedAccounts`        | int                | 10000                      | When generating transactions, first 'numSeedAccounts' number of transactions will be of type CRYPTOCREATEACCOUNT only. This is to seed the system with some accounts so crypto transfer lists can sample receiver accounts ids      |
| `faker.transaction.crypto.numTransferLists`       | NumberDistribution | rangeMin: 1, rangeMax: 10  | Distribution to sample number of crypto transfers to populate in a CRYPTOTRANSFER transaction                                                                                                                                       |
| Configure distribution of Crypto transactions     |                    |                            | _Values for `faker.transaction.crypto.<type>PerThousand` should add upto 1000._                                                                                                                                                     |
| `faker.transaction.crypto.createsPerThousand`     | int                | 10                         | Number of CREATECRYPTOACCOUNT transactions generated per 1000 crypto transactions                                                                                                                                                   |
| `faker.transaction.crypto.transfersPerThousand`   | int                | 988                        | Number of CRYPTOTRANSFER transactions generated per 1000 crypto transactions                                                                                                                                                        |
| `faker.transaction.crypto.updatesPerThousand`     | int                | 1                          | Number of CRYPTOUPDATEACCOUNT transactions generated per 1000 crypto transactions                                                                                                                                                   |
| `faker.transaction.crypto.deletesPerThousand`     | int                | 1                          | Number of CRYPTODELETE transactions generated per 1000 crypto transactions                                                                                                                                                          |
|                                                   |                    |                            |                                                                                                                                                                                                                                     |
| **Configure File transactions**                   |                    |                            |                                                                                                                                                                                                                                     |
| `faker.transaction.file.numSeedFiles`             | int                | 1000                       | When generating transactions, first 'numSeedFiles' number of transactions will be of type FILECREATE only. This is to seed the system with some files so that file append/update/delete transactions have valid files to operate on |
| `faker.transaction.file.fileDataSize`             | NumberDistribution | rangeMin: 0 rangeMax: 5000 | Distribution to sample `fileData` size. Used for all file transactions                                                                                                                                                              |
| Configure distribution of File transactions       |                    |                            | _Values for `faker.transaction.file.<type>PerThousand` should add upto 1000._                                                                                                                                                       |
| `faker.transaction.file.createsPerThousand`       | int                | 800                        | Number of FILECREATE transactions generated per 1000 file transactions                                                                                                                                                              |
| `faker.transaction.file.appendsPerThousand`       | int                | 100                        | Number of FILEAPPEND transactions generated per 1000 file transactions                                                                                                                                                              |
| `faker.transaction.file.updatesPerThousand`       | int                | 80                         | Number of FILEUPDATE transactions generated per 1000 file transactions                                                                                                                                                              |
| `faker.transaction.file.deletesPerThousand`       | int                | 20                         | Number of FILEDELETE transactions generated per 1000 file transactions                                                                                                                                                              |

## Java package hierarchy

Faker's data generation logic lives in `src/main/java/com/hedera/faker`. \
Package structure is as follows:

-   `com.hedera.faker.domain`: Generates fake data at `com.hedera.mirror.domain` abstraction so that data can be directly
    loaded into Postgres database.
    -   `DomainDriver.run()` contains top-level control logic.
    -   `com.hedera.faker.domain.generators`: Generators for fake transactions and entities.
    -   `com.hedera.faker.domain.writer`: `DomainWriter` interface to output generated fake data. `PostgresCSVDomainWriter`
        implementations of it writes fake data into CSV files. If needed in future, one can implement a writer to directly
        load data into Postgres tables.
-   `com.hedera.faker.common`: Code independent of `com.hedera.mirror.domain` so that it can be shared in future with
    streams data generator which will be at abstraction of `com.hederahashgraph.api.proto.java`.
-   `com.hedera.faker.sampling`: Various sampling distributions to help mimic real world data better.

**TODO: Unit tests for com.hedera.faker**

## Future tasks

-   Generate failed transactions too. (minor)
-   Add contract transactions. (medium)
-   Mirror node streams : Generate fake streams' files to test Parser's performance. (major)
    ![Extend faker for testing performance of Parser](faker-future.png)
-   Simulation: Generate fake stream data in realtime to test Parser's steady-state performance. (major)
