# Smart Contracts

## Purpose

Smart contracts have existed on Hedera since Open Access, but the mirror node has never stored all the data associated
with smart contraction transactions in its database. With the
[announcement](https://hedera.com/blog/hedera-evm-smart-contracts-now-bring-highest-speed-programmability-to-tokenization)
to bring high speed smart contract execution to the Hedera network, its become more important to ensure the mirror node
is storing the appropriate smart contract information and making it retrievable via its APIs.

## Goals

- Enhance the database schema to store all contract-related information from transactions and transaction records
- Enhance the REST API to retrieve smart contracts and the execution results
- Explore alternative smart contract APIs including compatability with Ethereum JSON RPC APIs

## Non-Goals

- Ensure 100% compatibility with all Ethereum JSON-RPC APIs
- Execute smart contracts on the mirror node

## Architecture

### Database

#### Contract

Create a contract table that inherits from the entity table. A database migration should move entries in `entity` into
`contract` if they are of type contract or have had create or update contract transactions.

```sql
create table if not exists contract
(
  constructor_parameters bytea            not null,
  file_id                bigint           null, -- null needed for migrating old data where we didn't capture it
  initial_balance        bigint default 0 not null,
  new_realm_admin_key    bytea            null, -- Might always be null. Remove?
  valid_range            int8range        not null
) inherits (entity);
```

#### Contract History

Create a contract history table that is populated by a trigger that runs after the update of every contract. It should
insert the old row to the history table after setting the old row's consensus period to end (exclusively) at the new
row's start consensus timestamp.

```sql
create table if not exists contract_history
(
  like contract
);
```

#### Contract Result

Update the existing `contract_result` to capture all fields present in the protobuf.

- Add `bloom`
- Add `contract_id`
- Add `created_contract_ids`
- Add `error_message`
- Add `gas` to track the max gas allowed to use for contract construction or function calls.
- Change `gas_used` to not null
- Replace `consensus_timestamp` index with `consensus_timestamp` primary key
-

```sql
create table if not exists contract_result
(
  bloom                bytea                       null,
  call_result          bytea                       null,
  consensus_timestamp  nanos_timestamp primary key not null,
  contract_id          bigint                      not null,
  created_contract_ids bigint array                not null,
  error_message        text default ''             not null,
  function_parameters  bytea                       null,
  gas                  bigint                      not null,
  gas_used             bigint                      not null
);
```

#### Contract Log

Create a new table to store the results of the contract's log output

```sql
create table if not exists contract_log
(
  bloom               bytea                    null,
  consensus_timestamp nanos_timestamp          not null,
  contract_id         bigint                   not null,
  index               smallint                 not null,
  data                bytea                    null,
  topics              bytea array default '{}' not null,
  primary key (consensus_timestamp, index)
);
```

## Importer

- Add a `Contract` domain object with fields that match the schema
- Add a `ContractLog` domain object with fields that match the schema
- Update the `ContractResult` domain object with fields that match the schema. Nest `ContractLog` under `ContractResult`
- Add a `ContractRepository`
- Add `EntityListener.onContract(Contract)`
- Add logic to create a `Contract` domain object in create, update, and delete contract transaction handlers and notify
  via `EntityListener.onContract(contract)`
- Add logic to create a `ContractResult` and `ContractLog` domain objects in the contract create and contract call
  transaction handlers and notify via `EntityListener.onContractResult(contractResult)`.
- Remove logic specific to contracts in `EntityRecordItemListener`

### Upsert

Implement a generic custom `UpsertQueryGenerator` that generates the insert query entirely from annotations on
the `Contract` domain object.

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
      "auto_renew_account_id": "0.0.10001",
      "auto_renew_period": 7776000,
      "constructor_parameters": "eb896db302d70cc2504d40f5ead67013989602c1",
      "contract_id": "0.0.10001",
      "deleted": false,
      "expiration_timestamp": null,
      "file_id": 1000,
      "initial_balance": 100,
      "memo": "First contract",
      "proxy_account_id": "0.0.100",
      "solidity_address": "0x0000000000000000000000000000000000001001",
      "valid_from": "1633466568.31556926",
      "valid_to": null
    }
  ],
  "links": {
    "next": null
  }
}
```

Optional filters

- `contract.id`
- `order`
- `limit`

### Get Contract

`GET /api/v1/contracts/{id}`

```json
{
  "admin_key": {
    "_type": "ProtobufEncoded",
    "key": "7b2233222c2233222c2233227d"
  },
  "auto_renew_account_id": "0.0.10001",
  "auto_renew_period": 7776000,
  "code": "c896c66db6d98784cc03807640f3dfd41ac3a48c",
  "constructor_parameters": "eb896db302d70cc2504d40f5ead67013989602c1",
  "contract_id": "0.0.10001",
  "file_id": "0.0.1000",
  "gas": 250000,
  "initial_balance": 100,
  "memo": "First contract",
  "proxy_account_id": "0.0.100",
  "solidity_address": "0x0000000000000000000000000000000000001001",
  "valid_from": "1633466229.96874612",
  "valid_to": "1633466568.31556926"
}
```

Optional filters

- `timestamp` Return the historical state of the contract

### List Contract Results

`GET /api/v1/contracts/{id}/results`

```json
{
  "contract": "0.0.1001",
  "results": [
    {
      "bloom": "549358c4c2e573e02410ef7b5a5ffa5f36dd7398",
      "created_contract_ids": [
        "0.0.1003"
      ],
      "error_message": "",
      "gas_used": 1000,
      "log_info": [
        {
          "bloom": "1513001083c899b1996ec7fa33621e2c340203f0",
          "data": "8f705727c88764031b98fc32c314f8f9e463fb62",
          "topic": [
            "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef",
            "0x59d088293f09d5119d5b55858b989ffce4d398dc"
          ]
        }
      ],
      "timestamp": "12345.10001"
    }
  ],
  "links": {
    "next": null
  }
}
```

Optional filters

- `timestamp`
- `order`
- `limit`

## Non-Functional Requirements

- Support peak smart contract call TPS (100-1000)
- Latency remains under 10s end to end at peak contract TPS

## Open Questions

1. Is `new_realm_admin_key` ever non-null?
2. Is there a way to figure out which file belongs to which contract to back-fill data?
3. Should there be a `GET /api/v1/contracts/{id}/results/{id}`?
4. What are the average and max sizes for the different smart contract bytea fields?
5. How many logs can a single contract call have?
6. What will externalization of the contract state in the transaction record look like?
7. Should we implicitly populate the auto-renew account for contracts?
8. Should we use hex or base64 for bloom, data, code, etc?
