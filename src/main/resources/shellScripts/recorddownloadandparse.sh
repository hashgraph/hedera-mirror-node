#!/bin/bash
HOME=/home/greg/mirrornode
PIDFILE=$HOME/pid/recorddownloadparse.pid

if [ -f $PIDFILE ]
then
  PID=$(cat $PIDFILE)
  ps -p $PID > /dev/null 2>&1
  if [ $? -eq 0 ]
  then
    echo "Process already running"
    exit 1
  else
    ## Process not found assume not running
    echo $$ > $PIDFILE
    if [ $? -ne 0 ]
    then
      echo "Could not create PID file"
      exit 1
    fi
  fi
else
  echo $$ > $PIDFILE
  if [ $? -ne 0 ]
  then
    echo "Could not create PID file"
    exit 1
  fi
fi

echo "started record download and parsing"
cd $HOME
java -Dlog4j.configurationFile=./log4j2.xml -cp mirrorNode.jar com.hedera.downloader.DownloadAndParseRecordFiles
echo "ended record download and parsing"

rm $PIDFILE
