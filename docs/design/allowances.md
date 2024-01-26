# HIP-336 Approval and Allowance

## Purpose

[HIP-336](https://hips.hedera.com/hip/hip-336) describes new APIs to approve and exercise allowances to a delegate
account. An allowance grants a spender the right to transfer a predetermined maximum limit of the payer's hbars or
tokens to another account of the spender's choice.

## Goals

- Enhance the database schema to store an account's allowances
- Store the historical state of allowances
- Enhance the REST API to show an account's crypto and token allowances

## Non-Goals

- Store the live state of allowances adjusted for each crypto transfer
- Enhance gRPC APIs with allowance information
- Enhance Web3 APIs with allowance information

## Architecture

### Database

#### Crypto Allowance

```sql
create table if not exists crypto_allowance
(
  amount           bigint    not null,
  owner            bigint    not null,
  payer_account_id bigint    not null,
  spender          bigint    not null,
  timestamp_range  int8range not null,
  primary key (owner, spender)
);
```

```sql
create table if not exists crypto_allowance_history
(
  like crypto_allowance including defaults,
  primary key (owner, spender, timestamp_range)
);
```

#### NFT Allowance

```sql

create table if not exists nft_allowance
(
  approved_for_all boolean   not null,
  owner            bigint    not null,
  payer_account_id bigint    not null,
  spender          bigint    not null,
  timestamp_range  int8range not null,
  token_id         bigint    not null,
  primary key (owner, spender, token_id)
);
```

```sql
create table if not exists nft_allowance_history
(
  like nft_allowance including defaults,
  primary key (owner, spender, token_id, timestamp_range)
);
```

Update `nft` table to add new columns and index for nft instance allowance.

```sql
alter table nft
  add column if not exists delegating_spender bigint default null,
  add column if not exists spender            bigint default null;

create index if not exists nft__allowance on nft (account_id, spender, token_id, serial_number)
  where account_id is not null and spender is not null;
```

#### Token Allowance

```sql
create table if not exists token_allowance
(
  amount           bigint    not null,
  owner            bigint    not null,
  payer_account_id bigint    not null,
  spender          bigint    not null,
  timestamp_range  int8range not null,
  token_id         bigint    not null,
  primary key (owner, spender, token_id)
);
```

```sql
create table if not exists token_allowance_history
(
  like token_allowance including defaults,
  primary key (owner, spender, token_id, timestamp_range)
);
```

### Importer

#### Nft

Add the following class members to the `Nft` domain class:

- `delegatingSpender`
- `spender`

#### Nft Allowance Parsing

When parsing nft allowances,

- Persist approved for all nft allowances (either grant or revoke) to the `nft_allowance` table
- Persist nft allowances by (token id, serial number) to the `nft` table by updating `delegating_spender`,
  `modified_timestamp` and `spender`

Update `EntityListener`

- Add `EntityListner.onNftAllowance(NftAllowance nft)` for approved for all nft allowances
- Update `EntityListner.onNft(Nft nft)` to handle nft allowances by (token id, serial number)

### REST API

#### Crypto Allowances

`/api/v1/accounts/{accountId}/allowances/crypto`

```json
{
  "allowances": [
    {
      "amount_granted": 10,
      "owner": "0.0.1000",
      "spender": "0.0.8488",
      "timestamp": {
        "from": "1633466229.96874612",
        "to": "1633466568.31556926"
      }
    },
    {
      "amount_granted": 5,
      "owner": "0.0.1000",
      "spender": "0.0.9857",
      "timestamp": {
        "from": "1633466229.96874612",
        "to": null
      }
    }
  ],
  "links": {}
}
```

Optional Filters

- `limit`: The maximum amount of items to return.
- `order`: Order by `spender`. Accepts `asc` or `desc` with a default of `asc`.
- `spender.id`: Filter by the spender account ID. `ne` operator is not supported.

#### NFT Allowances

##### NFT Allowances by Serial Numbers

Update `/api/v1/accounts/{accountId}/nfts` to show nft allowance

```json
{
  "nfts": [
    {
      "account_id": "0.0.1000",
      "created_timestamp": "1234567891.000000001",
      "delegating_spender": null,
      "deleted": false,
      "metadata": "VGhpcyBpcyBhIHRlc3QgTkZU",
      "modified_timestamp": "1610682446.003266001",
      "serial_number": 1,
      "spender": null,
      "token_id": "0.0.1033"
    },
    {
      "account_id": "0.0.1000",
      "created_timestamp": "1234567890.000000001",
      "delegating_spender": null,
      "deleted": false,
      "metadata": "VGhpcyBpcyBhIHRlc3QgTkZU",
      "modified_timestamp": "1610682500.000000002",
      "serial_number": 2,
      "spender": "0.0.1201",
      "token_id": "0.0.1032"
    },
    {
      "account_id": "0.0.1000",
      "created_timestamp": "1234567890.000000001",
      "delegating_spender": "0.0.1300",
      "deleted": false,
      "metadata": "VGhpcyBpcyBhIHRlc3QgTkZU",
      "modified_timestamp": "1610682500.000000001",
      "serial_number": 1,
      "spender": "0.0.1200",
      "token_id": "0.0.1032"
    }
  ],
  "links": {}
}
```

Optional Filters

- Add `spender.id`: Filter by the spender account ID. `ne` operator is not supported. Note if no `spender.id` filter
  is specified, the REST api will show all nfts owned by the account, regardless of nft allowance; if `spender.id`
  filter is specified, the REST api will only show nfts owned by the account with allowance spender matching
  `spender.id`.
- `order`: Order by `token_id` and `serial_number`. Accepts `asc` or `desc` with a default of `desc`.

##### Approved For All NFT Allowances

This API accepts a path parameter that represents either the owner or spender, depending on a boolean flag provided as a query parameter called `owner`. When the `owner` value is true or omitted, the `accountId` path parameter should specify the ID of the owner, and the API will retrieve the allowances that the owner has granted to different spenders. Conversely, when the `owner` value is false, the `accountId` path parameter should indicate the ID of the spender who has an allowance, and the API will instead provide the allowances granted to the spender by different owners of those tokens.

Following are example responses of the NFT allowance API:

GET `/api/v1/accounts/0.0.1000/allowances/nfts?limit=3&owner=true`

```json
{
  "allowances": [
    {
      "approved_for_all": true,
      "owner": "0.0.1000",
      "spender": "0.0.8488",
      "token_id": "0.0.1033",
      "timestamp": {
        "from": "1633466229.96874612",
        "to": null
      }
    },
    {
      "approved_for_all": false,
      "owner": "0.0.1000",
      "spender": "0.0.8489",
      "token_id": "0.0.1034",
      "timestamp": {
        "from": "1633466229.96874618",
        "to": null
      }
    },
    {
      "approved_for_all": true,
      "owner": "0.0.1000",
      "spender": "0.0.9857",
      "token_id": "0.0.1032",
      "timestamp": {
        "from": "1633466229.96884615",
        "to": null
      }
    }
  ],
  "links": {
    "next": "/api/v1/accounts/0.0.1000/allowances/nfts?limit=3&order=asc&account.id=gte:9857&token.id=gt:0.0.1032"
  }
}
```

GET `/api/v1/accounts/0.0.8488/allowances/nfts?limit=3&owner=false`

```json
{
  "allowances": [
    {
      "approved_for_all": true,
      "owner": "0.0.1000",
      "spender": "0.0.8488",
      "token_id": "0.0.1033",
      "timestamp": {
        "from": "1633466229.96874612",
        "to": null
      }
    },
    {
      "approved_for_all": true,
      "owner": "0.0.1000",
      "spender": "0.0.8488",
      "token_id": "0.0.1034",
      "timestamp": {
        "from": "1633466229.96874618",
        "to": null
      }
    },
    {
      "approved_for_all": false,
      "owner": "0.0.1001",
      "spender": "0.0.8488",
      "token_id": "0.0.1099",
      "timestamp": {
        "from": "1633466229.96875612",
        "to": null
      }
    }
  ],
  "links": {
    "next": "/api/v1/accounts/0.0.8488/allowances/nfts?limit=3&order=asc&account.id=gte:1001&token.id=gt:0.0.1099"
  }
}
```

Query Parameters:

- `account.id`: Filter by the spender account ID or owner account ID, depending on the owner flag. `ne` operator is not supported. Only one occurrence is allowed.
- `limit`: The maximum number of items to return. Defaults to 25 with a maximum of 100 allowed.
- `order`: Order by `account.id` then `token.id`. Accepts `asc` or `desc` with a default of `asc`.
- `owner`: Indicates whether the path parameter `accountId` is the owner or the spender ID. Accepts a boolean value of `true` or `false` with a default value set to `true`.
- `token.id`: Filter by the token ID. `ne` operator is not supported. Only one occurrence is allowed.

Pagination is important to implement because there are accounts that could have a large number of NFT allowances, making a non-paginated response impractical. This requires multi-column pagination, including owner, spender, and token IDs.

**Ordering**

The order is governed by a combination of the account ID and the token ID values, with the account ID being the parent column. The token ID value governs its order within the given account ID.
The default order for this API is ascending.

**Filtering**

When filtering there are some restrictions enforced to ensure correctness and scalability.

The table below defines the restrictions and support for the endpoint.

| Query Param | Comparison Operator | Support | Description                                                                                   | Example                         |
| ----------- | ------------------- | ------- | --------------------------------------------------------------------------------------------- | ------------------------------- |
| account.id  | eq                  | Y       | Single occurrence only.                                                                       | ?account.id=X                   |
|             | ne                  | N       |                                                                                               |                                 |
|             | lt(e)               | Y       | Single occurrence only.                                                                       | ?account.id=lte:X               |
|             | gt(e)               | Y       | Single occurrence only.                                                                       | ?account.id=gte:X               |
| token.id    | eq                  | Y       | Single occurrence only. Requires the presence of an `account.id` query parameter              | ?account.id=X&token.id=eq:Y     |
|             | ne                  | N       |                                                                                               |                                 |
|             | lt(e)               | Y       | Single occurrence only. Requires the presence of a `lte` or `eq` `account.id` query parameter | ?account.id=lte:X&token.id=lt:Y |
|             | gt(e)               | Y       | Single occurrence only. Requires the presence of a `gte` or `eq` `account.id` query parameter | ?account.id=gte:X&token.id=gt:Y |

Both filters must be a single occurrence of **gt(e)** or **lt(e)** which provide a lower and or upper boundary for search.

Note this API is optional.

#### Token Allowances

`/api/v1/accounts/{accountId}/allowances/tokens`

```json
{
  "allowances": [
    {
      "amount_granted": 10,
      "owner": "0.0.1000",
      "spender": "0.0.8488",
      "token_id": "0.0.1032",
      "timestamp": {
        "from": "1633466229.96874612",
        "to": "1633466568.31556926"
      }
    },
    {
      "amount_granted": 5,
      "owner": "0.0.1000",
      "spender": "0.0.9857",
      "token_id": "0.0.1032",
      "timestamp": {
        "from": "1633466229.96874612",
        "to": null
      }
    }
  ],
  "links": {}
}
```

Optional Filters

- `limit`: The maximum amount of items to return.
- `order`: Order by `spender` and `token_id`. Accepts `asc` or `desc` with a default of `asc`.
- `spender.id`: Filter by the spender account ID. `ne` operator is not supported.
- `token.id`: Filter by the token ID. `ne` operator is not supported.

#### Transactions APIs

Update all APIs that show transfers to return `is_approval` in its response.

### Accounts Endpoint

Update `/api/v1/accounts/{accountId}` to return `is_approval` for all transfers.

```json
{
  "account": "0.1.2",
  "auto_renew_period": "string",
  "balance": {
    "balance": 80,
    "timestamp": "0.000002345",
    "tokens": [
      {
        "token_id": "0.0.200001",
        "balance": 8
      }
    ]
  },
  "deleted": true,
  "expiry_timestamp": "1586567700.453054000",
  "key": {
    "_type": "ProtobufEncoded",
    "key": "7b2231222c2231222c2231227d"
  },
  "links": {
    "next": null
  },
  "max_automatic_token_associations": 0,
  "memo": "string",
  "receiver_sig_required": true,
  "transactions": [
    {
      "consensus_timestamp": "1234567890.000000007",
      "transaction_hash": "vigzKe2J7fv4ktHBbNTSzQmKq7Lzdq1/lJMmHT+a2KgvdhAuadlvS4eKeqKjIRmW",
      "valid_start_timestamp": "1234567890.000000006",
      "charged_tx_fee": 7,
      "memo_base64": null,
      "bytes": null,
      "result": "SUCCESS",
      "entity_id": "0.0.2281979",
      "name": "CRYPTOTRANSFER",
      "nft_transfers": [
        {
          "is_approval": true,
          "receiver_account_id": "0.0.121",
          "sender_account_id": "0.0.122",
          "serial_number": 1,
          "token_id": "0.0.123"
        },
        {
          "is_approval": true,
          "receiver_account_id": "0.0.321",
          "sender_account_id": "0.0.422",
          "serial_number": 2,
          "token_id": "0.0.123"
        }
      ],
      "max_fee": 33,
      "valid_duration_seconds": 11,
      "node": "0.0.3",
      "transaction_id": "0.0.8-1234567890-000000006",
      "scheduled": false,
      "transfers": [
        {
          "account": "0.0.3",
          "amount": 2,
          "is_approval": false
        },
        {
          "account": "0.0.8",
          "amount": -3,
          "is_approval": false
        },
        {
          "account": "0.0.98",
          "amount": 1,
          "is_approval": true
        }
      ],
      "token_transfers": [
        {
          "account": "0.0.9",
          "amount": 1200,
          "is_approval": true,
          "token_id": "0.0.90000"
        },
        {
          "account": "0.0.8",
          "amount": -1200,
          "is_approval": false,
          "token_id": "0.0.90000"
        }
      ],
      "assessed_custom_fees": [
        {
          "amount": 150,
          "collector_account_id": "0.0.87501",
          "effective_payer_account_ids": ["0.0.87501"],
          "token_id": null
        },
        {
          "amount": 10,
          "collector_account_id": "0.0.87502",
          "effective_payer_account_ids": ["0.0.10"],
          "token_id": "0.0.90000"
        }
      ]
    }
  ]
}
```

### Transactions Endpoint

Update `/api/v1/transactions/{id}` to include `is_approval` for all transfers.

```json
{
  "transactions": [
    {
      "consensus_timestamp": "1234567890.000000007",
      "transaction_hash": "vigzKe2J7fv4ktHBbNTSzQmKq7Lzdq1/lJMmHT+a2KgvdhAuadlvS4eKeqKjIRmW",
      "valid_start_timestamp": "1234567890.000000006",
      "charged_tx_fee": 7,
      "memo_base64": null,
      "bytes": null,
      "result": "SUCCESS",
      "entity_id": "0.0.2281979",
      "name": "CRYPTOTRANSFER",
      "nft_transfers": [
        {
          "is_approval": true,
          "receiver_account_id": "0.0.121",
          "sender_account_id": "0.0.122",
          "serial_number": 1,
          "token_id": "0.0.123"
        },
        {
          "is_approval": false,
          "receiver_account_id": "0.0.321",
          "sender_account_id": "0.0.422",
          "serial_number": 2,
          "token_id": "0.0.123"
        }
      ],
      "max_fee": 33,
      "valid_duration_seconds": 11,
      "node": "0.0.3",
      "transaction_id": "0.0.8-1234567890-000000006",
      "scheduled": false,
      "transfers": [
        {
          "account": "0.0.3",
          "amount": 2,
          "is_approval": true
        },
        {
          "account": "0.0.8",
          "amount": -3,
          "is_approval": false
        },
        {
          "account": "0.0.98",
          "amount": 1,
          "is_approval": false
        }
      ],
      "token_transfers": [
        {
          "account": "0.0.9",
          "amount": 1200,
          "is_approval": false,
          "token_id": "0.0.90000"
        },
        {
          "account": "0.0.8",
          "amount": -1200,
          "is_approval": true,
          "token_id": "0.0.90000"
        }
      ],
      "assessed_custom_fees": [
        {
          "amount": 150,
          "collector_account_id": "0.0.87501",
          "effective_payer_account_ids": ["0.0.87501"],
          "token_id": null
        },
        {
          "amount": 10,
          "collector_account_id": "0.0.87502",
          "effective_payer_account_ids": ["0.0.10"],
          "token_id": "0.0.90000"
        }
      ]
    }
  ]
}
```

### NFT Transaction History Endpoint

Update `/api/v1/tokens/{tokenId}/nfts/{serialNumber}/transactions` to include `is_approval` for all transfers.

```json
{
  "transactions": [
    {
      "consensus_timestamp": "1234567890.000000007",
      "is_approval": false,
      "nonce": 0,
      "transaction_id": "0.0.8-1234567890-000000007",
      "receiver_account_id": "0.0.3001",
      "sender_account_id": "0.0.2001",
      "type": "CRYPTOTRANSFER"
    },
    {
      "consensus_timestamp": "1234567890.000000006",
      "is_approval": false,
      "nonce": 1,
      "transaction_id": "0.0.8-1234567890-000000006",
      "receiver_account_id": "0.0.2001",
      "sender_account_id": "0.0.1001",
      "type": "CRYPTOTRANSFER"
    }
  ]
}
```

## Non-Functional Requirements

- Ingest new transaction types at the same rate as consensus nodes

## Open Questions

1. How will we do REST API pagination using multiple columns?

## Answered Questions

1. How will we handle adjust allowance for serial numbers?

   Crypto approve allowance transactions can grant nft allowances by serial numbers and crypto delete allowance
   transactions revoke them.

2. What happens if client populates both `approvedForAll` and `serialNumbers`?

   `approvedForAll` nft allowance and `serialNumbers` nft allowance are orthogonal. The two types of nft allowances can
   appear in the same `NftAllowance` protobuf message, mirrornode should parse them separately.
