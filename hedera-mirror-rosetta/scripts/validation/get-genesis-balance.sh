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
genesis_timestamp_query="select min(consensus_timestamp) from account_balance_file"

genesis_hbar_balance_query=$(cat <<EOF
\set ON_ERROR_STOP on
with genesis_balance as (
  select account_id, balance
  from account_balance ab
  join crypto_transfer ct
    on ct.entity_id = ab.account_id and ct.consensus_timestamp > :genesis_timestamp
  where balance <> 0 and ab.consensus_timestamp = :genesis_timestamp
  group by account_id,balance
  order by min(ct.consensus_timestamp)
  limit 20
)
select json_agg(json_build_object('id', account_id::text, 'value', balance::text))
from genesis_balance
EOF
)

genesis_token_balance_query=$(cat <<EOF
\set ON_ERROR_STOP on
select json_agg(json_build_object(
  'id', account_id::text,
  'token', tb.token_id::text,
  'decimals', t.decimals,
  'value', balance::text
))
from token_balance tb
join token t on t.token_id = tb.token_id
where tb.consensus_timestamp = :genesis_timestamp and
  tb.account_id in (:account_ids) and
  balance <> 0
EOF
)

network=${1:-demo}
parent_path="$(cd "$(dirname "${BASH_SOURCE[0]}")" ; pwd -P)"
psql_cmd="psql -h localhost -U mirror_rosetta mirror_node -t -P format=unaligned"

echo "localhost:5432:mirror_node:mirror_rosetta:mirror_rosetta_pass" > ~/.pgpass && chmod 0600 ~/.pgpass

SECONDS=0
while [[ $SECONDS -lt 120 ]];
do
  genesis_timestamp=$($psql_cmd -c "$genesis_timestamp_query")
  if [[ -n "$genesis_timestamp" ]]; then
    # get genesis hbar balances from genesis account balance file
    account_balances=$(echo "$genesis_hbar_balance_query" | $psql_cmd -v genesis_timestamp="$genesis_timestamp")
    echo "account balances - $(echo "$account_balances" | jq . )"

    # get genesis token balances from genesis account balance file
    account_ids=$(echo "$account_balances" | jq -r '[.[].id] | join(",")')
    token_balances=$(echo "$genesis_token_balance_query" \
      | $psql_cmd -v account_ids="$account_ids" -v genesis_timestamp="$genesis_timestamp")
    echo "token_balances - $(echo "$token_balances" | jq . )"

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

token_json=$(echo "$token_balances" | \
  jq 'select (.!=null) | [.[] | .account_identifier.address=("0.0." + .id) | del(.id)
    | .currency={symbol: ("0.0." + .token), decimals: .decimals} | del(.token) | del(.decimals)]')

echo "$hbar_json $token_json" | jq -s add > "$parent_path/$network/data_genesis_balances.json"