# Containerizes hedera-mirror-node tests that can be run against various endpoints without a local repo
# docker build -f hedera-mirror-test/Dockerfile ./hedera-mirror-test -t gcr.io/mirrornode/hedera-mirror-test:latest
FROM adoptopenjdk:11-jdk-hotspot

RUN apt-get update \
    && apt-get install -y --no-install-recommends git

RUN mkdir /usr/etc

WORKDIR /usr/etc

# create hedera-mirror-node dir
RUN mkdir hedera-mirror-node
WORKDIR /usr/etc/hedera-mirror-node

# pull JUnit 5 ConsoleLauncher to aid test runs
ADD https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/1.5.2/junit-platform-console-standalone-1.5.2.jar .

COPY target ./target

#ENV operatorid opid
#ENV operatorkey opkey
#ENV emitBackgroundMessages false
#ENV messageTimeout 30s
#ENV existingTopicNum 12345
#ENV mirrorNodeAddress hcs.testnet.mirrornode.hedera.com:5600
#ENV nodeAddress testnet
#ENV nodeId 0.0.3
ENV cucumberFlags "@BalanceCheck"
#ENV testProfile acceptance-tests

ENTRYPOINT java -jar junit-platform-console-standalone-1.5.2.jar \
            -cp 'target' \
           	-cp 'target/test-classes' \
           	-cp 'target/test-classes/dependencies' \
#           	--select-package com.hedera.mirror.test.e2e.acceptance \
#           	--include-classname='.*'
            --scan-classpath \
#            -p "" \
#           	-c com.hedera.mirror.test.e2e.acceptance.AcceptanceTest \
           	--config=cucumber.filter.tags=$cucumberFlags

# docker run -d -e operatorid=<opId> -e operatorkey=<opKey> gcr.io/mirrornode/hedera-mirror-test:latest
