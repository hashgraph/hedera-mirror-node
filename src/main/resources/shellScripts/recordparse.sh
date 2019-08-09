#!/bin/bash
HOME=/home/greg/mirrornode
PIDFILE=$HOME/pid/recordparse.pid

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

echo "started record parsing"
cd $HOME
java -Dlog4j.configurationFile=./log4j2.xml -cp mirrorNode.jar com.hedera.recordFileParser.RecordFileParser
echo "ended record parsing"

rm $PIDFILE
