# Containerizes hedera-mirror-node tests that can be run against various endpoints
# docker build -f scripts/test-container/Dockerfile . -t gcr.io/mirrornode/hedera-mirror-test:<project-version>
FROM adoptopenjdk:11-jdk-hotspot

RUN mkdir /usr/etc

WORKDIR /usr/etc

RUN mkdir /usr/etc/hedera-mirror-node/
COPY . /usr/etc/hedera-mirror-node/

WORKDIR /usr/etc/hedera-mirror-node

ENV testProfile acceptance
ENV cucumberFlags "@BalanceCheck"
ENV subscribeThreadCount 30
ENV jmeterTestPlan E2E_Subscribe_Only.jmx

# ensure all maven dependecies are placed ahead of test run
RUN ./mvnw install -DskipTests -Djib.skip -Ddocker.skip --no-transfer-progress \
    --batch-mode -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn

ENTRYPOINT scripts/test-container/run-tests.sh

# docker run -d -e testProfile=acceptance -e cucumberFlags="@SubscribeOnly" \
    #-v <host-application-yml>:/usr/etc/hedera-mirror-node/hedera-mirror-test/src/test/resources/application-default.yml \
    #gcr.io/mirrornode/hedera-mirror-test:<project-version>
#docker run -d -e testProfile=performance --e subscribeThreadCount=30 -e jmeter.test=E2E_Subscribe_Only.jmx \
    #-v <host-user.properties>:/usr/etc/hedera-mirror-node/hedera-mirror-test/src/test/jmeter/user.properties \
    #gcr.io/mirrornode/hedera-mirror-test:<project-version>
