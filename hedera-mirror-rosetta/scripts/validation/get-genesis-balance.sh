#!/bin/bash
set -euo pipefail

currency=$(cat <<EOF
{
  "symbol": "HBAR",
  "decimals": 8,
  "metadata": {
    "issuer": "Hedera"
  }
}
EOF
)
genesis_balance_query=$(cat <<EOF
with genesis_balance as (
  select account_id, balance
  from ((select min(consensus_timestamp) as timestamp from account_balance_file)) as genesis
  join crypto_transfer ct
    on ct.consensus_timestamp > genesis.timestamp
  join account_balance ab
    on ab.account_id = ct.entity_id and ab.consensus_timestamp = genesis.timestamp
  where balance <> 0
  group by account_id,balance
  order by min(ct.consensus_timestamp)
  limit 20
)
select json_agg(json_build_object('id', account_id::text, 'value', balance::text))
from genesis_balance
EOF
)
network=${1:-demo}
parent_path="$(cd "$(dirname "${BASH_SOURCE[0]}")" ; pwd -P)"

echo "localhost:5432:mirror_node:mirror_rosetta:mirror_rosetta_pass" > ~/.pgpass && chmod 0600 ~/.pgpass

SECONDS=0
while [[ $SECONDS -lt 120 ]];
do
  account_balances=$(psql -h localhost -U mirror_rosetta mirror_node -t -c "$genesis_balance_query" | sed -re 's/^[ \t]*(.*)[ \t]*$/\1/' || echo -n "")
  if [[ -n $account_balances ]]; then
    echo "account balances - $(echo "$account_balances" | jq . )"
    break
  fi

  sleep 5
done

if [[ -z $account_balances ]]; then
  echo "Failed to get genesis account balances"
  exit 1
fi

echo "$account_balances" | \
  jq --argjson currency "$currency" \
  '[.[] | .account_identifier.address=("0.0." + .id) | del(.id) ] | .[].currency=$currency' \
  > "$parent_path/$network/data_genesis_balances.json"
