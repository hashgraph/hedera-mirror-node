#!/bin/bash
set -euo pipefail

parent_path="$( cd "$(dirname "${BASH_SOURCE[0]}")" ; pwd -P )"
cd "${parent_path}"
network="${1:-demo}"

case $network in
  demo)
    api=Data
    check="check:data"
    config="./${network}/validate-from-genesis.json"
    ;;
  testnet)
    api=Construction
    check="check:construction"
    config="./${network}/validate-construction.json"
    ;;
  *)
    echo "Unsupported network ${network}"
    exit 1
    ;;
esac

if (./rosetta-cli 2>&1 | grep -q 'CLI for the Rosetta API'); then
    echo "Rosetta CLI already installed. Skipping installation"
else
    echo "Installing Rosetta CLI"
    curl -sSfL https://raw.githubusercontent.com/coinbase/rosetta-cli/master/scripts/install.sh | sh -s -- -b .
fi

echo "Running Rosetta $api API Validation with $network Network"

if (! ./rosetta-cli ${check} --configuration-file="$config"); then
    echo "Failed to Pass API Validation"
    exit 1
fi

echo "Rosetta Validation Passed Successfully!"
