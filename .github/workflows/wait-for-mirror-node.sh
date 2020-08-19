#!/bin/bash

counter=0
while [  $counter -lt 120 ];
  do sleep 1
  response=$(curl -sL -w "%{http_code}" -d '{"metadata":{}}' -i "http://localhost:5700/network/list")
  http_code=$(tail -n1 <<< "$response")
  if [ "$http_code" = "200" ]
  then
    echo Mirror Node has started
    exit 0
  else
    echo Mirror Node has not started yet...
    counter=$((counter + 1))
  fi
done

echo Timed out. Mirror Node did not start
exit 1
