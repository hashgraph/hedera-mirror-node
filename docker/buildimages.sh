#!/bin/sh

mkdir runtime
mkdir runtime/config
mkdir runtime/lib
rm -rf runtime/rest-api
mkdir runtime/rest-api

cd ..

mvn install -DskipTests

cd docker

cp -r ../src/main/resources/postgres/postgresInit.sql .
cp -r ../config/* runtime/config/
cp -r ../target/lib/* runtime/lib
cp ../target/mirrorNode.jar runtime/mirrorNode.jar
cp -r ../rest-api/* runtime/rest-api

cp ./wait-for-postgres.sh runtime/
chmod +x runtime/wait-for-postgres.sh

cp ./balanceParse.sh runtime/
chmod +x runtime/balanceParse.sh

cp ./recordDownloadParse.sh runtime/
chmod +x runtime/recordDownloadParse.sh

cp ./update102.sh runtime/
chmod +x runtime/update102.sh

cp ./restapi.sh runtime/
chmod +x runtime/restapi.sh

cp .env runtime/rest-api/.env

DOUPDATE=1

touch runtime/.102env
cat /dev/null > runtime/.102env

echo "Would you like to update the 0.0.102 file from the network (enter 1 or 2)?"
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

rm -f runtime/.102env
