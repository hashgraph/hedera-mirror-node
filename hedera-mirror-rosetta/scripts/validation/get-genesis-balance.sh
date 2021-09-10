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
select json_agg(json_build_object(
  'id', account_id::text,
  'token', tb.token_id::text,
  'decimals', t.decimals,
  'value', balance::text
))
from token_balance tb
join token t on t.token_id = tb.token_id
where tb.consensus_timestamp = :genesis_timestamp and tb.account_id in (:account_ids)
EOF
)

additional_transferred_token_query=$(cat <<EOF
with genesis_record_file as (
  select consensus_end
  from record_file
  where consensus_end > :genesis_timestamp
  order by consensus_end
  limit 1
)
select json_agg(json_build_object(
  'id', account_id::text,
  'token', tt.token_id::text,
  'decimals', t.decimals,
  'value', '0'
))
from token_transfer tt
join token t on t.token_id = tt.token_id
join genesis_record_file on tt.consensus_timestamp <= consensus_end
where tt.consensus_timestamp > :genesis_timestamp and
  tt.account_id in (:account_ids) and
  tt.token_id not in (:known_token_ids)
group by tt.account_id, tt.token_id
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
    account_balances=$(echo "$genesis_hbar_balance_query" | $psql_cmd -v genesis_timestamp="$genesis_timestamp")
    echo "account balances - $(echo "$account_balances" | jq . )"

    account_ids=$(echo "$account_balances" | jq -r '[.[].id] | join(",")')
    token_balances=$(echo "$genesis_token_balance_query" \
      | $psql_cmd -v account_ids="$account_ids" -v genesis_timestamp="$genesis_timestamp")
    echo "token_balances - $(echo "$token_balances" | jq . )"

    known_token_ids=$(echo "$token_balances" | jq -r '[.[].token] | join(",")')
    known_token_ids=${known_token_ids:-3}
    additional_token_balances=$(echo "$additional_transferred_token_query" | $psql_cmd -v account_ids="$account_ids" \
      -v genesis_timestamp="$genesis_timestamp" -v known_token_ids="$known_token_ids")
    echo "additional_token_balances - $(echo "$additional_token_balances" | jq . )"
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

token_json=$(echo "$token_balances $additional_token_balances" | \
  jq -s 'add | select (.!=null) | [.[] | .account_identifier.address=("0.0." + .id) | del(.id)
    | .currency={symbol: ("0.0." + .token), decimals: .decimals} | del(.token) | del(.decimals)]')

echo "$hbar_json $token_json" | jq -s add > "$parent_path/$network/data_genesis_balances.json"