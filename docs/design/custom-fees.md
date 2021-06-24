# Custom Fees Design

## Purpose

[HIP-18](https://github.com/hashgraph/hedera-improvement-proposal/blob/master/HIP/hip-18.md) proposes changes to support
Custom Hedera Token Service Fees. This document explains how the mirror node can be updated to support it.

## Goals

- Ingest custom fees in TokenCreate and TokenUpdate transactions to the database
- Ingest assessed custom fees in the transaction record to the database
- Expose custom fees via REST APIs
- Expose assessed custom fees via REST APIs
- Support historical token custom fees lookup

## Non Goals

## Architecture

### Database

- Update `t_transaction_results` with new response codes

- Add to `token` table a new column `can_update_custom_fees_with_admin_key` (boolean not null)

- Add a new `custom_fee` table

```sql
create table if not exists custom_fee
(
    consensus_timestamp bigint not null,
    fixed_fees          jsonb,
    fractional_fees     jsonb,
    token_id            bigint not null
);
alter table custom_fee add primary key (consensus_timestamp, token_id);
```

- Add a new `assessed_custom_fee` table

```sql
create table if not exists assessed_custom_fee (
    amount               bigint not null,
    collector_account_id bigint not null,
    consensus_timestamp  bigint not null,
    token_id             bigint
);
create index if not exists assessed_custom_fee__consensus_timestamp
  on assessed_custom_fee (consensus_timestamp);
create index if not exists assessed_custom_fee__collector_consensus_timestamp
  on assessed_custom_fee (collector_account_id, consensus_timestamp);
```

## Importer

### Converter

- Add a `FixedFeeConverter` class to convert `FixedFee` to/from json string
- Add a `FractionalFeeConverter` class to convert `FractionalFee` to/from json string
- Add a `FractionConverter` class to convert `Fraction` to/from json string

### Domain

- Add `AssessedCustomFee` class with the following fields
  - `amount`
  - `collectorAccountId`
  - `consensusTimestamp`
  - `tokenId`

- Add `CustomFee` class with the following fields
  - `consensusTimestamp`
  - `fixedFees` of type `List<FixedFee>`
  - `fractionalFees` of type `List<FractionalFee>`
  - `tokenId`

- Add `FixedFee` class with the following fields
  - `amount`
  - `denominatingTokenId`

- Add `FractionalFee` class with the following fields
  - `amount` of type `Fraction`
  - `maximum`
  - `minimum`

- Add `Fraction` class with the following fields
  - `numerator`
  - `denominator`

### Custom Fee Parsing

#### EntityListener

- Add a `onAssessedCustomFee()` to handle inserts on the `assessed_custom_fee` table
```java
    default void onAssessedCustomFee(AssessedCustomFee assessedCustomFee) throws ImporterException {
    }
```

#### EntityRecordItemListener

- Add a function `insertAssessedCustomFees()` to insert assessed custom fees in a transaction record
- Update `insertTokenCreate` and `insertTokenUpdate` to insert a token's custom fees

## REST API

### Transactions Endpoint

- Update `/api/v1/transactions` response to add assessed custom fees. Note if a assessed custom fee doesn't have a
  `token_id`, it's charged in HBAR; otherwise it's charged in the `token_id`

```json
    {
      "transactions": [
        {
          "consensus_timestamp": "1234567890.000000001",
          "valid_start_timestamp": "1234567890.000000000",
          "charged_tx_fee": 7,
          "memo_base64": null,
          "result": "SUCCESS",
          "transaction_hash": "aGFzaA==",
          "name": "TOKENTRANSFER",
          "node": "0.0.3",
          "transaction_id": "0.0.10-1234567890-000000000",
          "valid_duration_seconds": "11",
          "max_fee": "33",
          "transfers": [
            {
              "account": "0.0.9",
              "amount": 10
            },
            {
              "account": "0.0.10",
              "amount": -11
            },
            {
              "account": "0.0.98",
              "amount": 1
            }
          ],
          "token_transfers": [
            {
              "account": "0.0.200",
              "amount": 200,
              "token_id": "0.0.90000"
            },
            {
              "account": "0.0.300",
              "amount": -1200,
              "token_id": "0.0.90000"
            },
            {
              "account": "0.0.400",
              "amount": 1000,
              "token_id": "0.0.90000"
            }
          ],
          "custom_fees": [
            {
              "amount": 150,
              "collector": "0.0.87501"
            },
            {
              "amount": 10,
              "collector": "0.0.87502",
              "token_id": "0.0.90000"
            }
          ]
        }
      ]
    }

```

- Update transactions query to include transactions with assessed custom fee's collector matching the `account.id`
  filter

### Token Info

Add `can_update_custom_fees_with_admin_key` and `custom_fees` to the response json object of `/api/v1/tokens/<tokenId>`

```json
    {
      "admin_key": {
        "_type": "ProtobufEncoded",
        "key": "9c2233222c2233222c2233227d"
      },
      "auto_renew_account": "0.0.6",
      "auto_renew_period": null,
      "decimals": "1000",
      "expiry_timestamp": null,
      "freeze_default": false,
      "freeze_key": {
        "_type": "ProtobufEncoded",
        "key": "9c2233222c2233222c2233227d"
      },
      "initial_supply": "1000000",
      "kyc_key": {
        "_type": "ProtobufEncoded",
        "key": "9c2233222c2233222c2233227d"
      },
      "name": "FOO COIN TOKEN",
      "supply_key": {
        "_type": "ProtobufEncoded",
        "key": "9c2233222c2233222c2233227d"
      },
      "symbol": "FOOCOIN",
      "token_id": "0.15.3",
      "total_supply": "1000000",
      "treasury_account": "0.15.10",
      "wipe_key": {
        "_type": "ProtobufEncoded",
        "key": "9c2233222c2233222c2233227d"
      },
      "can_update_custom_fees_with_admin_key": true,
      "custom_fees": {
        "fixed_fees": [
          {
            "amount": 10,
            "collector_account_id": "0.0.99812"
          },
          {
            "amount": 10,
            "collector_account_id": "0.0.99813",
            "token_id": "0.0.10020"
          }
        ],
        "fractional_fees": [
          {
            "collector_account_id": "0.0.99820",
            "amount": {
              "numerator": 1,
              "denominator": 10
            },
            "maximum": 200,
            "minimum": 50
          },
          {
            "collector_account_id": "0.0.99821",
            "amount": {
              "numerator": 3,
              "denominator": 20
            },
            "minimum": 10
          }
        ]
      }
    }
```

### Token Historical Custom Fees

Add `/api/v1/tokens/<token_id>/customfees` to return the historical custom fees of a token

```json
{
  "token_id": "0.0.90007",
  "custom_fees": [
    {
      "consensus_timestamp": "1234567890.000000001",
      "fixed_fees": [
        {
          "amount": 10,
          "collector_account_id": "0.0.99812"
        },
        {
          "amount": 10,
          "collector_account_id": "0.0.99813",
          "token_id": "0.0.10020"
        }
      ],
      "fractional_fees": [
        {
          "collector_account_id": "0.0.99820",
          "amount": {
            "numerator": 1,
            "denominator": 10
          },
          "maximum": 200,
          "minimum": 50
        },
        {
          "collector_account_id": "0.0.99821",
          "amount": {
            "numerator": 3,
            "denominator": 20
          },
          "minimum": 10
        }
      ]
    },
    {
      "consensus_timestamp": "123456700.000000001",
      "fixed_fees": [
        {
          "amount": 10,
          "collector_account_id": "0.0.99812"
        },
      ],
      "fractional_fees": []
    }
  ],
  "links": {
    "next": "/api/v1/tokens/90007/customfees?limit=500&timestamp=lt:123456700.000000001"
  }
}
```

Optional filters:

- `/api/v1/tokens/<token_id>/customfees?order=asc`
- `/api/v1/tokens/<token_id>/customfees?timestamp=123456700.000000001`

## Non-Functional Requirements
