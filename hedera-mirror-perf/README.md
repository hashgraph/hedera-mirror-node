# Load Testing of Hedera Mirror Node

This code runs performance load tests against Mirror Node Modules.

## Overview

For many of our scenarios we have goals to support a high number of transactions and users.
In additional we want to incorporate testing especially of the load/stress kind early on in the development cycle.
As such this module aims to build customizable code that allows for easy runs of complex scenarios testing the extents of our services and underlying architecture components.

## jMeter

jMeter is an Apache developed load testing tool. It is now open source and java based and boasts over 15 of development and community contribution and usage.
Is support HTTP, Web services, JDBC and other process and connection testing. It also provides plugin extensibility to support more complex application e.g. gRPC service testing.
It thus provides one option for a single load testing framework. Another comparable option is Gatling.

The basis of the flow utilizes the JavaSampler Client API.
Each module will implement a Client that extends the AbstractJavaSamplerClient class to setup load tests.
It then calls a Sampler that contains business logic to make requests out to desired service e.g. http request, sql script
jMeter is then abel to take the built versions of this and orchestrate them in any number of combination of threads and loops to simulate stress testing scenarios for user behaviour.
Then using the jmeter maven plugin (https://github.com/jmeter-maven-plugin/jmeter-maven-plugin) we are able to incorporate the jMeter tests into the build flow.

### Test Creation

-   Build module for testing e.g. .mvnw clean install -DskipTests
-   Copy test jar (e.g hedera-mirror-grpc-hcs-0.1.0-rc1-tests.jar) to jmeter /lib/ext folder. If jmeter was installed using brew (recommend for OSX) /lib/ext folder can be found under /usr/local/Cellar/jmeter/<version>/libexec/lib/ext
-   Copy necessary module external dependencies to /lib/ext also (See individual module secions for list)
-   Open jMeter with 'open /usr/local/bin/jmeter' from terminal
-   Add a Thread Group and then a Java Request in jMeter. Following this select the test jar loaded and specify desired test params. Logic follows - https://jmeter.apache.org/usermanual/component_reference.html#Java_Request
-   Fill in client properties as appropriate for run e.g. host
-   Save test and copy resulting .jmx file to hedera-mirror-perf/src/test/jmeter/ location

gRPC External Dependencies to copy to /lib/ext

-             whatalokation-grpc-client.jar, r2dbc-spi-0.8.0.RELEASE.jar, spring-data-r2dbc-1.0.0.RELEASE.jar, spring-data-relational-1.1.1.RELEASE.jar

## Test Execution

-   Ensure desired .jmx file(s) are under hedera-mirror-perf/src/test/jmeter/
-   Startup database and necessary module grpc/restapi modules, either through docker-compose or through spring-boot
-   Run 'mvn -U clean verify' under hedera-mirror-perf/ module to kickoff the verify stage in the perf module where the performance tests are run

## Requirements

-   [ ] jMeter

## Contributing

Refer to [CONTRIBUTING.md](CONTRIBUTING.md)

## License

Apache License 2.0, see [LICENSE](LICENSE).
