# HIP-336 Approval and Allowance

## Purpose

[HIP-336](https://hips.hedera.com/hip/hip-336) describes new APIs to approve and exercise allowances to a delegate
account. An allowance grants a spender the right to transfer a predetermined maximum limit of the payer's hbars or
tokens to another account of the spender's choice.

## Goals

* Enhance the database schema to store an account's allowances
* Store the historical state of allowances
* Enhance the REST API to show an account's crypto and token allowances

## Non-Goals

* Store the live state of allowances adjusted for each crypto transfer
* Enhance gRPC APIs with allowance information
* Enhance Web3 APIs with allowance information

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
   add column if not exists allowance_granted_timestamp bigint default null,
   add column if not exists delegating_spender bigint default null,
   add column if not exists spender bigint default null;

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

  - `allowanceGrantedTimestamp`
  - `delegatingSpender`
  - `spender`

#### Nft Allowance Parsing

When parsing nft allowances,

  - Persist approved for all nft allowances (either grant or revoke) to the `nft_allowance` table
  - Persist nft allowances by (token id, serial number) to the `nft` table by updating `allowance_granted_timestamp`,
    `delegating_spender`, and `spender`

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

* `limit`: The maximum amount of items to return.
* `order`: Order by `spender`. Accepts `asc` or `desc` with a default of `asc`.
* `spender.id`: Filter by the spender account ID. `ne` operator is not supported.

#### NFT Allowances

##### NFT Allowances by Serial Numbers

Update `/api/v1/accounts/{accountId}/nfts` to show nft allowance

```json
{
  "nfts": [
    {
      "account_id": "0.0.1000",
      "allowance_granted_timestamp": null,
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
      "allowance_granted_timestamp": "1610682500.000000002",
      "created_timestamp": "1234567890.000000001",
      "delegating_spender": null,
      "deleted": false,
      "metadata": "VGhpcyBpcyBhIHRlc3QgTkZU",
      "modified_timestamp": "1610682445.003266001",
      "serial_number": 2,
      "spender": "0.0.1201",
      "token_id": "0.0.1032"
    },
    {
      "account_id": "0.0.1000",
      "allowance_granted_timestamp": "1610682500.000000001",
      "created_timestamp": "1234567890.000000001",
      "delegating_spender": "0.0.1300",
      "deleted": false,
      "metadata": "VGhpcyBpcyBhIHRlc3QgTkZU",
      "modified_timestamp": "1610682445.003266001",
      "serial_number": 1,
      "spender": "0.0.1200",
      "token_id": "0.0.1032"
    }
  ],
  "links": {}
}
```

Optional Filters

* Add `spender.id`: Filter by the spender account ID. `ne` operator is not supported. Note if no `spender.id` filter
  is specified, the REST api will show all nfts owned by the account, regardless of nft allowance; if `spender.id`
  filter is specified, the REST api will only show nfts owned by the account with allowance spender matching
  `spender.id`.
* `order`: Order by `token_id` and `serial_number`. Accepts `asc` or `desc` with a default of `desc`.

##### Approved For All NFT Allowances

`/api/v1/accounts/{accountId}/allowances/nfts`

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
      "spender": "0.0.8488",
      "token_id": "0.0.1034",
      "timestamp": {
        "from": "1633466229.96874612",
        "to": null
      }
    },
    {
      "approved_for_all": true,
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

* `limit`: The maximum amount of items to return.
* `order`: Order by `spender` and `token_id`. Accepts `asc` or `desc` with a default of `asc`.
* `spender.id`: Filter by the spender account ID. `ne` operator is not supported.
* `token.id`: Filter by the token ID. `ne` operator is not supported.

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

* `limit`: The maximum amount of items to return.
* `order`: Order by `spender` and `token_id`. Accepts `asc` or `desc` with a default of `asc`.
* `spender.id`: Filter by the spender account ID. `ne` operator is not supported.
* `token.id`: Filter by the token ID. `ne` operator is not supported.

#### Transactions APIs

Update all APIs that show transfers to return `is_approval` in its response.

###Accounts Endpoint
Update `/api/v1/accounts/{accountId}` to return `is_approval` for all transfers.

```json
{
  "account": "0.1.2",
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
          "receiver_account_id": "0.0.121",
          "sender_account_id": "0.0.122",
          "serial_number": 1,
          "token_id": "0.0.123",
          "is_approval": true
        },
        {
          "receiver_account_id": "0.0.321",
          "sender_account_id": "0.0.422",
          "serial_number": 2,
          "token_id": "0.0.123",
          "is_approval": true
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
          "token_id": "0.0.90000",
          "account": "0.0.9",
          "amount": 1200,
          "is_approval": true
        },
        {
          "token_id": "0.0.90000",
          "account": "0.0.8",
          "amount": -1200,
          "is_approval": false
        }
      ]
    }
  ]
}
```
###Transactions Endpoint

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
          "receiver_account_id": "0.0.121",
          "sender_account_id": "0.0.122",
          "serial_number": 1,
          "token_id": "0.0.123",
          "is_approval": true
        },
        {
          "receiver_account_id": "0.0.321",
          "sender_account_id": "0.0.422",
          "serial_number": 2,
          "token_id": "0.0.123",
          "is_approval": false
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
          "token_id": "0.0.90000",
          "account": "0.0.9",
          "amount": 1200,
          "is_approval": false
        },
        {
          "token_id": "0.0.90000",
          "account": "0.0.8",
          "amount": -1200,
          "is_approval": true
        }
      ]
    }
  ]
}
```
###NFT Transaction History Endpoint
Update `/api/v1/tokens/{tokenId}/nfts/{serialNumber}/transactions` to include `is_approval` for all transfers.
```json
{
  "transactions": [
    {
      "consensus_timestamp": "1234567890.000000007",
      "nonce": 0,
      "transaction_id": "0.0.8-1234567890-000000007",
      "receiver_account_id": "0.0.3001",
      "sender_account_id": "0.0.2001",
      "type": "CRYPTOTRANSFER",
      "is_approval": false
    },
    {
      "consensus_timestamp": "1234567890.000000006",
      "nonce": 1,
      "transaction_id": "0.0.8-1234567890-000000006",
      "receiver_account_id": "0.0.2001",
      "sender_account_id": "0.0.1001",
      "type": "CRYPTOTRANSFER",
      "is_approval": false
    }
  ]
}
```
## Non-Functional Requirements

* Ingest new transaction types at the same rate as consensus nodes

## Open Questions

1) How will we do REST API pagination using multiple columns?

## Answered Questions

1) How will we handle adjust allowance for serial numbers?

   The full list of allowed serials that result from the transaction will be provided in the record.

2) What happens if client populates both `approvedForAll` and `serialNumbers`?

   `approvedForAll` nft allowance and `serialNumbers` nft allowance are orthogonal. The two types of nft allowances can
   appear in the same `NftAllowance` protobuf message, mirrornode should parse them separately.
