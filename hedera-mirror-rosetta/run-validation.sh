#!/bin/bash
parent_path=$( cd "$(dirname "${BASH_SOURCE[0]}")" ; pwd -P )
cd "$parent_path"
echo Getting Rosetta CLI...
# Temporarily using Git Clone instead of Go Get. New version with support of start & end indexes is not released yet
git clone https://github.com/coinbase/rosetta-cli.git
cd ./rosetta-cli || exit 1
network="$1"
function run_from_genesis() {
    echo Running Rosetta Data API Validation \#1
    if ! go run main.go check:data --configuration-file=./../validation/$network/validate-from-genesis.json; then
        echo Failed to Pass API Validation \#1
        exit 1
    fi
}
function run_demo() {
    echo Running DEMO Validation
    run_from_genesis
    echo Running Rosetta Data API Validation \#2
    if ! go run main.go check:data --configuration-file=./../validation/$network/validate-from-block-10.json; then
        echo Failed to Pass API Validation \#2
        exit 1
    fi
}
function run_testnet() {
    echo Running TESTNET Validation
    run_from_genesis
}
case $network in
    "testnet")
        run_testnet
    ;;
    *)
        network="demo"
        run_demo
    ;;
esac
echo Rosetta Validation Passed Successfully!
