i#!/bin/bash
HOME=/home/greg/mirrornode
PIDFILE=$HOME/pid/balanceparse.pid

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

echo "starting balance parsing without history"
cd $HOME
java -cp mirrorNode.jar com.hedera.balancefilelogger.BalanceFileLogger
echo "ended balance parsing without history"

rm $PIDFILE
