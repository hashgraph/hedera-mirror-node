# Containerizes hedera-mirror-node tests that can be run against various endpoints
FROM adoptopenjdk:11-jdk-hotspot

WORKDIR /usr/etc/hedera-mirror-node
COPY . .

# copy licenses out of target folder as they'll be cleared on performance test runs
RUN mv hedera-mirror-test/target/container hedera-mirror-test/container

ENV testProfile acceptance
ENV cucumberFlags "@BalanceCheck"
ENV subscribeThreadCount 30
ENV jmeterTestPlan E2E_Subscribe_Only.jmx

# ensure all maven dependecies are placed ahead of test run
RUN ./mvnw install -DskipTests -Djib.skip -Ddocker.skip --no-transfer-progress \
    --batch-mode -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn

ENTRYPOINT hedera-mirror-test/src/main/resources/run-tests.sh
