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

- OpenJDK 11+

### Maven Execution

Tests can be compiled and run by running the following command from the root folder:

`./mvnw clean integration-test --projects hedera-mirror-test/ -P=acceptance-tests`

### Image Execution

The hedera-mirror-test image is deployed and available in the `gcr.io/mirrornode` repository. However, in the case
localized changes need to be tested you can build and run the tests as follows:

```shell
$ docker build -f src/main/resources/Dockerfile . -t gcr.io/mirrornode/hedera-mirror-test:latest
$ docker run -e cucumberFlags="@subscribeonly" \
    -v $(pwd)/application.yml:/usr/etc/hedera-mirror-node/hedera-mirror-test/src/test/resources/application-default.yml \
    gcr.io/mirrornode/hedera-mirror-test:latest
```

### Test Configuration

Configuration properties are set in the `application.yml` file located under `src/test/resources`. This component
uses [Spring Boot](https://spring.io/projects/spring-boot) properties to configure the application. Available properties
under `hedera.mirror.test.acceptance` include:

- `emitBackgroundMessages` - Flag to set if background messages should be emitted. For operations use in non-production
  `environments.
- `existingTopicNum` - A pre-existing default topic number that can be used when no topicId is specified in a test. Used
  initially by `@subscribeonly` test.
- `maxTinyBarTransactionFee` - The maximum transaction fee you're willing to pay on a transaction.
- `messageTimeout` - The number of seconds to wait on messages representing transactions (default is 20).
- `mirrorNodeAddress` - The mirror node grpc server endpoint including IP address and port. Refer to
  public [documentation](https://docs.hedera.com/guides/mirrornet/hedera-mirror-node) for a list of available endpoints.
- `network` - The desired Hedera network environment to point to. Options currently include `MAINNET`, `PREVIEWNET`,
  `TESTNET` (default) and `OTHER`. Set to `OTHER` to point to a custom environment.
- `nodes` - A map of custom nodes to be used by SDK. This is made up of an account ID (e.g. 0.0.1000) and host (e.g.
  127.0.0.1) key-value pairs.
- `operatorId` - Account ID on the network in 'x.y.z' format.
- `operatorKey` - Account private key to be used for signing transaction and client identification. Please do not check
  in.
- `restPollingProperties`
  - `baseUrl` - The host URL to the mirror node. For example, https://testnet.mirrornode.hedera.com/api/v1
  - `delay` - The time to wait in between failed REST API calls.
  - `maxAttempts` - The maximum number of attempts when calling a REST API endpoint and receiving a 404.
- `retrieveAddressBook` - Whether to download the address book from the network and use those nodes over the default
  nodes. Populating `hedera.mirror.test.acceptance.nodes` will take priority over this.
- `subscribeRetries` - The number of times client should retryable on supported failures.
- `subscribeRetryOffPeriod` - The number of milliseconds client should wait before retrying a retryable failure.

(Recommended) Options can be set by creating your own configuration file with the above properties. This allows for
multiple files per env. The following command will help to point out which file to use:

`./mvnw integration-test --projects hedera-mirror-test/ -P=acceptance-tests -Dcucumber.filter.tags="@acceptance" -Dspring.config.name=application-testnet`

Options can also be set through the command line as follows

`./mvnw integration-test --projects hedera-mirror-test/ -P=acceptance-tests -Dhedera.mirror.test.acceptance.nodeId=0.0.4 -Dhedera.mirror.test.acceptance.nodeAddress=1.testnet.hedera.com:50211`

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

Tags: Tags allow you to filter which Cucumber scenarios and files are run. By default, tests marked with the @sanity tag
are run. To run a different set of files different tags can be specified

Acceptance test cases

`./mvnw clean integration-test --projects hedera-mirror-test/ -P=acceptance-tests -Dcucumber.filter.tags="@acceptance"`

All cases

`./mvnw clean integration-test --projects hedera-mirror-test/ -P=acceptance-tests -Dcucumber.filter.tags="@fullsuite"`

Negative cases

`./mvnw clean integration-test --projects hedera-mirror-test/ -P=acceptance-tests -Dcucumber.filter.tags="@negative"`

Edge cases

`./mvnw clean integration-test --projects hedera-mirror-test/ -P=acceptance-tests -Dcucumber.filter.tags="@edge"`

Other (search for @? tags within the .feature files for further tags)

`./mvnw integration-test --projects hedera-mirror-test/ -P=acceptance-tests -Dcucumber.filter.tags="@balancecheck"`

### Test Layout

The project layout encompasses the Cucumber Feature files, the Runner file(s) and the Step files

- Feature Files : These are located under 'src/test/resources/features/' folder and are files of the \*.feature format.
  These files contain the Gherkin based language that describes the test scenarios.
- Step Files : These are java classes located under 'src/test/java/com/hedera/mirror/test/e2e/acceptance/steps'. Every '
  Given', 'When', 'And', and 'Then' keyword line in the .feature file has a matching step method that implements its
  logic. Feature files scenarios and Step file method descriptions must be kept in sync to avoid mismatch errors.
- Runner Files : Currently a single Runner file is used at '
  src/test/java/com/hedera/mirror/test/e2e/acceptance/AcceptanceTest.java'. This file also specifies the CucumberOptions
  such as 'features', 'glue' and 'plugin' that are used to connect all the files together.

### Test Creation

To create a new test/scenario follow these steps

1. Update an existing .feature file or create a new .feature file under 'src/test/resources/features/' with your desired
   scenario. Describe your scenario with a 'Given' setup, a 'When' execution and a 'Then' validation step. The 'When'
   and 'Then' steps would be the expected minimum for a meaningful scenario.
2. Update an existing step file or create a new step file with the corresponding java method under '
   src/test/java/com/hedera/mirror/test/e2e/acceptance/steps' that will be run. Note method Cucumber attribute text must
   match the feature file.
