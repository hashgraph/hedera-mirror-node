# 1. E2E Acceptance Testing

This section covers the E2E testing strategy employed by the mirror node for key scenarios

## Overview

In an effort to quickly confirm product capability during deployment windows, we desired to have E2E tests that would allow us to confirm functionality on core scenarios that spanned the main and mirror networks interactions.
HCS specifically is a key scenario where transactions are submitted to the main network, the mirror node parser ingests these to the DB and the mirror node GRCP endpoint is subscribed to to obtain messages verifying transactions.
HTS is another key scenerio where transactions are submitted to the main network, the mirror node parser ingests these to the DB, and the mirror node REST transaction endpoint is periodically hit to verify transactions.
This E2E suite gives us the ability to execute scenarios as external users would and gain the required confidence during development cycles.

To achieve this the tests utilize the Hedera Java SDK under the hood - https://github.com/hashgraph/hedera-sdk-java

## Cucumber

A BDD approach was desired for our E2E test strategy as it would ensure we more closely tracked valid customer scenarios.
Cucumber is one framework that provides tools to follow this methodology. One benefit being that tests can be written in human readable text. This allows developers, PM's and designers to formulate tests that have connected code to run valid customer scenarios.
Cucumber uses the Gherkin plain language parser to describe tests.
Further details may be explored at https://cucumber.io/. Additionally, cucumbers BDD approach is explained here https://cucumber.io/docs/bdd/

### Requirements

-   Java (JDK 11 and above recommended), can follow https://sdkman.io/install for instructions

### Acceptance Test Execution

Tests can be compiled and run by running the following command from the root folder

    `./mvnw clean integration-test --projects hedera-mirror-test/ -P=acceptance-tests`

### Test Configuration

-   Test Run Config Properties: Configuration properties are set in the `application-default.yml` file located under `/src/test/resources` utilizing the spring boot application context for DI logic. Properties include

    -   emitBackgroundMessages - Flag to set if background messages should be emitted. For OPS use in non production environments
    -   existingTopicNum - a preexisting default topic number that can be used when no topicId is specified in a test. Used initially by @SubscribeOnly test
    -   maxTinyBarTransactionFee - The maximum transaction fee you're willing to pay on a transaction
    -   messageTimeout - number of seconds to wait on messages representing transactions (default is 20)
    -   mirrorNodeAddress - mirror node grpc server (refer to https://docs.hedera.com/guides/docs/mirror-node-api/hedera-consensus-service-api-1)
    -   network - The desired Hedera network environment to point to. Options currently include MAINNET, PREVIEWNET, TESTNET (default) and OTHER. Set to OTHER to point to a custom environment.
    -   nodes - A map of custom nodes to be used by SDK. This is made up of accountId (e.g. 0.0.1000) and host (e.g. 127.0.0.1) key-value pairs
    -   operatorId - account id on network 'x.y.z' format
    -   operatorKey - account private key, to be used for signing transaction and client identification #Be careful with showing this, do not check this value in.
    -   restPollingProperties
        -   baseUrl - The host url to the mirrorNode e.g. https://testnet.mirrornode.hedera.com/api/v1
        -   delay - The time to wait in between failed REST API calls
        -   maxAttempts - The maximum number of attempts when calling a REST API endpoint and receiving a 404
    -   retrieveAddressBook - Whether to download the address book from the network and use those nodes over the default nodes. Populating `hedera.mirror.test.acceptance.nodes` will take priority over this.
    -   subscribeRetries - number of times client should retryable on supported failures
    -   subscribeRetryOffPeriod - number of milliseconds client should wait before retrying on a retryable failure

(Recommended) Options can be set by creating your own configuration file with the above properties. This allows for multiple files per env. The following command will help to point out which file to use
`./mvnw integration-test --projects hedera-mirror-test/ -P=acceptance-tests -Dcucumber.filter.tags="@Acceptance" -Dspring.config.name=application-testnet`

Options can also be set through the command line as follows

    `./mvnw integration-test --projects hedera-mirror-test/ -P=acceptance-tests -Dhedera.mirror.test.acceptance.nodeId=0.0.4 -Dhedera.mirror.test.acceptance.nodeAddress=1.testnet.hedera.com:50211`

#### Custom nodes
In some scenarios you may want to point to nodes not yet captured by the SDK, a subset of published nodes, or custom nodes for a test environment.
To achieve this you can specify a list of accountId and host key-value pairs in the `hedera.mirror.test.acceptance.nodes` value of the config.
These values will always take precedence over the default network map used by the SDK for an environment.
Refer to [Mainnet Nodes](https://docs.hedera.com/guides/mainnet/mainnet-nodes) and [Testnet Nodes](https://docs.hedera.com/guides/testnet/testnet-nodes) for the published list of nodes.

The following example shows how you might specify a set of hosts to point to. Modify the accountId and host values as needed

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
-   Tags : Tags allow you to filter which cucumber scenarios and files are run. By default tests marked with the @Sanity tag are run. To run a different set of files different tags can be specified
    -   Acceptance test cases

*                                   `./mvnw clean integration-test --projects hedera-mirror-test/ -P=acceptance-tests -Dcucumber.filter.tags="@Acceptance"`
    -   All cases
*                                   `./mvnw clean integration-test --projects hedera-mirror-test/ -P=acceptance-tests -Dcucumber.filter.tags="@FullSuite"`
    -   Negative cases
*                                   `./mvnw clean integration-test --projects hedera-mirror-test/ -P=acceptance-tests -Dcucumber.filter.tags="@Negative"`
    -   Edge cases
*                                   `./mvnw clean integration-test --projects hedera-mirror-test/ -P=acceptance-tests -Dcucumber.filter.tags="@Edge"`
    -   ... (search for @? tags within the .feature files for further tags)
*                                     `./mvnw integration-test --projects hedera-mirror-test/ -P=acceptance-tests  -Dcucumber.filter.tags="@BalanceCheck"`
    -   Account check case

### Test Layout

The project layout encompasses the Cucumber Feature files, the Runner file(s) and the Step files

-   Feature Files : These are located under 'src/test/resources/features/' folder and are files of the \*.feature format. These files contain the Gherkin based language that describes the test scenarios.
-   Step Files : These are java classes located under 'src/test/java/com/hedera/mirror/test/e2e/acceptance/steps'. Every 'Given', 'When', 'And', and 'Then' keyword line in the .feature file has a matching step method that implements its logic. Feature files scenarios and Step file method descriptions must be kept in sync to avoid mismatch errors.
-   Runner Files : Currently a single Runner file is used at 'src/test/java/com/hedera/mirror/test/e2e/acceptance/AcceptanceTest.java'. This file also specifies the CucumberOptions such as 'features', 'glue' and 'plugin' that are used to connect all the files together.

### Test Creation

To create a new test/scenario follow these steps

1. Update an existing .feature file or create a new .feature file under 'src/test/resources/features/' with your desired scenario. Describe your scenario with a 'Given' setup, a 'When' execution and a 'Then' validation step. The 'When' and 'Then' steps would be the expected minimum for a meaningful scenario.
2. Update an existing step file or create a new step file with the corresponding java method under 'src/test/java/com/hedera/mirror/test/e2e/acceptance/steps' that will be run. Note method Cucumber attribute text must match the feature file.

# 2. Performance Testing

This code runs performance load tests against Mirror Node Modules.

## Overview

For many of our scenarios we have goals to support a high number of transactions and users.
In addition, we want to incorporate load testing early on in the development cycle.
As such this module aims to build customizable code that allows for easy runs of complex scenarios testing the extents of our services and underlying architecture components.

## Apache JMeter

[JMeter](https://jmeter.apache.org) is an Apache developed load testing tool. It is open source and java based and boasts over 15 years of development and community contribution and usage.
It supports HTTP, Web services, JDBC and other process and connection testing. It also provides plugin extensibility to support more complex application (e.g. gRPC service testing).
It thus provides one option for a single load testing framework. Another comparable option is Gatling.

The basis of the testing flow utilizes the JavaSampler Client API.
Each module will implement a Client that extends the AbstractJavaSamplerClient class to setup load tests.
It then calls a Sampler that contains business logic to make requests out to desired service (e.g. HTTP request, SQL script).
JMeter is then able to take the built versions of these and orchestrate them in any number of combination of threads and loops to simulate stress testing scenarios for user behaviour.
Then using the [JMeter Maven Plugin](https://github.com/jmeter-maven-plugin/jmeter-maven-plugin) we are able to incorporate the JMeter tests into the build flow.

### Test Creation

#### Requirements

-   [JMeter](https://jmeter.apache.org/download_jmeter.cgi): (On macOS `brew install jmeter`)

### Setup

-   Build module for testing:

    `../mvnw clean install -DskipTests`

-   Copy test jar to JMeter `/lib/ext` folder:

    `cp target/hedera-mirror-grpc-*-tests.jar /usr/local/Cellar/jmeter/5.2.1/libexec/lib/ext/`

-   Copy necessary module external dependencies:

    `cp whatalokation-grpc-client.jar r2dbc-spi-0.8.0.RELEASE.jar spring-data-r2dbc-1.0.0.RELEASE.jar spring-data-relational-1.1.1.RELEASE.jar /usr/local/Cellar/jmeter/5.2.1/libexec/lib/ext/`

-   Start JMeter GUI:

    `open /usr/local/bin/jmeter`

-   Fill in client properties as appropriate for run (e.g. host)

-   Save test JMX file to `hedera-mirror-test/src/test/jmeter`

## Performance Test Execution

-   Ensure desired JMX file(s) are under `hedera-mirror-test/src/test/jmeter/`
-   Startup database and module, either through Docker Compose or through Spring Boot:

    `'docker-compose [-f composefile.yml] up' or '../mvnw spring-boot:run'`

-   Start the tests:

    `./mvnw clean integration-test -P=performance-tests --projects hedera-mirror-test`



## Test Configuration

Using the initial basic test plan, load tests can be configured to achieve many number of scenarios concerned with historical, future messages and their combination within a given topic ID.
Historical messages are populated, and incoming future messages are simulated over time whiles a connection is established to the gRPC server to subscribe to a topic.
A specified number of threads per client perform a subscription to HCS and messages are observed and verified for validity of order.
At the end the db is cleared to ensure each test can run independently and the db state is not altered.

For Subscribe Only tests no db operations are performed.

### JMeter Configurations

Optional properties follow the 'Modifying Properties' logic (https://github.com/jmeter-maven-plugin/jmeter-maven-plugin/wiki/Modifying-Properties) and include the following that should be set at maven command line

- jmeter.test - The Test plan to run e.g. `-Djmeter.test="E2E_200_TPS_All_Messages.jmx"`
- jmeter.subscribeThreadCount - The number of threads JMeter should kick off e.g.`-Djmeter.subscribeThreadCount=17`


### Client Configurations
The test properties file is located at `hedera-mirror-test/src/test/jmeter/user.properties` and contains properties for the client regarding topic, subscribe details and wait logic. The following configurations should be set

- `hedera.mirror.test.performance.host` - This is the address of the mirror node grpc server e.g. `localhost` or `hcs.testnet.mirrornode.hedera.com`
- `hedera.mirror.test.performance.port` - This is the port of the mirror node grpc server e.g. 5600
- `hedera.mirror.test.performance.clientCount` - When running multiple clients this is the number of clients desired. This is not the same as the number of threads JMeter creates. JMeter will create the specified number of threads for each client.
- `hedera.mirror.test.performance.sharedChannel` - Whether the HCS subscription should share a grpc channel or not
- `hedera.mirror.test.performance.clientTopicId[x]` - The desired Topic num to subscribe to
- `hedera.mirror.test.performance.clientStartTime[x]` - The start time to subscribe from in seconds from EPOCH. Leaving this empty will start subscription from now. Relative subscribe times in units of seconds can be used e.g. setting `-60` will start the subscription from 60 seconds ago
- `hedera.mirror.test.performance.clientEndTime[x]` - The end time to subscribe up to in seconds from EPOCH.
- `hedera.mirror.test.performance.clientLimit[x]` - The limit to the number of messages a client would like to receive
- `hedera.mirror.test.performance.clientRealmNum[x]` - The realm number for the desired topic
- `hedera.mirror.test.performance.clientHistoricMessagesCount[x]` - The desired number of historical messages a client expects to receive
- `hedera.mirror.test.performance.clientIncomingMessageCount[x]` - The desired number of incoming messages a client expects to receive
- `hedera.mirror.test.performance.clientSubscribeTimeoutSeconds[x]` - The wait time a client will hold on for to receive expected message counts
- `hedera.mirror.test.performance.clientUseMAPI[x]` - Toggle to use the Mirror API (MAPI) endpoints under the [Hedera Java SDK](https://github.com/hashgraph/hedera-sdk-java)

HTS tests require additional properties to execute, including
-   `hedera.mirror.test.performance.recipientId` - account id on network 'x.y.z' format to receive tokens
-   `hedera.mirror.test.performance.tokenId` - token id on network 'x.y.z' format to transfer
-   `hedera.mirror.test.performance.transferAmount` - number of tokens to transfer between accounts
-   `hedera.mirror.test.performance.restBaseUrl` - the url for the REST service to request from
-   `hedera.mirror.test.performance.restMaxRetry` - the number of retries used to acquire a transaction from the REST service
-   `hedera.mirror.test.performance.restRetryBackoffMs` - the interval (in milliseconds) between retry attempts when requesting a tranasction from the REST service
-   `hedera.mirror.test.performance.expectedTransactionCount` - the minimum number of transactions needed to mark the test successful
-   `hedera.mirror.test.performance.batchRestTimeoutSeconds` - the number of seconds given to the REST sampler to receive all of the transactions from the REST service.

> **_Note:_** currently only the `E2E_Multi_Client_Subscribe_Only.jmx` test plan uses multiple clients. All others use a single client. So in most cases only hedera.mirror.test.performance.clientProperty[0]

## JMX Test Plan Structure
The initial jmx test plan files under `hedera-mirror-test/src/test/jmeter/` follow the below structure

1.  DB_Setup_Sampler - Populates historic data into the db
    -   host (db host)
    -   port (db port)
    -   dbUser (db user)
    -   dbPassword (db password)
    -   dbName (db name)
    -   topicID (HCS topic message id)
    -   realmNum (HCS topic realm num)
    -   historicMessagesCount (number of historical messages to populate)
    -   newTopicsMessageCount (number of future messages to emit per cycle)
    -   newTopicsMessageDelay (delay between message cycle emission)
    -   topicMessageEmitCycles (number of cycle to emit messages)
    -   delSeqFrom (sequence number to delete messages in a topic from)
2.  DB_Future_Sampler - Simulates incoming messages
    -   (See DB_Setup_Sampler options)
3.  Subscribe_Sampler - Subscribes and listens for messages
    -   host
    -   port
    -   limit
    -   consensusStartTimeSeconds
    -   consensusEndTimeSeconds
    -   topicID
    -   realmNum
    -   historicMessagesCount
    -   newTopicsMessageCount
    -   messagesLatchWaitSeconds
4.  DB_Cleanup_Sampler - Cleans up the db
    -   (See DB_Setup_Sampler options)

> **_Note:_** Subscribe_Only tests only cover section 3. No db operations are carried out.


# 3 Containerized Run

The hedera-mirror-test module offers a containerized distribution of the [acceptance](#acceptance-test-execution) and [performance](#performance-test-execution) tests.

## Kubernetes Cluster Run

The repo provides 4 pre-configured Kubernetes [Jobs](https://kubernetes.io/docs/concepts/workloads/controllers/job/) specs : `hedera-mirror-test/src/test/resources/k8s/hcs-perf-publish-test.yml`, `hedera-mirror-test/src/test/resources/k8s/hcs-perf-subscribe-test.yml`, `hedera-mirror-test/src/test/resources/k8s/hcs-perf-message-submit.yml`, and `hedera-mirror-test/src/test/resources/k8s/hts-perf-publish-and-retrieve.yml`
These utilize [ConfigMaps](https://kubernetes.io/docs/concepts/configuration/configmap/) and [Secrets](https://kubernetes.io/docs/concepts/configuration/secret/) to hold the configuration values

### HCS Performance Publish
The `hedera-mirror-test/src/test/resources/k8s/hcs-perf-publish-test.yml` provides a mostly pre-configured Job and Secret to run the acceptance tests `@PublishOnly` test

    kubectl apply -f hedera-mirror-test/src/test/resources/k8s/hcs-perf-publish-test.yml

The following properties must be specified prior to deploying this specs

- `operatorid` - as described in [acceptance tests section](#acceptance-test-execution)
- `operatorkey` - as described in [acceptance tests section](#acceptance-test-execution)
- `existingTopicNum` - as described in [acceptance tests section](#acceptance-test-execution)

### HCS Performance Subscribe
The `hedera-mirror-test/src/test/resources/k8s/hcs-perf-subscribe-test.yml` provides a mostly pre-configured Job and ConfigsMap to run the performance tests `E2E_Subscribe_Only.jmx` test plan.

    kubectl apply -f hedera-mirror-test/src/test/resources/k8s/hcs-perf-subscribe-test.yml

- Job `spec.template.spec.containers.env.subscribeThreadCount` - Sets the jmeter.subscribeThreadCount variable which dictates the number of threads JMeter creates
- ConfigMap `data` section
    - `hedera.mirror.test.performance.host` - as described in [performance](#performance-test-execution)
    - `hedera.mirror.test.performance.port` - as described in [performance](#performance-test-execution)
    - `hedera.mirror.test.performance.clientCount` - as described in [performance](#performance-test-execution)
    - `hedera.mirror.test.performance.sharedChannel` - as described in [performance](#performance-test-execution)
    - `hedera.mirror.test.performance.clientTopicId[x]` - as described in [performance](#performance-test-execution)
    - `hedera.mirror.test.performance.clientStartTime[x]` - as described in [performance](#performance-test-execution)
    - `hedera.mirror.test.performance.clientEndTime[x]` - as described in [performance](#performance-test-execution)
    - `hedera.mirror.test.performance.clientLimit[x]` - as described in [performance](#performance-test-execution)
    - `hedera.mirror.test.performance.clientRealmNum[x]` - as described in [performance](#performance-test-execution)
    - `hedera.mirror.test.performance.clientHistoricMessagesCount[x]` - as described in [performance](#performance-test-execution)
    - `hedera.mirror.test.performance.clientIncomingMessageCount[x]` - as described in [performance](#performance-test-execution)
    - `hedera.mirror.test.performance.clientSubscribeTimeoutSeconds[x]` - as described in [performance](#performance-test-execution)
    - `hedera.mirror.test.performance.clientUseMAPI[x]` - as described in [performance](#performance-test-execution)

### HTS Performance Tests
The `hedera-mirror-test/src/test/resources/k8s/hts-perf-publish-and-retrieve.yml` provides a mostly pre-configured Job and ConfigsMap to run the performance tests `Token_Transfer_Publish_And_Retrieve.jmx` test plan.

    kubectl apply -f hedera-mirror-test/src/test/resources/k8s/hts-perf-publish-and-retrieve.yml

The `hedera-mirror-test/src/test/resources/k8s/hts-perf-batch-publish-batch-validate.yml` provides a mostly pre-configured Job and ConfigsMap to run the performance tests `Token_Transfer_Publish_Batch_Validate_Batch.jmx` test plan.

    kubectl apply -f hedera-mirror-test/src/test/resources/k8s/hts-perf-batch-publish-batch-validate.yml


The following properties must be specified prior to deploying these specs

- `operatorid` - as described in [acceptance tests section](#acceptance-test-execution)
- `operatorkey` - as described in [acceptance tests section](#acceptance-test-execution)
- `recipientId` - as described in [acceptance tests section](#acceptance-test-execution)
- `tokenId` - as described in [acceptance tests section](#acceptance-test-execution)


> **_Note_** based on your test case you may need to specify more than one environment variable under `spec.template.spec.containers.env`

Refer to the [acceptance tests section](#acceptance-test-execution) and [performance](#performance-test-execution) for more details on configuration options



## Local run

The hedera-mirror-test image is deployed and available in the `gcr.io/mirrornode` repository. However, in the case of localized changes need to be tests you can build and run the tests as follows
### Image creation

    docker build -f scripts/test-container/Dockerfile . -t gcr.io/mirrornode/hedera-mirror-test:<project-version>

Run the following commands to configure a docker container to run tests

### Image run (Acceptance tests)

    docker run -d -e testProfile=acceptance -e cucumberFlags="@SubscribeOnly" \
        -v <host-application-yml>:/usr/etc/hedera-mirror-node/hedera-mirror-test/src/test/resources/application-default.yml \
        gcr.io/mirrornode/hedera-mirror-test:<project-version>

Refer to the [acceptance tests section](#acceptance-test-execution) for more details on configuration

### Image run (Performance tests)

    docker run -d -e testProfile=performance --e subscribeThreadCount=30 -e jmeter.test=E2E_Subscribe_Only.jmx \
        #-v <host-user.properties>:/usr/etc/hedera-mirror-node/hedera-mirror-test/src/test/jmeter/user.properties \
        #gcr.io/mirrornode/hedera-mirror-test:<project-version>

Refer to the [performance](#performance-test-execution) for more details on configuration
