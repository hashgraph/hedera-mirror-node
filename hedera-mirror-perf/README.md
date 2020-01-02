# Load Testing of Hedera Mirror Node

This code runs performance load tests against Mirror Node Modules.

## Overview

## jMeter

#### gRPC Load Testing:

Java based ...

-   Build hedera-miror-grpc module
-   Copy hedera-mirror-grpc-hcs-0.1.0-rc1-tests.jar to jmeter /lib/ext folder. In my case i installed jmeter using brew and it could be found under /usr/local/Cellar/jmeter/5.2.1/libexec/lib/ext
-   Copy the following external dependencies
-           whatalokation-grpc-client.jar, r2dbc-spi-0.8.0.RELEASE.jar, spring-data-r2dbc-1.0.0.RELEASE.jar, spring-data-relational-1.1.1.RELEASE.jar
-   Open jMeter with 'open /usr/local/bin/jmeter' from terminal
-   Add a Thread Group and then a Java Request in jMeter. Following this select the test jar loaded and specify desired test params. Logic follow - https://jmeter.apache.org/usermanual/component_reference.html#Java_Request
-   Save test and copy resulting .jmx file to hedera-mirror-perf/src/test/jmeter/ location
-   Startup database and grpc modules, either thorugh docker-compose or through spring-boot
-   Run 'mvn -U clean verify' under hedera-mirror-perf/ module to kickoff the verify stage in the perf module where the performance tests are run

#### gRPC Load Testing Using Gatling:

#### REST API Load Testing Using jMeter:

#### JDBC Load Testing Using jMeter:

## Gatling

Scala based ...

#### gRPC Load Testing:

#### REST API Load Testing:

## Requirements

-   [ ] jMeter

## Contributing

Refer to [CONTRIBUTING.md](CONTRIBUTING.md)

## License

Apache License 2.0, see [LICENSE](LICENSE).
