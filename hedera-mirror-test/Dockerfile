# Containerizes hedera-mirror-node tests that can be run against various endpoints without a local repo
# docker build -f hedera-mirror-test/Dockerfile ./hedera-mirror-test -t gcr.io/mirrornode/hedera-mirror-test:latest
FROM adoptopenjdk:11-jdk-hotspot

RUN apt-get update \
    && apt-get install -y --no-install-recommends git

RUN mkdir /usr/etc

WORKDIR /usr/etc

ENV branch master
RUN git clone -b $branch https://github.com/hashgraph/hedera-mirror-node.git

WORKDIR /usr/etc/hedera-mirror-node

#RUN ./mvnw package -DskipTests -Djib.skip -Ddocker.skip --no-transfer-progress \
#    --show-version -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn

ENV testProfile acceptance
ENV cucumberFlags "@BalanceCheck"
ENV subscribeThreadCount 30
ENV jmeterTestPlan E2E_Subscribe_Only.jmx

COPY . hedera-mirror-test

#RUN rm -rf hedera-mirror-test
#RUN mkdir hedera-mirror-test
#COPY . hedera-mirror-test

#COPY pom.xml hedera-mirror-test
#RUN mkdir hedera-mirror-test/scripts
#COPY scripts/run-tests.sh hedera-mirror-test/scripts/run-tests.sh

ENTRYPOINT hedera-mirror-test/scripts/run-tests.sh

# docker run -d -e operatorid=<opId> -e operatorkey=<opKey> gcr.io/mirrornode/hedera-mirror-test:latest
