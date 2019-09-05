#!/bin/bash
set -e

rm -rf docker/runtime/{rest-api}
mkdir -p docker/runtime/{config,lib,rest-api}

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

set +e
cp -n ../config/config.json runtime/config/config.json

set -e
cp -r ../target/lib/* runtime/lib
cp ../target/mirror-node.jar runtime/mirror-node.jar
cp -r ../rest-api/* runtime/rest-api

cp ./restapi.sh runtime/
chmod +x runtime/restapi.sh

cp .env runtime/rest-api/.env

DOUPDATE=1

touch runtime/.102env
cat /dev/null > runtime/.102env

echo "Would you like to fetch or use an existing address book file (0.0.102) (enter 1, 2, 3 or 4)?"
select yn in "Yes" "Skip" "Integration-Testnet" "Public-Testnet"; do
    case $yn in
        Yes )
          break
          ;;
        Skip )
          DOUPDATE=0
          break
          ;;
        Integration-Testnet )
          DOUPDATE=2
          break
          ;;
        Public-Testnet )
          DOUPDATE=3
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
elif [ $DOUPDATE -eq 2 ]
then
  echo "Copying integration testnet address book to runtime/config/0.0.102"
  cp ../config/0.0.102-35.232.131.251-integration.net runtime/config/0.0.102
elif [ $DOUPDATE -eq 3 ]
then
  echo "Copying public testnet address book to runtime/config/0.0.102"
  cp ../config/0.0.102-testnet runtime/config/0.0.102
fi

docker-compose build
docker-compose up

rm -f docker/runtime/.102env

cd ..
