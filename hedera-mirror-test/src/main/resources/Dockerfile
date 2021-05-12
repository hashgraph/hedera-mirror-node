# Containerizes hedera-mirror-node tests that can be run against various endpoints
FROM adoptopenjdk:11-jdk-hotspot

WORKDIR /usr/etc/hedera-mirror-node
COPY . .

ENV testProfile acceptance
ENV cucumberFlags "@balancecheck"
ENV subscribeThreadCount 30
ENV jmeterTestPlan E2E_Subscribe_Only.jmx
ENV jmeterPropsDirectory "/usr/etc/hedera-mirror-test"
ENV publishThreadCount 1

# ensure all maven dependecies are placed ahead of test run
RUN ./mvnw install -DskipTests -Djib.skip -Ddocker.skip -Dskip.npm --no-transfer-progress \
    --batch-mode -pl hedera-mirror-test --also-make

ENTRYPOINT hedera-mirror-test/src/main/resources/run-tests.sh
