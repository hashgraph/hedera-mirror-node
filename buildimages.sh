#!/bin/bash
set -e

rm -rf docker/runtime/{sql,rest-api}
mkdir -p docker/runtime/{config,lib,rest-api,sql}

DOCOMPILE=3
echo "Compile source via 1-docker-compose, 2-local maven, 3-skip?"
select comp in "Docker" "Local" "Skip"; do
    case $comp in
        Docker )
          DOCOMPILE=1
          break
          ;;
        Local )
          DOCOMPILE=2
          break
          ;;
        Skip )
          DOCOMPILE=3
          break
          ;;
    esac
done

if [ $DOCOMPILE -eq 1 ]
then
  docker-compose up
fi

if [ $DOCOMPILE -eq 2 ]
then
  mvn install -DskipTests
fi

cd docker

cp ../src/main/resources/postgres/V*.sql runtime/sql
cp -r ../config/* runtime/config/
cp -r ../target/lib/* runtime/lib
cp ../target/mirrorNode.jar runtime/mirrorNode.jar
cp -r ../rest-api/* runtime/rest-api

cp ./restapi.sh runtime/
chmod +x runtime/restapi.sh

cp .env runtime/rest-api/.env

DOUPDATE=1

touch runtime/.102env
cat /dev/null > runtime/.102env

echo "Would you like to update the address book file (0.0.102) from the network (enter 1 or 2)?"
select yn in "Yes" "No"; do
    case $yn in
        Yes )
          break
          ;;
        No )
          DOUPDATE=0
          break
          ;;
    esac
done

if [ $DOUPDATE -eq 1 ]
then
  echo "Input node address (x.x.x.x:port)"
  read NODE_ADDRESS
  echo "NODE_ADDRESS=${NODE_ADDRESS}" >> runtime/.102env

  echo "Input node ID (0.0.x)"
  read NODE_ID
  echo "NODE_ID=${NODE_ID}" >> runtime/.102env

  echo "Input operator ID (0.0.x)"
  read OPERATOR_ID
  echo "OPERATOR_ID=${OPERATOR_ID}" >> runtime/.102env

  echo "Input operator key (302....)"
  read OPERATOR_KEY
  echo "OPERATOR_KEY=${OPERATOR_KEY}" >> runtime/.102env
fi

docker-compose build
docker-compose up

rm -f docker/runtime/.102env

cd ..
