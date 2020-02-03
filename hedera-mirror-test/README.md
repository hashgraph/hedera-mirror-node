# 1. E2E Acceptance Testing

This section covers the E2E testing strategy employed by the mirror node for key scenarios

## Overview

In an effort to quickly confirm product capability during deployment windows, we desired to have E2E tests that would allow us to confirm functionality on core scenarios that spanned the main and mirror networks interactions.
HCS specifically is a key scenario where transactions are submitted to the main network, the mirror node parser ingests these to the DB and the mirror node GRCP endpoint is subscribed to to obtain messages verifying transactions.
This E2E suite gives us the ability to execute scenarios as external users would and gain the required confidence during development cycles.

## Cucumber

A BDD approach was desired for our E2E test strategy as it would ensure we more closely tracked valid customer scenarios.
Cucumber is one framework that provides tools to follow this methodology. One benefit being that tests can be written in human readable text. This allows developers, PM's and designers to formulate tests that have connected code to run valid customer scenarios.
Cucumber uses the Gherkin plain language parser to describe tests.
Further details may be explored at https://cucumber.io/. Additionally, cucumbers BDD approach is explained here https://cucumber.io/docs/bdd/

### Test Execution

Tests can be compiled and run by running the following command from the root folder

    `./mvnw clean integration-test --projects hedera-mirror-test/`

### Test Configuration

-   Test run Config Properties: Configuration properties are set in the application.yml file located under /src/test/resources utilizing the spring boot application context for DI logic. Properties include

    -   messagewaitsla - number of seconds to wait on messages representing transactions (default is 20)
    -   nodeid - main node id to submit transactions to in 'x.y.z' format (refer to https://docs.hedera.com/guides/testnet/nodes or https://docs.hedera.com/guides/mainnet/address-book)
    -   nodeaddress - node domain or IP address (refer to https://docs.hedera.com/guides/testnet/nodes or https://docs.hedera.com/guides/mainnet/address-book or set to 'testnet' or 'mainnet' for automatic sdk handling)
    -   mirrornodeaddress - mirror node grpc server (refer to https://docs.hedera.com/guides/docs/mirror-node-api/hedera-consensus-service-api-1)
    -   operatorid - account id on network 'x.y.z' format
    -   operatorkey - account private key, to be used for signing transaction and client identification #Be careful with showing this, do not check this value in.

-   Tags : Tags allow you to filter which cucumber scenarios and files are run. By default tests marked with the @Sanity tag are run. To run a different set of files different tags can be specified
    -   All test cases

*             `./mvnw clean integration-test --projects hedera-mirror-test/ -P=acceptance-tests -Dcucumber.filter.tags="@FullSuite"`
    -   Negative cases
*             `./mvnw clean integration-test --projects hedera-mirror-test/ -P=acceptance-tests -Dcucumber.filter.tags="@Negative"`
    -   Edge cases
*             `./mvnw clean integration-test --projects hedera-mirror-test/ -P=acceptance-tests -Dcucumber.filter.tags="@Edge"`
    -   ... (search for @? tags within the .feature files for further tags)

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

## Test Execution

-   Ensure desired JMX file(s) are under `hedera-mirror-test/src/test/jmeter/`
-   Startup database and module, either through Docker Compose or through Spring Boot:

    `'docker-compose [-f composefile.yml] up' or '../mvnw spring-boot:run'`

-   Start the tests:

    `./mvnw clean verify -P=integration-tests --projects hedera-mirror-test`

    Optional properties follow the 'Modifying Properties' logic (https://github.com/jmeter-maven-plugin/jmeter-maven-plugin/wiki/Modifying-Properties) and include the following

    -   jmeter.test e.g. `-Djmeter.test="E2E_200_TPS_All_Messages.jmx"`
    -   jmeter.subscribeThreadCount e.g.`-Djmeter.subscribeThreadCount=17`

## Test Configuration

Using the initial basic test plan, load tests can be configured to achieve many number of scenarios concerned with historical, future messages and their combination within a given topic ID.
Historical messages are populated, and incoming future messages are simulated over times whiles a connection is established to the gRPC server to subscribe to a topic.
At the end the db is cleared to ensure each test can run independently and the db state is not altered.

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
3.  Subcribe_Sampler - Subscribes and listens for messages
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
