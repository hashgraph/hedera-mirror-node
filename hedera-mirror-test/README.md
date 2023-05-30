# Acceptance Tests

This module covers the end-to-end (E2E) testing strategy employed by the mirror node for key scenarios.

## Overview

In an effort to quickly confirm product capability during deployment windows, we desire to have E2E tests that will
allow us to confirm functionality for core scenarios that span the main and mirror networks. In general, the acceptance
tests will submit transactions to the mainnet scenario where transactions are submitted to the main network, the mirror
node importer ingests these to the database, and the client subscribes to either the mirror node gRPC or REST API to
receive results.

To achieve this the tests utilize the [Hedera Java SDK](https://github.com/hashgraph/hedera-sdk-java) under the hood.
This E2E suite gives us the ability to execute scenarios as regular clients would and gain the required confidence
during deployment.

## Cucumber

Our E2E tests use the [Cucumber](https://cucumber.io) framework, which allows them to be written in
a [BDD](https://cucumber.io/docs/bdd/) approach that ensures we closely track valid customer scenarios. The framework
allows tests to be written in a human-readable text format by way of the Gherkin plain language parser, which gives
developers, project managers, and quality assurance the ability to write easy-to-read scenarios that connect to more
complex code underneath.

### Requirements

- OpenJDK 17+

### Test Execution

Tests can be compiled and run by running the following command from the root folder:

`./gradlew :test:acceptance --info -Dcucumber.filter.tags=@acceptance`

### Test Configuration

Configuration properties are set in the `application.yml` file located under `src/test/resources`. This component
uses [Spring Boot](https://spring.io/projects/spring-boot) properties to configure the application. Available properties
include:

| Name                                                           | Default                                      | Description                                                                                               |
| -------------------------------------------------------------- | -------------------------------------------- | --------------------------------------------------------------------------------------------------------- |
| `hedera.mirror.test.acceptance.backOffPeriod`                  | 5s                                           | The amount of time the client will wait before retrying a retryable failure.                              |
| `hedera.mirror.test.acceptance.createOperatorAccount`          | false                                        | Whether to create a separate operator account to run the acceptance tests.                                |
| `hedera.mirror.test.acceptance.emitBackgroundMessages`         | false                                        | Whether background topic messages should be emitted.                                                      |
| `hedera.mirror.test.acceptance.feature.maxContractFunctionGas` | 3000000                                      | The maximum amount of gas an account is willing to pay for a contract call.                               |
| `hedera.mirror.test.acceptance.feature.sidecars`               | false                                        | Whether information in sidecars should be used to verify test scenarios.                                  |
| `hedera.mirror.test.acceptance.maxNodes`                       | 10                                           | The maximum number of nodes to validate from the address book.                                            |
| `hedera.mirror.test.acceptance.maxRetries`                     | 2                                            | The number of times client should retry mirror node on supported failures.                                |
| `hedera.mirror.test.acceptance.maxTinyBarTransactionFee`       | 5000000000                                   | The maximum transaction fee the payer is willing to pay in tinybars.                                      |
| `hedera.mirror.test.acceptance.messageTimeout`                 | 20s                                          | The maximum amount of time to wait to receive topic messages from the mirror node.                        |
| `hedera.mirror.test.acceptance.mirrorNodeAddress`              | testnet.mirrornode.hedera.com:443            | The mirror node gRPC server endpoint including IP address and port.                                       |
| `hedera.mirror.test.acceptance.network`                        | TESTNET                                      | Which Hedera network to use. Can be either `MAINNET`, `PREVIEWNET`, `TESTNET` or `OTHER`.                 |
| `hedera.mirror.test.acceptance.nodes`                          | []                                           | A map of custom consensus node with the key being the account ID and the value its endpoint.              |
| `hedera.mirror.test.acceptance.operatorBalance`                | 26000000000                                  | The amount of tinybars to fund the operator. Applicable only when `createOperatorAccount` is `true`.      |
| `hedera.mirror.test.acceptance.operatorId`                     | 0.0.2                                        | Operator account ID used to pay for transactions.                                                         |
| `hedera.mirror.test.acceptance.operatorKey`                    | Genesis key                                  | Operator ED25519 private key used to sign transactions in hex encoded DER format.                         |
| `hedera.mirror.test.acceptance.rest.baseUrl`                   | https://testnet.mirrornode.hedera.com/api/v1 | The URL to the mirror node REST API.                                                                      |
| `hedera.mirror.test.acceptance.rest.maxAttempts`               | 20                                           | The maximum number of attempts when calling a REST API endpoint and receiving a 404.                      |
| `hedera.mirror.test.acceptance.rest.maxBackoff`                | 4s                                           | The maximum amount of time to wait between REST API attempts.                                             |
| `hedera.mirror.test.acceptance.rest.minBackoff`                | 0.5s                                         | The minimum amount of time to wait between REST API attempts.                                             |
| `hedera.mirror.test.acceptance.rest.retryableExceptions`       | [java.lang.Exception]                        | A list of exception classes that will be considered for retry.                                            |
| `hedera.mirror.test.acceptance.retrieveAddressBook`            | true                                         | Whether to download the address book from the mirror node and use those nodes to publish transactions.    |
| `hedera.mirror.test.acceptance.sdk.grpcDeadline`               | 10s                                          | The maximum amount of time to wait for a grpc call to complete.                                           |
| `hedera.mirror.test.acceptance.sdk.maxAttempts`                | 100                                          | The maximum number of times the sdk should try to submit a transaction to the network.                    |
| `hedera.mirror.test.acceptance.sdk.maxNodesPerTransaction`     | 2147483647                                   | The maximum number of nodes that a transaction can be submitted to.                                       |
| `hedera.mirror.test.acceptance.startupTimeout`                 | 60m                                          | How long the startup probe should wait for the network as a whole to be healthy before failing the tests. |
| `hedera.mirror.test.acceptance.web3.baseUrl`                   | Inherits `rest.baseUrl`                      | The endpoint associated with the web3 API.                                                                |
| `hedera.mirror.test.acceptance.web3.enabled`                   | false                                        | Whether to invoke the web3 API.                                                                           |
| `hedera.mirror.test.acceptance.webclient.connectionTimeout`    | 10s                                          | The timeout duration to wait to establish a connection with the server.                                   |
| `hedera.mirror.test.acceptance.webclient.readTimeout`          | 10s                                          | The timeout duration to wait for data to be read.                                                         |
| `hedera.mirror.test.acceptance.webclient.wiretap`              | false                                        | Whether a wire logger configuration should be applied to connection calls.                                |
| `hedera.mirror.test.acceptance.webclient.writeTimeout`         | 10s                                          | The timeout duration to wait for data to be written.                                                      |

Options can be set by creating your own configuration file with the above properties. This allows for
multiple files per environment. The `spring.config.additional-location` property can be set to the folder containing
the environment-specific `application.yml`:

`./gradlew :test:acceptance --info -Dcucumber.filter.tags="@acceptance" -Dspring.config.additional-location=/etc/hedera/`

Options can also be set through the command line as follows

`./gradlew :test:acceptance --info -Dhedera.mirror.test.acceptance.nodeId=0.0.4 -Dhedera.mirror.test.acceptance.nodeAddress=1.testnet.hedera.com:50211`

#### Custom nodes

In some scenarios you may want to point to nodes not yet captured by the SDK, a subset of published nodes, or custom
nodes for a test environment. To achieve this you can specify a list of accountId and host key-value pairs in
the `hedera.mirror.test.acceptance.nodes` value of the config. These values will always take precedence over the default
network map used by the SDK for an environment. Refer
to [Mainnet Nodes](https://docs.hedera.com/guides/mainnet/mainnet-nodes)
and [Testnet Nodes](https://docs.hedera.com/guides/testnet/testnet-nodes) for the published list of nodes.

The following example shows how you might specify a set of hosts to point to. Modify the accountId and host values as
needed

```yaml
hedera:
  mirror:
    test:
      acceptance:
        network: OTHER
        nodes:
          - accountId: 0.0.3
            host: 127.0.0.1
          - accountId: 0.0.4
            host: 127.0.0.2
          - accountId: 0.0.5
            host: 127.0.0.3
          - accountId: 0.0.6
            host: 127.0.0.4
```

#### Feature Tags

Tags: Tags allow you to filter which Cucumber scenarios and files are run. By default, tests marked with
the `@acceptance` tag are run. To run a different set of files different tags can be specified.

Test Suite Tags

- `@critical` - Test cases to ensure the network is up and running and satisfies base scenarios. Total cost to run 6.5
  ℏ.
- `@release` - Test cases to verify a new deployed version satisfies core scenarios and is release worthy. Total cost to
  run 19.2 ℏ.
- `@acceptance` - Test cases to verify most feature scenarios meet customer acceptance criteria. Total cost to run 31.6
  ℏ.
- `@fullsuite` - All cases - this will require some configuration of feature files and may include some disabled tests
  that will fail on execution. Total cost to run 33.9 ℏ.

> **_NOTE:_** Any noted total costs are estimates.
> They will fluctuate with test coverage expansion, improvements and network fee schedule changes.

Feature based Tags

- `@accounts` - Crypto account focused tests.
- `@topicmessagesbase` - Simple HCS focused tests.
- `@topicmessagesfilter` - HCS focused tests wth varied subscription filters.
- `@tokenbase` - HTS focused tests.
- `@schedulebase` - Scheduled transactions focused tests.

To execute run

    ./gradlew :test:acceptance --info -Dcucumber.filter.tags="<tag name>"

> **_NOTE:_** Feature tags can be combined - See [Tag expressions](https://cucumber.io/docs/cucumber/api/). To run a
> subset of tags
>
> - `@acceptance and @topicmessagesbase` - all token acceptance scenarios
> - `@acceptance and not @tokenbase` - all acceptance except token scenarios

### Test Layout

The project layout encompasses the Cucumber Feature files, the Runner file(s) and the Step files

- Feature Files : These are located under `src/test/resources/features` folder and are files of the `.feature` format.
  These files contain the Gherkin based language that describes the test scenarios.
- Step Files : These are java classes located under `src/test/java/com/hedera/mirror/test/e2e/acceptance/steps`. Every
  `Given`, `When`, `And`, and `Then` keyword line in the `.feature` file has a matching step method that implements its
  logic. Feature files scenarios and step file method descriptions must be kept in sync to avoid mismatch errors.
- Runner Files : Currently a single runner file is used at
  `src/test/java/com/hedera/mirror/test/e2e/acceptance/AcceptanceTest.java`. This file also specifies
  the `CucumberOptions`
  such as `features`, `glue` and `plugin` that are used to connect all the files together.

### Test Creation

To create a new test/scenario follow these steps

1. Update an existing .feature file or create a new .feature file under `src/test/resources/features` with your desired
   scenario. Describe your scenario with a `Given` setup, a `When` execution and a `Then` validation step. The `When`
   and `Then` steps would be the expected minimum for a meaningful scenario.
2. Update an existing step file or create a new step file with the corresponding java method under
   `src/test/java/com/hedera/mirror/test/e2e/acceptance/steps` that will be run. Note method Cucumber attribute text
   must
   match the feature file.
