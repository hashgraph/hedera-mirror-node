FROM adoptopenjdk:11-jdk-hotspot

ENV cucumberFlags "@critical"
WORKDIR /usr/etc/hedera-mirror-node

COPY .mvn/ .mvn/
COPY mvnw .
COPY pom.xml .
COPY hedera-mirror-common/pom.xml hedera-mirror-common/pom.xml
COPY hedera-mirror-grpc/pom.xml hedera-mirror-grpc/pom.xml
COPY hedera-mirror-importer/pom.xml hedera-mirror-importer/pom.xml
COPY hedera-mirror-monitor/pom.xml hedera-mirror-monitor/pom.xml
COPY hedera-mirror-protobuf/pom.xml hedera-mirror-protobuf/pom.xml
COPY hedera-mirror-rest/pom.xml hedera-mirror-rest/pom.xml
COPY hedera-mirror-rosetta/pom.xml hedera-mirror-rosetta/pom.xml
COPY hedera-mirror-test/ hedera-mirror-test/
COPY hedera-mirror-web3/ hedera-mirror-web3/

# Ensure all maven dependecies are placed ahead of test run
RUN ./mvnw clean integration-test -pl hedera-mirror-test -P=acceptance-tests -Dcucumber.filter.tags=none \
  --no-transfer-progress --batch-mode

ENTRYPOINT ./mvnw integration-test -pl hedera-mirror-test -P=acceptance-tests -Dcucumber.filter.tags=${cucumberFlags} -e
