#!/bin/bash
./wait-for-postgres.sh

java -Dlog4j.configurationFile=./config/log4j2.xml -cp mirrorNode.jar com.hedera.downloader.DownloadAndParseEventFiles
