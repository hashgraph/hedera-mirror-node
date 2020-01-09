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

    `../mvnw clean package -DskipTests`

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
-   Startup database and module, either through Docker Compose or through Spring Boot
-   Start the tests:

    `./mvnw clean verify --projects hedera-mirror-test`
