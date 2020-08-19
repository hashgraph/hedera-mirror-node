#!/bin/bash
parent_path=$( cd "$(dirname "${BASH_SOURCE[0]}")" ; pwd -P )
cd "$parent_path"

echo Getting Rosetta CLI...

# Temporarily using Git Clone instead of Go Get. New version with support of start & end indexes is not released yet
git clone https://github.com/coinbase/rosetta-cli.git
cd ./rosetta-cli || exit 1

echo Running Rosetta Data API Validation \#1
if ! go run main.go check:data --configuration-file=./../validation/validate-from-genesis.json; then
    echo Failed to Pass API Validation \#1
    exit 1
fi
echo Running Rosetta Data API Validation \#2
if ! go run main.go check:data --configuration-file=./../validation/validate-from-block-10.json; then
    echo Failed to Pass API Validation \#2
    exit 1
fi

echo Rosetta Validation Passed Successfully!
