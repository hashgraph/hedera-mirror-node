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

### Nft Allowance Parsing

Approved for all nft allowances (either grant or revoke) are persisted into the `nft_allowance` table, while nft
allowances by (token id, serial number) are persisted into the `nft` table by updating `allowance_granted_timestamp`,
`delegating_spender`, and `spender`.

In order to do so, add

  - `EntityListner.onNftAllowance(NftAllowance nft)` for approved for all nft allowances
  - `EntityListner.onNftInstanceAllowance(Nft nft)` for nft allowances by (token id, serial number)

### REST API

#### Crypto Allowances

`/api/v1/accounts/{accountId}/allowances/crypto`

```json
{
  "allowances": [
    {
      "amount": 10,
      "owner": "0.0.1000",
      "payer_account_id": "0.0.1000",
      "spender": "0.0.8488",
      "timestamp": {
        "from": "1633466229.96874612",
        "to": "1633466568.31556926"
      }
    },
    {
      "amount": 5,
      "owner": "0.0.1000",
      "payer_account_id": "0.0.1001",
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
* `spender.id`: Filter by the spender account ID. Only need to support `eq` operator and allow multiple.

#### NFT Allowances

`/api/v1/accounts/{accountId}/allowances/nfts`

```json
{
  "allowances": [
    {
      "approved_for_all": null,
      "owner": "0.0.1000",
      "serial_number": 1,
      "spender": "0.0.8488",
      "token_id": "0.0.1032",
      "timestamp": {
        "from": "1633466229.96874612",
        "to": null
      }
    },
     {
        "approved_for_all": null,
        "owner": "0.0.1000",
        "serial_number": 2,
        "spender": "0.0.8488",
        "token_id": "0.0.1032",
        "timestamp": {
           "from": "1633466229.96874612",
           "to": null
        }
     },
     {
        "approved_for_all": true,
        "owner": "0.0.1000",
        "serial_number": null,
        "spender": "0.0.8488",
        "token_id": "0.0.1033",
        "timestamp": {
           "from": "1633466229.96874612",
           "to": null
        }
     },
    {
      "approved_for_all": null,
      "owner": "0.0.1000",
      "serial_number": 1,
      "spender": "0.0.8488",
      "token_id": "0.0.1034",
      "timestamp": {
        "from": "1633466229.96874612",
        "to": null
      }
    }
  ],
  "links": {}
}
```

`/api/v1/accounts/{accountId}/allowances/nfts?approval.type=approved_for_all`
```json
{
  "allowances": [
     {
        "approved_for_all": true,
        "owner": "0.0.1000",
        "serial_number": null,
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
      "serial_number": 1,
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
* `order`: Order by `spender.id`, `token.id` and `serialnumber`. Accepts `asc` or `desc` with a default of `asc`.
* `approval.type`: Filter by the nft allowance approval type. Accepts `approved_for_all` or `serialnumber` with a
  default of `serialnumber`.
* `spender.id`: Filter by the spender ID. `ne` filter is not supported.
* `token.id`: Filter by the token ID. `ne` filter is not supported.
* `serialnumber`: Filter by the nft serial number. `ne` filter is not supported. Note the filter is ignored
  if `approval.type` is `approved_for_all`.

#### Token Allowances

`/api/v1/accounts/{accountId}/allowances/tokens`

```json
{
  "allowances": [
    {
      "amount": 10,
      "owner": "0.0.1000",
      "payer_account_id": "0.0.1000",
      "spender": "0.0.8488",
      "token_id": "0.0.1032",
      "timestamp": {
        "from": "1633466229.96874612",
        "to": "1633466568.31556926"
      }
    },
    {
      "amount": 5,
      "owner": "0.0.1000",
      "payer_account_id": "0.0.1000",
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
* `spender.id`: Filter by the spender account ID. Only need to support `eq` operator and allow multiple.
* `token.id`: Filter by the token ID. Only need to support `eq` operator and allow multiple.

#### Transactions APIs

Update all APIs that show transfers to return `is_approval` in its response. Including `/api/v1/accounts/:id` and all
the transactions REST APIs.

## Non-Functional Requirements

* Ingest new transaction types at the same rate as consensus nodes

## Open Questions

1) How will we do REST API pagination using multiple columns?

## Answered Questions

1) How will we handle adjust allowance for serial numbers?

   The full list of allowed serials that result from the transaction will be provided in the record.

2) What happens if client populates both `approvedForAll` and `serialNumbers`?

   It is an error if they populate `approvedForAll=true` and a non-empty `serialNumbers`. It is allowed, but not
   required, to populate `approvedForAll=false` when providing a non-empty `serialNumbers`.
