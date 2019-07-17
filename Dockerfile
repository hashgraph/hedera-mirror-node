# Pull base image.
FROM ubuntu:latest

RUN \
# Update
apt-get update -y && \
# Install Java
apt-get install default-jre -y

RUN mkdir -p /config
RUN mkdir -p /lib

ADD ./target/lib ./lib
ADD ./target/mirrorNode.jar mirrorNode.jar

# nodes configuration
ADD ./target/0.0.102 ./config/0.0.102
ADD ./target/nodesInfo.json ./config/nodesInfo.json

# general mirror node configuration
ADD ./target/config.json ./config/config.json

# log configuration
ADD ./target/log4j2.xml log4j2.xml

EXPOSE 8080

CMD java -Dlog4j.configurationFile=./log4j2.xml -jar mirrorNode.jar ./config/config.json