# Performance Testing

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

    `./mvnw clean verify --projects hedera-mirror-test`

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
