#!/bin/bash
./wait-for-postgres.sh

cd rest-api
npm install
npm start
