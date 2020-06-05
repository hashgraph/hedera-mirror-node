# Containerizes hedera-mirror-node tests that can be run against various endpoints without a local repo
# docker build -f hedera-mirror-test/Dockerfile ./hedera-mirror-test -t gcr.io/mirrornode/hedera-mirror-test:latest
FROM adoptopenjdk:11-jdk-hotspot

RUN apt-get update \
    && apt-get install -y --no-install-recommends git

RUN mkdir /usr/etc

WORKDIR /usr/etc

# pull down repo and cd to folder
ENV gitbranch master
RUN git clone -branch $gitbranch https://github.com/hashgraph/hedera-mirror-node.git

WORKDIR /usr/etc/hedera-mirror-node

# pull JUnit 5 ConsoleLauncher to aid test runs
ADD https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/1.5.2/junit-platform-console-standalone-1.5.2.jar .

# maven package
RUN ./mvnw package -DskipTests -Djib.skip -Ddocker.skip --no-transfer-progress \
    --show-version -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn

ENV operatorid opid
ENV operatorkey opkey
ENV emitBackgroundMessages false
ENV messageTimeout 30s
ENV existingTopicNum 12345
ENV mirrorNodeAddress hcs.testnet.mirrornode.hedera.com:5600
ENV nodeAddress testnet
ENV nodeId 0.0.3
ENV cucumberFlags "@BalanceCheck"
ENV testProfile acceptance-tests

#ENTRYPOINT java -jar junit-platform-console-standalone-1.5.2.jar \
#           	-cp 'build/classes/java/test' \
#           	-c com.hedera.mirror.test.e2e.acceptance.steps.AccountFeature --config=cucumber.filter.tags=$cucumberFlags

ENTRYPOINT ./mvnw integration-test --projects hedera-mirror-test/ -P=$testProfile -Dcucumber.filter.tags=$cucumberFlags \
    -Dhedera.mirror.test.acceptance.emitBackgroundMessages=$emitBackgroundMessages \
    -Dhedera.mirror.test.acceptance.messageTimeout=$messageTimeout \
    -Dhedera.mirror.test.acceptance.existingTopicNum=$existingTopicNum \
    -Dhedera.mirror.test.acceptance.mirrorNodeAddress=$mirrorNodeAddress \
    -Dhedera.mirror.test.acceptance.nodeAddress=$nodeAddress \
    -Dhedera.mirror.test.acceptance.nodeId=$nodeId \
    -Dhedera.mirror.test.acceptance.operatorid=$operatorid \
    -Dhedera.mirror.test.acceptance.operatorkey=$operatorkey

# docker run -d -e operatorid=<opId> -e operatorkey=<opKey> gcr.io/mirrornode/hedera-mirror-test:latest
