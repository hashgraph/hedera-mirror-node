#!/bin/sh

mkdir runtime
mkdir runtime/config

cd ..

mvn install -DskipTests

cd docker

cp -r ../postgres/postgresInit.sql .
cp -r ../config/* runtime/config/
cp -r ../target/* runtime

docker-compose up
