#!/bin/sh

mkdir runtime
mkdir runtime/config
mkdir runtime/lib

cd ..

mvn install -DskipTests

cd docker

cp -r ../src/main/resources/postgres/postgresInit.sql .
cp -r ../config/* runtime/config/
cp -r ../target/lib/* runtime/lib
cp ../target/mirrorNode.jar runtime/mirrorNode.jar
cp ./wait-for-postgres.sh runtime/
chmod +x runtime/wait-for-postgres.sh

cp ./balanceParse.sh runtime/
chmod +x runtime/balanceParse.sh

cp ./recordDownloadParse.sh runtime/
chmod +x runtime/recordDownloadParse.sh

docker-compose build
docker-compose up
