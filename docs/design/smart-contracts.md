# Smart Contracts

## Purpose

Smart contracts have existed on Hedera since Open Access, but the mirror node has never stored all the data associated
with smart contract transactions in its database. With the
[announcement](https://hedera.com/blog/hedera-evm-smart-contracts-now-bring-highest-speed-programmability-to-tokenization)
to bring high speed smart contract execution to the Hedera network, it has become more important to ensure the mirror
node is storing the appropriate smart contract information and making it retrievable via its APIs.

## Goals

- Enhance the database schema to store all contract-related information from transactions and transaction records
- Enhance the REST API to retrieve smart contracts and their execution results
- Enhance the REST API to search by smart contract log topics
- Explore alternative smart contract APIs including compatibility
  with [Ethereum JSON-RPC](https://ethereum.org/en/developers/docs/apis/json-rpc/) APIs

## Non-Goals

- Ensure 100% compatibility with all Ethereum JSON-RPC APIs
- Execute smart contracts on the mirror node

## Architecture

### Database

#### Contract

Create a contract table that has most of the same fields as the entity table. A database migration should move entries
in `entity` into `contract` if they are of type contract or have contract create or update transactions. The
contract-specific fields will need to be marked as nullable since we didn't store them on any existing tables.

```sql
create table if not exists contract
(
  auto_renew_period    bigint             null,
  created_timestamp    bigint             null,
  deleted              boolean            null,
  expiration_timestamp bigint             null,
  file_id              bigint             null,
  id                   bigint             not null,
  key                  bytea              null,
  memo                 text    default '' not null,
  num                  bigint             not null,
  obtainer_id          bigint             null,
  parent_id            bigint             null,
  proxy_account_id     bigint             null,
  public_key           character varying  null,
  realm                bigint             not null,
  shard                bigint             not null,
  timestamp_range      int8range          not null,
  type                 integer default 2  not null
);

alter table if exists contract
  add primary key (id);
```

> _Note:_ Entity table should be updated by another PR to add `timestamp_range int8range`.

#### Contract History

Create a contract history table that is populated by application upsert logic. It should insert the old row to the
history table after setting its timestamp range to end (exclusively) at the new row's start consensus timestamp.

```sql
create table if not exists contract_history
(
  like contract,
  primary key (id, timestamp_range)
);

create index if not exists contract_history__timestamp_range on contract_history using gist (timestamp_range);
```

#### Contract Result

Update the existing `contract_result` to capture all fields present in the protobuf (see below).
Replace `consensus_timestamp` index with `consensus_timestamp` primary key. Migrate data in `call_result` to parse it
using the protobuf and normalize it into the other fields.

```sql
create table if not exists contract_result
(
  amount               bigint             null,
  bloom                bytea              null,
  call_result          bytea              null,
  consensus_timestamp  bigint primary key not null,
  contract_id          bigint             null,
  created_contract_ids bigint array       null,
  error_message        text               null,
  function_parameters  bytea              not null,
  gas_limit            bigint             not null,
  gas_used             bigint             not null
);
```

#### Contract Log

Create a new table to store the results of the contract's log output.

```sql
create table if not exists contract_log
(
  bloom               bytea  not null,
  consensus_timestamp bigint not null,
  contract_id         bigint not null,
  data                bytea  not null,
  index               int    not null,
  topic0              text   null,
  topic1              text   null,
  topic2              text   null,
  topic3              text   null,
  primary key (consensus_timestamp, index)
);
```

## Importer

- Add a `Contract` domain object with fields that match the schema.
- Add a `ContractLog` domain object with fields that match the schema.
- Update the `ContractResult` domain object with fields that match the schema.
- Add a `ContractRepository` and `ContractLogRepository`.
- Add `EntityListener.onContract(Contract)` and `EntityListener.onContractLog(ContractLog)`.
- Add logic to create a `Contract` domain object in create, update, and delete contract transaction handlers and notify
  via `EntityListener`.
- Add logic to create a `ContractResult` and `ContractLog` domain objects in the contract create and contract call
  transaction handlers and notify via `EntityListener`.
- Add logic to `SqlEntityListener` to batch insert `Contract` and `ContractLog`.
- Implement a generic custom `UpsertQueryGenerator` that generates the insert query entirely from annotations on
  the `Contract` domain object.
- Remove logic specific to contracts in `EntityRecordItemListener`.

## REST API

### List Contracts

`GET /api/v1/contracts`

```json
{
  "contracts": [
    {
      "admin_key": {
        "_type": "ProtobufEncoded",
        "key": "7b2233222c2233222c2233227d"
      },
      "auto_renew_period": 7776000,
      "contract_id": "0.0.10001",
      "deleted": false,
      "expiration_timestamp": null,
      "file_id": 1000,
      "memo": "First contract",
      "obtainer_id": null,
      "proxy_account_id": "0.0.100",
      "solidity_address": "0x0000000000000000000000000000000000001001",
      "timestamp": {
        "from": "1633466568.31556926",
        "to": null
      }
    }
  ],
  "links": {
    "next": null
  }
}
```

Optional filters

- `contract.id` Supports all comparison operators and repeated equality parameters to generate an `IN` clause
- `limit`
- `order`

### Get Contract

`GET /api/v1/contracts/{id}`

```json
{
  "admin_key": {
    "_type": "ProtobufEncoded",
    "key": "7b2233222c2233222c2233227d"
  },
  "auto_renew_period": 7776000,
  "bytecode": "0xc896c66db6d98784cc03807640f3dfd41ac3a48c",
  "contract_id": "0.0.10001",
  "deleted": false,
  "file_id": "0.0.1000",
  "memo": "First contract",
  "obtainer_id": "0.0.101",
  "proxy_account_id": "0.0.100",
  "solidity_address": "0x0000000000000000000000000000000000001001",
  "timestamp": {
    "from": "1633466229.96874612",
    "to": "1633466568.31556926"
  }
}
```

Optional filters

- `timestamp` Return the historical state of the contract. Supports all the operators but returns the latest version of
  the contract within that time range.

### List Contract Results

`GET /api/v1/contracts/{id}/results`

```json
{
  "results": [
    {
      "amount": 10,
      "bloom": "0x549358c4c2e573e02410ef7b5a5ffa5f36dd7398",
      "call_result": "0x2b048531b38d2882e86044bc972e940ee0a01938",
      "created_contract_ids": [
        "0.0.1003"
      ],
      "error_message": "",
      "function_parameters": "0xbb9f02dc6f0e3289f57a1f33b71c73aa8548ab8b",
      "gas_limit": 2500,
      "gas_used": 1000,
      "timestamp": "12345.10001"
    }
  ],
  "links": {
    "next": null
  }
}
```

Optional filters

- `limit` Maximum limit will be configurable and lower than current global max limit
- `order`
- `timestamp`

### Get Contract Result

`GET /api/v1/contracts/{id}/results/{timestamp}`

```json
{
  "amount": 10,
  "bloom": "0x549358c4c2e573e02410ef7b5a5ffa5f36dd7398",
  "call_result": "0x2b048531b38d2882e86044bc972e940ee0a01938",
  "created_contract_ids": [
    "0.0.1003"
  ],
  "error_message": "",
  "function_parameters": "0xbb9f02dc6f0e3289f57a1f33b71c73aa8548ab8b",
  "gas_limit": 2500,
  "gas_used": 1000,
  "logs": [
    {
      "bloom": "0x1513001083c899b1996ec7fa33621e2c340203f0",
      "data": "0x8f705727c88764031b98fc32c314f8f9e463fb62",
      "topics": [
        "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef",
        "0x59d088293f09d5119d5b55858b989ffce4d398dc"
      ]
    }
  ],
  "timestamp": "12345.10001"
}
```

## Non-Functional Requirements

- Support peak smart contract call TPS (400+)
- Support peak smart contract call gas per second (15 million)
- Support max smart contract call size (6K)
- Support max smart contract call state and output size (~4M gas or 900 KiB)
- Latency remains under 10s end to end at peak contract TPS

## Open Questions

1. What will externalization of the contract state in the transaction record look like? Still being designed.
2. How should we allow searching by topics or logs?
3. How will Hedera transactions triggered from a smart contract be externalized in the record stream? Still being
   designed. Tentatively, each contract triggered transaction will show up as a separate transaction and record with an
   incremented consensus timestamp and a parent timestamp populated.
4. Should we show individual function parameters in a normalized form? We decided against it at this time as it might be
   a performance concern or require parsing the solidity contract. Can revisit in the future by adding a new field with
   the normalized structure.
