#!/bin/bash
set -euo pipefail

parent_path="$( cd "$(dirname "${BASH_SOURCE[0]}")" ; pwd -P )"
cd "${parent_path}"
network="${1}"

if (./rosetta-cli | grep -q 'CLI for the Rosetta API'); then
    echo "Rosetta CLI already installed. Skipping installation"
else
    echo "Installing Rosetta CLI"
    curl -sSfL https://raw.githubusercontent.com/coinbase/rosetta-cli/master/scripts/install.sh | sh -s -- -b .
fi

function run_from_genesis() {
    echo "Running Rosetta Data API Validation #1"
    if (! ./rosetta-cli check:data --configuration-file="./${network}/validate-from-genesis.json"); then
        echo "Failed to Pass API Validation #1"
        exit 1
    fi
}

function run_demo() {
    echo "Running DEMO Validation"
    run_from_genesis
}

function run_testnet() {
    echo "Running TESTNET Validation"
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

echo "Rosetta Validation Passed Successfully!"
