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

- Add a new `custom_fee` table

```sql
create table if not exists custom_fee
(
    amount                    bigint,
    amount_denominator        bigint,
    can_update_with_admin_key boolean not null,
    collector_account_id      bigint,
    created_timestamp         bigint not null,
    denominating_token_id     bigint,
    maximum_amount            bigint,
    minimum_amount            bigint,
    token_id                  bigint not null
);
create index if not exists
    custom_fee__token_timestamp on custom_fee (token_id, created_timestamp desc);
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
```

## Importer

### Domain

- Add `AssessedCustomFee` class with the following fields
  - `amount`
  - `collectorAccountId`
  - `consensusTimestamp`
  - `tokenId`

- Add `CustomFee` class with the following fields
  - `amount`
  - `amountDenominator`
  - `canUpdateWithAdminKey`
  - `collectorAccountId`
  - `createdTimestamp`
  - `denominatingTokenId`
  - `maximumAmount`
  - `minimumAmount`
  - `tokenId`

### Custom Fee Parsing

#### EntityListener

- Add a `onAssessedCustomFee()` to handle inserts on the `assessed_custom_fee` table
```java
    default void onAssessedCustomFee(AssessedCustomFee assessedCustomFee) throws ImporterException {
    }
```

- Add a `onCustomFee()` to handle inserts on the `custom_fee` table
```java
    default void onCustomFee(CustomFee CustomFee) throws ImporterException {
    }
```

#### EntityRecordItemListener

- Add a function `insertAssessedCustomFees()` to insert assessed custom fees in a transaction record
- Add a function `insertCustomFees()` to insert custom fees in a transaction record
- Update `insertTokenCreate` and `insertTokenUpdate` to insert a token's custom fees

## REST API

### Transactions Endpoint

- Update `/api/v1/transactions/{id}` response to add assessed custom fees. Note if an assessed custom fee doesn't have a
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
            "amount": -161
          },
          {
            "account": "0.0.98",
            "amount": 1
          },
          {
            "account": "0.0.87501",
            "amount": 150
          }
        ],
        "token_transfers": [
          {
            "account": "0.0.200",
            "amount": 200,
            "token_id": "0.0.90000"
          },
          {
            "account": "0.0.10",
            "amount": -1210,
            "token_id": "0.0.90000"
          },
          {
            "account": "0.0.400",
            "amount": 1000,
            "token_id": "0.0.90000"
          },
          {
            "account": "0.0.87502",
            "amount": 10,
            "token_id": "0.0.90000"
          }
        ],
        "assessed_custom_fees": [
          {
            "amount": 150,
            "collector_account_id": "0.0.87501",
            "payer_account_id": "0.0.10"
          },
          {
            "amount": 10,
            "collector_account_id": "0.0.87502",
            "token_id": "0.0.90000",
            "payer_account_id": "0.0.10"
          }
        ]
      }
    ]
  }
```

### Token Info

Add `can_update_custom_fees_with_admin_key` and `custom_fees` to the response json object of `/api/v1/tokens/:id`

```json
  {
    "token_id": "0.0.1135",
    "symbol": "ORIGINALRDKSE",
    "admin_key": null,
    "auto_renew_account": null,
    "auto_renew_period": null,
    "created_timestamp": "1234567890.000000002",
    "decimals": "1000",
    "expiry_timestamp": null,
    "freeze_default": false,
    "freeze_key": null,
    "initial_supply": "1000000",
    "kyc_key": null,
    "max_supply": "9223372036854775807",
    "modified_timestamp": "1234567899.000000002",
    "name": "Token name",
    "supply_key": null,
    "supply_type": "INFINITE",
    "total_supply": "1000000",
    "treasury_account_id": "0.0.98",
    "type": "FUNGIBLE_COMMON",
    "wipe_key": null,
    "can_update_custom_fees_with_admin_key": true,
    "custom_fees": {
      "created_timestamp": "1234567896.000000001",
      "can_update_with_admin_key": true,
      "fixed_fees": [
        {
          "amount": 10,
          "collector_account_id": "0.0.99812"
        },
        {
          "amount": 10,
          "collector_account_id": "0.0.99813",
          "denominating_token_id": "0.0.10020"
        }
      ],
      "fractional_fees": [
        {
          "amount": {
            "numerator": 1,
            "denominator": 10
          },
          "collector_account_id": "0.0.99820",
          "maximum": 200,
          "minimum": 50
        },
        {
          "amount": {
            "numerator": 3,
            "denominator": 20
          },
          "collector_account_id": "0.0.99821",
          "minimum": 10
        }
      ]
    }
  }
```

Add optional filters

- `/api/v1/tokens/:id?timestamp=123456789.000000001` - return the historical custom fees of a token effective at the
  specified timestamp. Note the query is designed with support of historical value of other token fields but only custom
  fees will be implemented.

## Non-Functional Requirements
