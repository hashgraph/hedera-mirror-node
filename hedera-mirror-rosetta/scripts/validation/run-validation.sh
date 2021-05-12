#!/bin/bash
set -euo pipefail

function wait_for_balance() {
  echo "Wait for balance sync"

  count=0
  until [ $count -gt 60 ]
  do
    balance=$(curl -s -H "Content-Type: application/json" -H "Content-Type:application/json" -d "$data" \
                http://localhost:5700/account/balance | jq -r '.balances[0].value | values')
    echo "$account balance - $balance"
    if [ -n "$balance" ] && [ "$balance" != "0" ]; then
      return 0
    fi

    count=$((count+1))
    sleep 5
  done

  echo "Failed to get synced balance in 5 minutes"
  return 1
}

parent_path="$( cd "$(dirname "${BASH_SOURCE[0]}")" ; pwd -P )"
cd "${parent_path}"
network="${1:-demo}"
account=${2:-0.0.98}

data=$(cat <<EOM
{
  "network_identifier": {
  "blockchain": "Hedera",
  "network": "testnet",
  "sub_network_identifier": {
      "network": "shard 0 realm 0"
    }
  },
  "account_identifier": {
    "address": "$account"
  }
}
EOM
)

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

    wait_for_balance
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
