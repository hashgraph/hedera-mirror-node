#!/bin/bash
set -euo pipefail

parent_path="$( cd "$(dirname "${BASH_SOURCE[0]}")" ; pwd -P )"
cd "${parent_path}"
network="${1:-demo}"

if [[ "${network}" != "demo" && "${network}" != "testnet" ]]; then
    echo "Unsupported network ${network}"
    exit 1
fi

if (./rosetta-cli 2>&1 | grep -q 'CLI for the Rosetta API'); then
    echo "Rosetta CLI already installed. Skipping installation"
else
    echo "Installing Rosetta CLI"
    curl -sSfL https://raw.githubusercontent.com/coinbase/rosetta-cli/master/scripts/install.sh | sh -s -- -b .
fi

echo "Running Rosetta Data API Validation"

if (! ./rosetta-cli check:data --configuration-file="./${network}/validate-from-genesis.json"); then
    echo "Failed to Pass API Validation"
    exit 1
fi

echo "Rosetta Validation Passed Successfully!"
