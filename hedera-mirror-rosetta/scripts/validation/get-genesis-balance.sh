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
genesis_timestamp_query="select consensus_timestamp from account_balance_file order by consensus_timestamp asc limit 2"

genesis_hbar_balance_query=$(cat <<EOF
\set ON_ERROR_STOP on
with recent_crypto_accounts as (
 select distinct(entity_id)
 from crypto_transfer where consensus_timestamp > :genesis_timestamp and consensus_timestamp <= :second_timestamp
 limit 20
),
genesis_balance as (
  select account_id, balance
  from account_balance ab
  join recent_crypto_accounts ct
    on ct.entity_id = ab.account_id
  where balance <> 0 and ab.consensus_timestamp = :genesis_timestamp
  group by account_id,balance
)
select json_agg(json_build_object('id', account_id::text, 'value', balance::text))
from genesis_balance
EOF
)

network=${1:-demo}
parent_path="$(cd "$(dirname "${BASH_SOURCE[0]}")" ; pwd -P)"
psql_cmd="psql -h localhost -U mirror_rosetta mirror_node -t -P format=unaligned"

echo "localhost:5432:mirror_node:mirror_rosetta:mirror_rosetta_pass" > ~/.pgpass && chmod 0600 ~/.pgpass

SECONDS=0
while [[ $SECONDS -lt 120 ]];
do
  first_two_timestamps=($($psql_cmd -c "$genesis_timestamp_query"))
  genesis_timestamp=${first_two_timestamps[0]}
  second_timestamp=${first_two_timestamps[1]}
  if [[ -n "$genesis_timestamp" ]]; then
    # get genesis hbar balances from genesis account balance file
    account_balances=$(echo "$genesis_hbar_balance_query" | $psql_cmd -v genesis_timestamp="$genesis_timestamp" -v second_timestamp="$second_timestamp")
    echo "account balances - $(echo "$account_balances" | jq . )"
    break
  fi

  echo "${SECONDS}s elapsed, retry getting genesis timestamp in 5 seconds"
  sleep 5
done

if [[ -z "$genesis_timestamp" ]]; then
  echo "Failed to get genesis timestamp"
  exit 1
fi

hbar_json=$(echo "$account_balances" | \
  jq --argjson currency "$currency" \
  '[.[] | .account_identifier.address=("0.0." + .id) | del(.id) ] | .[].currency=$currency')

echo "$hbar_json" > "$parent_path/$network/data_genesis_balances.json"
